/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.baskt.music.ads.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import com.baskt.music.ads.domain.OpenSupportPageUseCase
import com.baskt.music.ads.domain.SupportPageOpenResult
import javax.inject.Inject

internal sealed interface SupportBasktScreenState {
    @Immutable
    data object Loading : SupportBasktScreenState

    @Immutable
    data object Success : SupportBasktScreenState

    @Immutable
    data object Empty : SupportBasktScreenState

    @Immutable
    data class Error(
        val reason: SupportBasktError,
    ) : SupportBasktScreenState
}

internal enum class SupportBasktError {
    PageUnavailable,
}

internal enum class SupportBasktUiEvent {
    OpenFailed,
}

@HiltViewModel
internal class SupportBasktViewModel
    @Inject
    constructor(
        private val openSupportPage: OpenSupportPageUseCase,
    ) : ViewModel() {
        private val _screenState =
            MutableStateFlow<SupportBasktScreenState>(SupportBasktScreenState.Success)
        val screenState: StateFlow<SupportBasktScreenState> = _screenState.asStateFlow()

        private val eventChannel = Channel<SupportBasktUiEvent>(Channel.BUFFERED)
        val events = eventChannel.receiveAsFlow()

        fun onSupportBasktClick() {
            if (_screenState.value is SupportBasktScreenState.Loading) return
            _screenState.value = SupportBasktScreenState.Loading
            when (openSupportPage()) {
                SupportPageOpenResult.Opened -> {
                    _screenState.value = SupportBasktScreenState.Success
                }

                SupportPageOpenResult.Unavailable -> {
                    _screenState.value =
                        SupportBasktScreenState.Error(SupportBasktError.PageUnavailable)
                    eventChannel.trySend(SupportBasktUiEvent.OpenFailed)
                }
            }
        }
    }
