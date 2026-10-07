/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.baskt.music.extensions

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC
import androidx.media3.common.MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE
import com.baskt.music.db.entities.Song
import com.baskt.music.innertube.models.EpisodeItem
import com.baskt.music.innertube.models.SongItem
import com.baskt.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import com.baskt.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_UGC
import com.baskt.music.models.MediaMetadata
import com.baskt.music.models.toMediaMetadata
import com.baskt.music.ui.utils.YTThumbQuality
import com.baskt.music.ui.utils.YtimgResizePolicy
import com.baskt.music.ui.utils.buildYTThumbnailUrl
import com.baskt.music.ui.utils.resize
import com.baskt.music.utils.NotificationArtworkSizePx
import com.baskt.music.utils.isLocalMediaId

const val ExtraIsMusicVideo = "com.baskt.music.extra.IS_MUSIC_VIDEO"
const val ExtraIsPodcast = "com.baskt.music.extra.IS_PODCAST"

val MediaItem.metadata: MediaMetadata?
    get() = localConfiguration?.tag as? MediaMetadata

private fun String?.toNotificationArtworkUri() =
    this
        ?.resize(
            width = NotificationArtworkSizePx,
            height = NotificationArtworkSizePx,
            ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
        )?.toUri()

private fun MediaItem.Builder.setCacheKeyIfRemote(mediaId: String): MediaItem.Builder {
    if (!mediaId.isLocalMediaId()) {
        setCustomCacheKey(mediaId)
    }
    return this
}

fun Song.toMediaItem() =
    MediaItem
        .Builder()
        .setMediaId(song.id)
        .setUri(song.id)
        .setCacheKeyIfRemote(song.id)
        .setTag(toMediaMetadata())
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata
                .Builder()
                .setTitle(song.title)
                .setSubtitle(artists.joinToString { it.name })
                .setArtist(artists.joinToString { it.name })
                .setArtworkUri(
                    if (song.isMusicVideo) {
                        buildYTThumbnailUrl(song.id, YTThumbQuality.HQ).toUri()
                    } else {
                        song.thumbnailUrl.toNotificationArtworkUri()
                    },
                )
                .setAlbumTitle(song.albumName)
                .setAlbumArtist(song.albumArtist)
                .setTrackNumber(song.trackNumber)
                .setDiscNumber(song.discNumber)
                .setReleaseYear(song.year)
                .setIsPlayable(true)
                .setMediaType(if (song.isPodcast) MEDIA_TYPE_PODCAST_EPISODE else MEDIA_TYPE_MUSIC)
                .setExtras(
                    Bundle().apply {
                        putBoolean(ExtraIsMusicVideo, song.isMusicVideo)
                        putBoolean(ExtraIsPodcast, song.isPodcast)
                    },
                )
                .build(),
        ).build()

fun SongItem.toMediaItem() =
    MediaItem
        .Builder()
        .setMediaId(id)
        .setUri(id)
        .setCacheKeyIfRemote(id)
        .setTag(toMediaMetadata())
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata
                .Builder()
                .setTitle(title)
                .setSubtitle(artists.joinToString { it.name })
                .setArtist(artists.joinToString { it.name })
                .setArtworkUri(
                    if (isMusicVideo()) {
                        buildYTThumbnailUrl(id, YTThumbQuality.HQ).toUri()
                    } else {
                        thumbnail.toNotificationArtworkUri()
                    },
                ).setAlbumTitle(album?.name)
                .setIsPlayable(true)
                .setMediaType(MEDIA_TYPE_MUSIC)
                .setExtras(
                    Bundle().apply {
                        putBoolean(ExtraIsMusicVideo, isMusicVideo())
                        putBoolean(ExtraIsPodcast, false)
                    },
                )
                .build(),
        ).build()

fun EpisodeItem.toMediaItem() = toMediaMetadata().toMediaItem()

fun MediaMetadata.toMediaItem() =
    MediaItem
        .Builder()
        .setMediaId(id)
        .setUri(id)
        .setCacheKeyIfRemote(id)
        .setTag(this)
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata
                .Builder()
                .setTitle(title)
                .setSubtitle(artists.joinToString { it.name })
                .setArtist(artists.joinToString { it.name })
                .setArtworkUri(
                    if (isMusicVideo) {
                        buildYTThumbnailUrl(id, YTThumbQuality.HQ).toUri()
                    } else {
                        thumbnailUrl.toNotificationArtworkUri()
                    },
                ).setAlbumTitle(album?.title)
                .setAlbumArtist(albumArtist)
                .setTrackNumber(trackNumber)
                .setDiscNumber(discNumber)
                .setReleaseYear(year)
                .setIsPlayable(true)
                .setMediaType(if (isPodcast) MEDIA_TYPE_PODCAST_EPISODE else MEDIA_TYPE_MUSIC)
                .setExtras(
                    Bundle().apply {
                        putBoolean(ExtraIsMusicVideo, isMusicVideo)
                        putBoolean(ExtraIsPodcast, isPodcast)
                    },
                )
                .build(),
        ).build()

private fun SongItem.isMusicVideo(): Boolean {
    val musicVideoType = endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
    return musicVideoType == MUSIC_VIDEO_TYPE_OMV || musicVideoType == MUSIC_VIDEO_TYPE_UGC
}
