/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.baskt.music.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.baskt.music.constants.AccountChannelHandleKey
import com.baskt.music.constants.AccountEmailKey
import com.baskt.music.constants.AccountNameKey
import com.baskt.music.constants.DataSyncIdKey
import com.baskt.music.constants.InnerTubeCookieKey
import com.baskt.music.constants.PoTokenGvsKey
import com.baskt.music.constants.PoTokenKey
import com.baskt.music.constants.PoTokenPlayerKey
import com.baskt.music.constants.SavedAccountsKey
import com.baskt.music.constants.SelectedYtmPlaylistsKey
import com.baskt.music.constants.VisitorDataKey
import com.baskt.music.constants.WebClientPoTokenEnabledKey
import com.baskt.music.constants.YtmSyncKey
import com.baskt.music.innertube.PlaybackAuthState
import com.baskt.music.innertube.YouTube
import com.baskt.music.innertube.models.AccountInfo
import com.baskt.music.innertube.utils.hasYouTubeLoginCookie
import com.baskt.music.innertube.utils.hasCompleteYouTubeLoginCookies
import com.baskt.music.utils.SavedAccount
import com.baskt.music.utils.dataStore
import com.baskt.music.utils.decodeSavedAccounts
import com.baskt.music.utils.encodeSavedAccounts
import com.baskt.music.utils.toPlaybackAuthState
import javax.inject.Inject
import javax.inject.Singleton

data class YouTubeLoginSession(
    val authState: PlaybackAuthState,
    val accountName: String,
    val accountEmail: String,
    val accountChannelHandle: String,
)

class MissingYouTubeDataSyncIdException : IllegalStateException("YouTube DataSyncId is missing")

@Singleton
class YouTubeLoginRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend fun completeLogin(
            cookie: String,
            visitorData: String?,
            dataSyncId: String?,
        ): Result<YouTubeLoginSession> =
            withContext(Dispatchers.IO) {
                runCatchingPreservingCancellation {
                    val normalizedCookie = cookie.trim()
                    check(hasYouTubeLoginCookie(normalizedCookie)) { "YouTube login cookie is missing" }
                    check(hasCompleteYouTubeLoginCookies(normalizedCookie)) { "YouTube login cookies are incomplete" }

                    val initialAuthState =
                        PlaybackAuthState(
                            cookie = normalizedCookie,
                            visitorData = visitorData,
                            dataSyncId = dataSyncId,
                        ).normalized()
                    val verificationAuthState = initialAuthState.copy(dataSyncId = null)
                    YouTube.authState = verificationAuthState

                    val accountInfo = YouTube.accountInfo().getOrThrow()

                    val resolvedDataSyncId = resolveRequiredDataSyncId(initialAuthState.dataSyncId)
                    val resolvedAuthState = initialAuthState.copy(dataSyncId = resolvedDataSyncId).normalized()
                    YouTube.authState = resolvedAuthState

                    persistLoginSession(
                        authState = resolvedAuthState,
                        accountInfo = accountInfo,
                    )

                    YouTubeLoginSession(
                        authState = resolvedAuthState,
                        accountName = accountInfo.name,
                        accountEmail = accountInfo.email.orEmpty(),
                        accountChannelHandle = accountInfo.channelHandle.orEmpty(),
                    )
                }
            }

        suspend fun switchSavedAccount(account: SavedAccount): Result<PlaybackAuthState> =
            withContext(Dispatchers.IO) {
                runCatchingPreservingCancellation {
                    check(hasYouTubeLoginCookie(account.innerTubeCookie)) { "Saved account login cookie is missing" }
                    check(hasCompleteYouTubeLoginCookies(account.innerTubeCookie)) {
                        "Saved account login cookies are incomplete"
                    }

                    val initialAuthState =
                        PlaybackAuthState(
                            cookie = account.innerTubeCookie,
                            visitorData = account.visitorData,
                            dataSyncId = account.dataSyncId,
                        ).normalized()
                    YouTube.authState = initialAuthState

                    val resolvedDataSyncId = resolveRequiredDataSyncId(initialAuthState.dataSyncId)
                    val resolvedAuthState = initialAuthState.copy(dataSyncId = resolvedDataSyncId).normalized()
                    YouTube.authState = resolvedAuthState

                    context.dataStore.edit { preferences ->
                        preferences[InnerTubeCookieKey] = account.innerTubeCookie
                        account.visitorData
                            .normalizeAuthValue()
                            ?.let { preferences[VisitorDataKey] = it }
                            ?: preferences.remove(VisitorDataKey)
                        preferences[DataSyncIdKey] = resolvedDataSyncId
                        preferences[AccountNameKey] = account.name
                        preferences[AccountEmailKey] = account.email
                        preferences[AccountChannelHandleKey] = account.channelHandle
                        preferences.remove(PoTokenKey)
                        preferences.remove(PoTokenGvsKey)
                        preferences.remove(PoTokenPlayerKey)
                        preferences[WebClientPoTokenEnabledKey] = false
                        preferences[YtmSyncKey] = account.ytmSync
                        preferences[SelectedYtmPlaylistsKey] = account.selectedYtmPlaylists

                        val savedAccounts = decodeSavedAccounts(preferences[SavedAccountsKey].orEmpty())
                        val repairedAccounts =
                            savedAccounts.map { savedAccount ->
                                if (savedAccount.id == account.id && savedAccount.dataSyncId != resolvedDataSyncId) {
                                    savedAccount.copy(dataSyncId = resolvedDataSyncId)
                                } else {
                                    savedAccount
                                }
                            }
                        if (repairedAccounts != savedAccounts) {
                            preferences[SavedAccountsKey] = encodeSavedAccounts(repairedAccounts)
                        }
                    }

                    context.dataStore.data
                        .first()
                        .toPlaybackAuthState()
                }
            }

        suspend fun saveLoginContext(
            visitorData: String? = null,
            dataSyncId: String? = null,
        ) {
            withContext(Dispatchers.IO) {
                val normalizedVisitorData = visitorData.normalizeAuthValue()
                val normalizedDataSyncId = dataSyncId.normalizeDataSyncId()
                if (normalizedVisitorData == null && normalizedDataSyncId == null) return@withContext

                context.dataStore.edit { preferences ->
                    normalizedVisitorData?.let { preferences[VisitorDataKey] = it }
                    normalizedDataSyncId?.let { preferences[DataSyncIdKey] = it }
                }

                normalizedVisitorData?.let { YouTube.visitorData = it }
                normalizedDataSyncId?.let { YouTube.dataSyncId = it }
            }
        }

        private suspend fun persistLoginSession(
            authState: PlaybackAuthState,
            accountInfo: AccountInfo,
        ) {
            val dataSyncId = authState.dataSyncId ?: throw MissingYouTubeDataSyncIdException()
            context.dataStore.edit { preferences ->
                preferences[InnerTubeCookieKey] = authState.cookie.orEmpty()
                authState.visitorData
                    ?.let { preferences[VisitorDataKey] = it }
                    ?: preferences.remove(VisitorDataKey)
                preferences[DataSyncIdKey] = dataSyncId
                preferences[AccountNameKey] = accountInfo.name
                preferences[AccountEmailKey] = accountInfo.email.orEmpty()
                preferences[AccountChannelHandleKey] = accountInfo.channelHandle.orEmpty()
                preferences.remove(PoTokenKey)
                preferences.remove(PoTokenGvsKey)
                preferences.remove(PoTokenPlayerKey)
                preferences[WebClientPoTokenEnabledKey] = false
            }
        }

        private suspend fun resolveRequiredDataSyncId(candidate: String?): String {
            val networkDataSyncId =
                YouTube
                    .accountDataSyncId()
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrNull()
                    .normalizeDataSyncId()

            return networkDataSyncId
                ?: candidate.normalizeDataSyncId()
                ?: throw MissingYouTubeDataSyncIdException()
        }
    }

class CompleteYouTubeLoginUseCase
    @Inject
    constructor(
        private val repository: YouTubeLoginRepository,
    ) {
        suspend operator fun invoke(
            cookie: String,
            visitorData: String?,
            dataSyncId: String?,
        ): Result<YouTubeLoginSession> =
            repository.completeLogin(
                cookie = cookie,
                visitorData = visitorData,
                dataSyncId = dataSyncId,
            )
    }

class SwitchSavedYouTubeAccountUseCase
    @Inject
    constructor(
        private val repository: YouTubeLoginRepository,
    ) {
        suspend operator fun invoke(account: SavedAccount): Result<PlaybackAuthState> = repository.switchSavedAccount(account)
    }

class UpdateYouTubeLoginContextUseCase
    @Inject
    constructor(
        private val repository: YouTubeLoginRepository,
    ) {
        suspend operator fun invoke(
            visitorData: String? = null,
            dataSyncId: String? = null,
        ) {
            repository.saveLoginContext(
                visitorData = visitorData,
                dataSyncId = dataSyncId,
            )
        }
    }

private suspend inline fun <T> runCatchingPreservingCancellation(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        Result.failure(throwable)
    }

private fun String?.normalizeAuthValue(): String? {
    val trimmed = this?.trim()
    return trimmed?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}

private fun String?.normalizeDataSyncId(): String? = PlaybackAuthState(dataSyncId = this).normalized().dataSyncId
