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
                    val hit = searchFirst(query, cleanTitle, artist) ?: continue
                    val stream = streamUrlFor(hit) ?: continue
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

        private data class Hit(
            val id: String,
            val title: String,
            val artists: String,
            val durationSeconds: Int?,
            val directEncryptedUrl: String?,
        )

        private suspend fun searchFirst(
            query: String,
            wantTitle: String,
            wantArtist: String?,
        ): Hit? {
            val text =
                get(
                    call = "search.getResults",
                    params = mapOf("q" to query, "p" to "1", "n" to "10"),
                ) ?: return null
            val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
            val results = root.optJSONArray("results") ?: return null
            var best: Hit? = null
            var bestScore = Int.MIN_VALUE
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                if (item.optString("type") !in setOf("song", "")) continue
                val hit = parseHit(item) ?: continue
                val score = score(hit, wantTitle, wantArtist)
                if (score > bestScore) {
                    bestScore = score
                    best = hit
                }
            }
            return best?.takeIf { bestScore > SCORE_FLOOR }
        }

        private fun parseHit(item: JSONObject): Hit? {
            val id = item.optString("id").ifBlank { return null }
            val info = item.optJSONObject("more_info") ?: JSONObject()
            val artists =
                info.optString("primary_artists").ifBlank { info.optString("singers") }
            return Hit(
                id = id,
                title = item.optString("title"),
                artists = artists,
                durationSeconds = info.optString("duration").toIntOrNull(),
                directEncryptedUrl = info.optString("encrypted_media_url").ifBlank { null },
            )
        }

        private suspend fun streamUrlFor(hit: Hit): String? {
            // Search hits sometimes already carry the encrypted URL; otherwise
            // fetch full details for the id.
            hit.directEncryptedUrl?.let { decryptTo320(it)?.let { return it } }
            val text =
                get(
                    call = "song.getDetails",
                    params = mapOf("pids" to hit.id),
                ) ?: return null
            val songs =
                runCatching { JSONObject(text).optJSONArray("songs") } .getOrNull()
                    ?: return null
            for (i in 0 until songs.length()) {
                val info = songs.optJSONObject(i)?.optJSONObject("more_info") ?: continue
                val encrypted = info.optString("encrypted_media_url")
                if (encrypted.isNotBlank()) {
                    decryptTo320(encrypted)?.let { return it }
                }
            }
            return null
        }

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

        private fun score(
            hit: Hit,
            wantTitle: String,
            wantArtist: String?,
        ): Int {
            val t = norm(hit.title)
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
                val a = norm(hit.artists)
                val wa = norm(wantArtist)
                if (wa.isNotEmpty() && (a.contains(wa) || wa.split(" ").any { it.length > 3 && a.contains(it) })) {
                    s += 40
                }
            }
            return s
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
