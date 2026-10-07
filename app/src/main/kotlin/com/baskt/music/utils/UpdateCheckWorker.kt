/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.baskt.music.utils

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import com.baskt.music.BuildConfig
import com.baskt.music.constants.AutomaticUpdateCheckKey
import com.baskt.music.constants.UpdateChannel
import com.baskt.music.constants.UpdateChannelKey
import com.baskt.music.defaultUpdateChannel

class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!BuildConfig.UPDATER_AVAILABLE) {
            return Result.success()
        }

        return try {
            val dataStore = applicationContext.dataStore

            val preferences = dataStore.data.first()
            val automaticChecksEnabled = preferences[AutomaticUpdateCheckKey] ?: true
            if (!automaticChecksEnabled) return Result.success()

            val updateChannel =
                UpdateChannel.fromStoredName(preferences[UpdateChannelKey], defaultUpdateChannel)

            val latestVersion =
                when (updateChannel) {
                    UpdateChannel.ARTIFACT -> Updater.getLatestCanaryVersionName()
                    UpdateChannel.STABLE -> Updater.getLatestVersionName()
                }.getOrElse { throw it }

            if (Updater.isUpdateAvailable(latestVersion, BuildConfig.VERSION_NAME)) {
                UpdateNotificationManager.notifyIfNewVersion(
                    applicationContext,
                    latestVersion,
                    updateChannel,
                )
            }

            Result.success()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            reportException(exception)
            Result.retry()
        }
    }
}
