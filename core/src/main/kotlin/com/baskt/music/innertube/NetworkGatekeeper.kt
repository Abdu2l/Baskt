/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.baskt.music.innertube

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

object NetworkGatekeeper : Interceptor {
    private val connectionBlocked = AtomicBoolean(true)

    val isConnectionBlocked: Boolean
        get() = connectionBlocked.get()

    fun setConnectionBlocked(blocked: Boolean) {
        connectionBlocked.set(blocked)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        if (connectionBlocked.get()) {
            throw IOException(
                "Connection blocked by Baskt Remote. This app could not be verified as an " +
                    "official Baskt build. Install an official build from " +
                    "https://github.com/Abdu2l/Baskt or https://github.com/Abdu2l/Baskt.",
            )
        }
        return chain.proceed(chain.request())
    }
}
