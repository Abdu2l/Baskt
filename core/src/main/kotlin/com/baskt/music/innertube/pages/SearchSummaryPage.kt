/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.baskt.music.innertube.pages

import com.baskt.music.innertube.models.Album
import com.baskt.music.innertube.models.AlbumItem
import com.baskt.music.innertube.models.Artist
import com.baskt.music.innertube.models.ArtistItem
import com.baskt.music.innertube.models.MusicCardShelfRenderer
import com.baskt.music.innertube.models.MusicResponsiveListItemRenderer
import com.baskt.music.innertube.models.PlaylistItem
import com.baskt.music.innertube.models.SongItem
import com.baskt.music.innertube.models.YTItem
import com.baskt.music.innertube.models.filterExplicit
import com.baskt.music.innertube.models.filterVideo
import com.baskt.music.innertube.models.oddElements
import com.baskt.music.innertube.models.splitBySeparator
import com.baskt.music.innertube.models.toArtists
import com.baskt.music.innertube.utils.parseTime

data class SearchSummary(
    val title: String,
    val items: List<YTItem>,
)

data class SearchSummaryPage(
    val summaries: List<SearchSummary>,
) {
    fun filterExplicit(enabled: Boolean) =
        if (enabled) {
            SearchSummaryPage(
                summaries.mapNotNull { s ->
                    SearchSummary(
                        title = s.title,
                        items =
                            s.items.filterExplicit().ifEmpty {
                                return@mapNotNull null
                            },
                    )
                },
            )
        } else {
            this
        }

    fun filterVideo(enabled: Boolean) =
        if (enabled) {
            SearchSummaryPage(
                summaries.mapNotNull { s ->
                    SearchSummary(
                        title = s.title,
                        items = s.items.filterVideo().ifEmpty { return@mapNotNull null },
                    )
                },
            )
        } else {
            this
        }

    companion object {
        fun itemsFromMusicCardShelfRenderer(renderer: MusicCardShelfRenderer): List<YTItem> {
            val artist =
                renderer.onTap.browseEndpoint
                    ?.takeIf { it.isArtistEndpoint }
                    ?.let { endpoint ->
                        renderer.title.runs
                            ?.joinToString(separator = "") { it.text }
                            ?.takeIf(String::isNotBlank)
                            ?.let { Artist(name = it, id = endpoint.browseId) }
                    }
            return buildList {
                fromMusicCardShelfRenderer(renderer)?.let { add(it) }
                renderer.contents.orEmpty().forEach { content ->
                    val item =
                        content.musicResponsiveListItemRenderer
                            ?.let { fromMusicResponsiveListItemRenderer(it) }
                            ?: return@forEach
                    add(
                        if (item is SongItem && item.artists.isEmpty() && artist != null) {
                            item.copy(artists = listOf(artist))
                        } else {
                            item
                        },
                    )
                }
            }
        }

        fun fromMusicCardShelfRenderer(renderer: MusicCardShelfRenderer): YTItem? {
            val subtitle = renderer.subtitle.runs?.splitBySeparator().orEmpty()
            val thumbnail = renderer.thumbnail.musicThumbnailRenderer?.getBestThumbnail()
            return when {
                renderer.onTap.watchEndpoint != null -> {
                    val itemThumbnail = thumbnail ?: return null
                    SongItem(
                        id = renderer.onTap.watchEndpoint.videoId ?: return null,
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text ?: return null,
                        artists =
                            subtitle
                                .asSequence()
                                .map { it.toArtists() }
                                .firstOrNull { it.isNotEmpty() }
                                .orEmpty(),
                        album =
                            subtitle.getOrNull(2)?.firstOrNull()?.takeIf { it.navigationEndpoint?.browseEndpoint != null }?.let {
                                Album(
                                    name = it.text,
                                    id = it.navigationEndpoint?.browseEndpoint?.browseId!!,
                                )
                            },
                        duration =
                            subtitle
                                .lastOrNull()
                                ?.firstOrNull()
                                ?.text
                                ?.parseTime(),
                        thumbnail = itemThumbnail.normalizedUrl,
                        thumbnailWidth = itemThumbnail.width,
                        thumbnailHeight = itemThumbnail.height,
                        explicit =
                            renderer.subtitleBadges?.find {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } != null,
                        endpoint = renderer.onTap.watchEndpoint,
                    )
                }

                renderer.onTap.browseEndpoint?.isArtistEndpoint == true -> {
                    val itemThumbnail = thumbnail ?: return null
                    ArtistItem(
                        id = renderer.onTap.browseEndpoint.browseId,
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text ?: return null,
                        thumbnail = itemThumbnail.normalizedUrl,
                        thumbnailWidth = itemThumbnail.width,
                        thumbnailHeight = itemThumbnail.height,
                        shuffleEndpoint =
                            renderer.buttons
                                .find { it.buttonRenderer.icon?.iconType == "MUSIC_SHUFFLE" }
                                ?.buttonRenderer
                                ?.command
                                ?.watchPlaylistEndpoint ?: return null,
                        radioEndpoint =
                            renderer.buttons
                                .find { it.buttonRenderer.icon?.iconType == "MIX" }
                                ?.buttonRenderer
                                ?.command
                                ?.watchPlaylistEndpoint ?: return null,
                    )
                }

                renderer.onTap.browseEndpoint?.isAlbumEndpoint == true -> {
                    val itemThumbnail = thumbnail ?: return null
                    AlbumItem(
                        browseId = renderer.onTap.browseEndpoint.browseId,
                        playlistId =
                            renderer.buttons
                                .firstOrNull()
                                ?.buttonRenderer
                                ?.command
                                ?.anyWatchEndpoint
                                ?.playlistId ?: return null,
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text ?: return null,
                        artists =
                            subtitle?.getOrNull(1)?.oddElements()?.map {
                                Artist(
                                    name = it.text,
                                    id = it.navigationEndpoint?.browseEndpoint?.browseId,
                                )
                            } ?: return null,
                        year = null,
                        thumbnail = itemThumbnail.normalizedUrl,
                        thumbnailWidth = itemThumbnail.width,
                        thumbnailHeight = itemThumbnail.height,
                        explicit =
                            renderer.subtitleBadges?.find {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } != null,
                    )
                }

                renderer.onTap.browseEndpoint?.isPlaylistEndpoint == true -> {
                    val itemThumbnail = thumbnail ?: return null
                    PlaylistItem(
                        id =
                            renderer.onTap.browseEndpoint.browseId
                                .removePrefix("VL"),
                        title =
                            renderer.header
                                ?.musicCardShelfHeaderBasicRenderer
                                ?.title
                                ?.runs
                                ?.joinToString(separator = "") { it.text }
                                ?: return null,
                        author =
                            Artist(
                                id = null,
                                name = renderer.subtitle.runs?.joinToString { it.text } ?: return null,
                            ),
                        songCountText = null,
                        thumbnail = itemThumbnail.normalizedUrl,
                        thumbnailWidth = itemThumbnail.width,
                        thumbnailHeight = itemThumbnail.height,
                        playEndpoint =
                            renderer.buttons
                                .find { it.buttonRenderer.icon?.iconType == "PLAY_ARROW" }
                                ?.buttonRenderer
                                ?.command
                                ?.watchPlaylistEndpoint
                                ?: return null,
                        shuffleEndpoint =
                            renderer.buttons
                                .find { it.buttonRenderer.icon?.iconType == "MUSIC_SHUFFLE" }
                                ?.buttonRenderer
                                ?.command
                                ?.watchPlaylistEndpoint
                                ?: return null,
                        radioEndpoint = null,
                    )
                }

                else -> {
                    null
                }
            }
        }

        fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): YTItem? = SearchPage.toYTItem(renderer)
    }
}
