/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.baskt.music.domain.player

import androidx.media3.common.Player
import kotlinx.coroutines.flow.Flow
import com.baskt.music.canvas.CanvasPlaybackRequest
import com.baskt.music.canvas.CanvasPlaybackUseCase
import com.baskt.music.canvas.CanvasPolicy
import com.baskt.music.canvas.CanvasVideo
import com.baskt.music.db.entities.containerLabel
import com.baskt.music.db.entities.formattedBitrate
import com.baskt.music.db.entities.formattedSampleRate
import com.baskt.music.playback.ImmersivePlayerData
import com.baskt.music.playback.ImmersivePlayerRepository
import com.baskt.music.ui.player.immersive.ImmersivePlayerArtist
import com.baskt.music.ui.player.immersive.ImmersivePlayerUiModel
import java.util.Locale
import javax.inject.Inject

class ObserveImmersivePlayerUseCase @Inject constructor(
    private val repository: ImmersivePlayerRepository,
    private val canvasPlaybackUseCase: CanvasPlaybackUseCase,
) {
    val canvasPolicy = canvasPlaybackUseCase.policy
    val canvasRevision = canvasPlaybackUseCase.revision
    val spotifyConnected = canvasPlaybackUseCase.spotifyConnected

    operator fun invoke(): Flow<ImmersivePlayerData?> = repository.observePlayer()

    fun map(data: ImmersivePlayerData, canvas: CanvasVideo? = null): ImmersivePlayerUiModel {
        val metadata = data.metadata
        val formatDetails =
            data.format?.let { format ->
                listOfNotNull(
                    format.containerLabel().takeIf(String::isNotBlank),
                    format.formattedSampleRate(),
                    format.formattedBitrate(),
                ).joinToString(separator = " · ")
            }.orEmpty()
        return ImmersivePlayerUiModel(
            mediaId = metadata.id,
            title = metadata.title,
            artists = metadata.artists.map { ImmersivePlayerArtist(it.id, it.name) },
            albumId = metadata.album?.id,
            sourceTitle = metadata.album?.title?.takeIf(String::isNotBlank) ?: data.queueTitle.orEmpty(),
            artworkUrl = metadata.thumbnailUrl?.takeIf(String::isNotBlank)
                ?: data.song?.takeIf { it.id == metadata.id }?.thumbnailUrl?.takeIf(String::isNotBlank),
            isPlaying = data.isPlaying,
            isBuffering = data.playbackState == Player.STATE_BUFFERING,
            canSkipPrevious = data.canSkipPrevious,
            canSkipNext = data.canSkipNext,
            isLiked = data.song?.song?.liked == true,
            formatDetails = formatDetails,
            outputDevice = data.outputDevice,
            canvas = canvas,
            durationMs = metadata.duration.coerceAtLeast(0) * 1_000L,
        )
    }

    fun requestFor(data: ImmersivePlayerData): CanvasPlaybackRequest {
        val country = Locale.getDefault().country
        return CanvasPlaybackRequest(
            mediaId = data.metadata.id,
            title = data.metadata.title,
            artist = data.metadata.artists.firstOrNull()?.name.orEmpty(),
            storefront = if (country.length == 2) country.lowercase(Locale.ROOT) else "us",
            requireVertical = true,
        )
    }

    suspend fun loadCanvas(request: CanvasPlaybackRequest, policy: CanvasPolicy): CanvasVideo? =
        canvasPlaybackUseCase.load(request, policy)
}
