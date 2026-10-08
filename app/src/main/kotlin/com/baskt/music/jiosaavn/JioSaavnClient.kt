/*
 * Baskt (2026)
 * JioSaavn fallback source: resolves a direct MP3 stream when YouTube
 * extraction yields nothing. Unofficial endpoints, same approach as
 * sumitkolhe/jiosaavn-api (DES-ECB key 38346591).
 */

package com.baskt.music.jiosaavn

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import org.json.JSONObject
import timber.log.Timber
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

data class JioSaavnStream(
    val url: String,
    val title: String,
    val durationSeconds: Int?,
)

data class SaavnTrack(
    val id: String,
    val title: String,
    val artists: String,
    val image: String,
    val durationSeconds: Int?,
    val album: String?,
)

@Singleton
class JioSaavnClient
    @Inject
    constructor() {
        private val client =
            HttpClient(OkHttp) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 15_000L
                    connectTimeoutMillis = 8_000L
                    socketTimeoutMillis = 15_000L
                }
            }

        /**
         * Best-effort direct MP3 URL for [title] (+ optional [artist]).
         * Returns null when nothing usable is found; never throws.
         */
        suspend fun resolveStreamUrl(
            title: String,
            artist: String?,
        ): JioSaavnStream? =
            runCatching {
                val cleanTitle = title.substringBefore(" (").substringBefore(" [").trim()
                val queries =
                    buildList {
                        if (!artist.isNullOrBlank()) add("$cleanTitle $artist")
                        add(cleanTitle)
                    }
                for (query in queries) {
                    val hits = searchTracks(query, limit = 5)
                    val hit = hits.maxByOrNull { scoreOf(it, cleanTitle, artist) }
                        ?.takeIf { scoreOf(it, cleanTitle, artist) > SCORE_FLOOR }
                        ?: continue
                    val stream = streamUrlById(hit.id) ?: continue
                    return JioSaavnStream(
                        url = stream,
                        title = hit.title,
                        durationSeconds = hit.durationSeconds,
                    )
                }
                null
            }.getOrElse { failure ->
                Timber.tag(TAG).w(failure, "JioSaavn resolve failed for $title")
                null
            }

        /** Raw track search for UI lists. Never throws. */
        suspend fun searchTracks(
            query: String,
            limit: Int = 8,
        ): List<SaavnTrack> =
            runCatching {
                val text =
                    get(
                        call = "search.getResults",
                        params = mapOf("q" to query, "p" to "1", "n" to limit.coerceIn(1, 20).toString()),
                    ) ?: return emptyList()
                val root = JSONObject(text)
                val results = root.optJSONArray("results") ?: return emptyList()
                buildList {
                    for (i in 0 until results.length()) {
                        val item = results.optJSONObject(i) ?: continue
                        if (item.optString("type") !in setOf("song", "")) continue
                        val info = item.optJSONObject("more_info") ?: JSONObject()
                        val artists =
                            info.optString("primary_artists").ifBlank { info.optString("singers") }
                        add(
                            SaavnTrack(
                                id = item.optString("id"),
                                title = item.optString("title"),
                                artists = artists,
                                image = upscaleImage(item.optString("image")),
                                durationSeconds = info.optString("duration").toIntOrNull(),
                                album = info.optString("album").ifBlank { null },
                            ),
                        )
                    }
                }.filter { it.id.isNotBlank() && it.title.isNotBlank() }
            }.getOrElse { failure ->
                Timber.tag(TAG).w(failure, "JioSaavn search failed for $query")
                emptyList()
            }

        /** Direct stream URL for a known JioSaavn song id. Never throws. */
        suspend fun streamUrlById(id: String): String? =
            runCatching {
                val text =
                    get(
                        call = "song.getDetails",
                        params = mapOf("pids" to id),
                    ) ?: return null
                val songs = JSONObject(text).optJSONArray("songs") ?: return null
                for (i in 0 until songs.length()) {
                    val info = songs.optJSONObject(i)?.optJSONObject("more_info") ?: continue
                    val encrypted = info.optString("encrypted_media_url")
                    if (encrypted.isNotBlank()) {
                        decryptTo320(encrypted)?.let { return it }
                    }
                }
                null
            }.getOrElse { failure ->
                Timber.tag(TAG).w(failure, "JioSaavn details failed for $id")
                null
            }

        private fun scoreOf(
            track: SaavnTrack,
            wantTitle: String,
            wantArtist: String?,
        ): Int {
            val t = norm(track.title)
            val w = norm(wantTitle)
            if (t.isEmpty() || w.isEmpty()) return Int.MIN_VALUE
            var s = 0
            if (t == w) {
                s += 100
            } else if (t.contains(w) || w.contains(t)) {
                s += 60
            } else {
                s -= levenshtein(t, w).coerceAtMost(40)
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

        private fun upscaleImage(url: String): String =
            url
                .replace(Regex("150x150|50x50"), "500x500")
                .replace(Regex("^http://"), "https://")

        private suspend fun get(
            call: String,
            params: Map<String, String>,
        ): String? =
            runCatching {
                client.get(API_URL) {
                    parameter("__call", call)
                    parameter("_format", "json")
                    parameter("_marker", "0")
                    parameter("api_version", "4")
                    parameter("ctx", "web6dot0")
                    params.forEach { (k, v) -> parameter(k, v) }
                    header("User-Agent", USER_AGENT)
                    header("Accept", "application/json")
                }.bodyAsText().takeIf { it.isNotBlank() }
            }.getOrElse { failure ->
                Timber.tag(TAG).w(failure, "JioSaavn api $call failed")
                null
            }

        private fun norm(s: String): String = s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

        private fun levenshtein(a: String, b: String): Int {
            if (a == b) return 0
            if (a.isEmpty()) return b.length
            if (b.isEmpty()) return a.length
            var prev = IntArray(b.length + 1) { it }
            var curr = IntArray(b.length + 1)
            for (i in 1..a.length) {
                curr[0] = i
                for (j in 1..b.length) {
                    curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                }
                val tmp = prev
                prev = curr
                curr = tmp
            }
            return prev[b.length]
        }

        companion object {
            private const val TAG = "JioSaavn"
            /** MediaId prefix marking JioSaavn-direct tracks. */
            const val ID_PREFIX = "jiosaavn:"
            private const val API_URL = "https://www.jiosaavn.com/api.php"
            private const val USER_AGENT =
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            private const val SCORE_FLOOR = -10

            /**
             * DES-ECB decrypt (PKCS5) with the public JioSaavn key, then swap
             * the _96 marker for the _320 (320kbps) file.
             */
            fun decryptTo320(encryptedMediaUrl: String): String? =
                runCatching {
                    val raw = android.util.Base64.decode(encryptedMediaUrl, android.util.Base64.DEFAULT)
                    val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
                    cipher.init(
                        Cipher.DECRYPT_MODE,
                        SecretKeySpec("38346591".toByteArray(Charsets.US_ASCII), "DES"),
                    )
                    String(cipher.doFinal(raw), Charsets.UTF_8)
                        .trim()
                        .replace("_96", "_320")
                        .takeIf { it.startsWith("http") }
                }.getOrNull()
        }
    }
