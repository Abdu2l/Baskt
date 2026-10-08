/*
 * Baskt (2026)
 * TIDAL source, same approach as monochrome.tf: public browser client
 * credentials mint an app token, catalog + streams come from api.tidal.com.
 * If TIDAL dies, the resolver falls through to JioSaavn automatically.
 */

package com.baskt.music.tidal

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class TidalTrack(
    val id: String,
    val title: String,
    val artists: String,
    val image: String,
    val durationSeconds: Int?,
)

@Singleton
class TidalClient
    @Inject
    constructor() {
        private val client =
            HttpClient(OkHttp) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 20_000L
                    connectTimeoutMillis = 10_000L
                    socketTimeoutMillis = 20_000L
                }
            }

        @Volatile
        private var token: String? = null

        @Volatile
        private var tokenExpiryMs: Long = 0L

        private val tokenLock = Any()

        /** Direct audio URL for a TIDAL track id. Never throws (null on miss). */
        suspend fun streamUrlById(id: String): String? =
            runCatching {
                val auth = appToken() ?: return null
                val text =
                    client.get("$API_BASE/tracks/$id/playbackinfo") {
                        parameter("audioquality", "LOSSLESS")
                        parameter("playbackmode", "STREAM")
                        parameter("assetpresentation", "FULL")
                        parameter("countryCode", COUNTRY)
                        bearerAuth(auth)
                        header("Accept", "application/json")
                    }.bodyAsText()
                extractStreamUrl(JSONObject(text))
            }.getOrElse { failure ->
                Timber.tag(TAG).w(failure, "TIDAL playbackinfo failed for $id")
                null
            }

        /** Track search. Never throws. */
        suspend fun searchTracks(
            query: String,
            limit: Int = 8,
        ): List<TidalTrack> =
            runCatching {
                val auth = appToken() ?: return emptyList()
                val text =
                    client.get("$API_BASE/search") {
                        parameter("query", query)
                        parameter("limit", limit.coerceIn(1, 20))
                        parameter("countryCode", COUNTRY)
                        bearerAuth(auth)
                        header("Accept", "application/json")
                    }.bodyAsText()
                val data = JSONObject(text).optJSONObject("data") ?: return emptyList()
                val tracks = data.optJSONObject("tracks")?.optJSONArray("items")
                    ?: return emptyList()
                buildList {
                    for (i in 0 until tracks.length()) {
                        parseTrack(tracks.optJSONObject(i) ?: continue)?.let { add(it) }
                    }
                }
            }.getOrElse { failure ->
                Timber.tag(TAG).w(failure, "TIDAL search failed for $query")
                emptyList()
            }

        /** Resolve title (+artist) to a stream, best match first. Never throws. */
        suspend fun resolveStreamUrl(
            title: String,
            artist: String?,
        ): String? =
            runCatching {
                val cleanTitle = title.substringBefore(" (").substringBefore(" [").trim()
                val queries =
                    buildList {
                        if (!artist.isNullOrBlank()) add("$cleanTitle $artist")
                        add(cleanTitle)
                    }
                for (query in queries) {
                    val hits = searchTracks(query, limit = 5)
                    val best =
                        hits.maxByOrNull { scoreTrack(it, cleanTitle, artist) }
                            ?.takeIf { scoreTrack(it, cleanTitle, artist) > SCORE_FLOOR }
                            ?: continue
                    streamUrlById(best.id)?.let { return it }
                }
                null
            }.getOrElse { failure ->
                Timber.tag(TAG).w(failure, "TIDAL resolve failed for $title")
                null
            }

        private fun parseTrack(item: JSONObject): TidalTrack? {
            val id = item.optLong("id", -1L).takeIf { it > 0 } ?: return null
            val artistsJson = item.optJSONArray("artists")
            val artists =
                buildList {
                    if (artistsJson != null) {
                        for (i in 0 until artistsJson.length()) {
                            artistsJson.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                    }
                }.ifEmpty { listOf(item.optJSONObject("artist")?.optString("name").orEmpty()) }
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
            val album = item.optJSONObject("album")
            val cover = album?.optString("cover").orEmpty()
            return TidalTrack(
                id = id.toString(),
                title = item.optString("title"),
                artists = artists,
                image = coverToUrl(cover),
                durationSeconds = item.optInt("duration", -1).takeIf { it > 0 },
            )
        }

        private fun coverToUrl(coverUuid: String): String {
            if (coverUuid.isBlank()) return ""
            // resources.tidal.com/images/{uuid-with-slashes}/{size}.jpg
            val path = coverUuid.replace("-", "/")
            return "https://resources.tidal.com/images/$path/640x640.jpg"
        }

        private fun extractStreamUrl(info: JSONObject): String? {
            val manifestB64 = info.optString("manifest").ifBlank { return null }
            val manifestMime = info.optString("manifestMimeType")
            val raw =
                runCatching {
                    android.util.Base64.decode(manifestB64, android.util.Base64.DEFAULT).toString(Charsets.UTF_8)
                }.getOrNull() ?: return null
            // Classic BTS manifest: base64 JSON with a urls[] array.
            runCatching {
                val urls = JSONObject(raw).optJSONArray("urls")
                if (urls != null && urls.length() > 0) {
                    return urls.optString(0).takeIf { it.startsWith("http") }
                }
            }
            // DASH manifest: hand the MPD back only if ExoPlayer can take it
            // directly; otherwise fail soft and let the next source try.
            if (manifestMime.contains("dash", ignoreCase = true) && raw.trimStart().startsWith("<MPD")) {
                Timber.tag(TAG).i("DASH manifest received; no direct URL path")
            }
            return null
        }

        private suspend fun appToken(): String? {
            val cached = token
            if (cached != null && System.currentTimeMillis() < tokenExpiryMs) return cached
            return synchronized(tokenLock) {
                val cached2 = token
                if (cached2 != null && System.currentTimeMillis() < tokenExpiryMs) {
                    cached2
                } else {
                    null
                }
            } ?: fetchToken()
        }

        private suspend fun fetchToken(): String? =
            runCatching {
                val text =
                    client.post(TOKEN_URL) {
                        val creds = "$CLIENT_ID:$CLIENT_SECRET"
                        val basic =
                            android.util.Base64.encodeToString(
                                creds.toByteArray(Charsets.UTF_8),
                                android.util.Base64.NO_WRAP,
                            )
                        header("Authorization", "Basic $basic")
                        header("Content-Type", "application/x-www-form-urlencoded")
                        setBody(
                            FormDataContent(
                                Parameters.build {
                                    append("grant_type", "client_credentials")
                                },
                            ),
                        )
                    }.bodyAsText()
                val json = JSONObject(text)
                val access = json.optString("access_token").ifBlank { return null }
                val expiresIn = json.optLong("expires_in", 3600L)
                synchronized(tokenLock) {
                    token = access
                    tokenExpiryMs = System.currentTimeMillis() + (expiresIn - 60) * 1_000L
                }
                access
            }.getOrElse { failure ->
                Timber.tag(TAG).w(failure, "TIDAL token fetch failed")
                null
            }

        private fun scoreTrack(
            track: TidalTrack,
            wantTitle: String,
            wantArtist: String?,
        ): Int {
            fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
            val t = norm(track.title)
            val w = norm(wantTitle)
            if (t.isEmpty() || w.isEmpty()) return Int.MIN_VALUE
            var s =
                when {
                    t == w -> 100
                    t.contains(w) || w.contains(t) -> 60
                    else -> 0
                }
            if (!wantArtist.isNullOrBlank()) {
                val a = norm(track.artists)
                val wa = norm(wantArtist)
                if (wa.isNotEmpty() && (a.contains(wa) || wa.split(" ").any { it.length > 3 && a.contains(it) })) {
                    s += 40
                }
            }
            return s
        }

        companion object {
            private const val TAG = "Tidal"
            private const val API_BASE = "https://api.tidal.com/v1"
            private const val TOKEN_URL = "https://auth.tidal.com/v1/oauth2/token"
            private const val COUNTRY = "US"

            // Public browser client credentials, same as monochrome.tf uses.
            // If TIDAL rotates these, streams fall through to JioSaavn.
            private const val CLIENT_ID = "txNoH4kkV41MfH25"
            private const val CLIENT_SECRET = "dQjy0MinCEvxi1O4UmxvxWnDjt4cgHBPw8ll6nYBk98="

            /** MediaId prefix marking TIDAL-direct tracks. */
            const val ID_PREFIX = "tidal:"
            private const val SCORE_FLOOR = 20
        }
    }
