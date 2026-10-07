/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:Suppress("UNUSED_EXPRESSION")

package com.baskt.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.baskt.music.LocalPlayerAwareWindowInsets
import com.baskt.music.LocalPlayerConnection
import com.baskt.music.R
import com.baskt.music.constants.ListItemHeight
import com.baskt.music.extensions.togglePlayPause
import com.baskt.music.innertube.models.AlbumItem
import com.baskt.music.innertube.models.ArtistItem
import com.baskt.music.innertube.models.EpisodeItem
import com.baskt.music.innertube.models.PlaylistItem
import com.baskt.music.innertube.models.PodcastItem
import com.baskt.music.innertube.models.SongItem
import com.baskt.music.models.toMediaMetadata
import com.baskt.music.extensions.toMediaItem
import com.baskt.music.playback.queues.ListQueue
import com.baskt.music.playback.queues.YouTubeQueue
import com.baskt.music.ui.component.IconButton
import com.baskt.music.ui.component.LocalMenuState
import com.baskt.music.ui.component.NavigationTitle
import com.baskt.music.ui.component.YouTubeGridItem
import com.baskt.music.ui.component.YouTubeListItem
import com.baskt.music.ui.component.shimmer.GridItemPlaceHolder
import com.baskt.music.ui.component.shimmer.ShimmerHost
import com.baskt.music.ui.component.shimmer.TextPlaceholder
import com.baskt.music.ui.menu.YouTubeAlbumMenu
import com.baskt.music.ui.menu.YouTubeArtistMenu
import com.baskt.music.ui.menu.YouTubePlaylistMenu
import com.baskt.music.ui.menu.YouTubeSongMenu
import com.baskt.music.ui.utils.SnapLayoutInfoProvider
import com.baskt.music.ui.utils.backToMain
import com.baskt.music.viewmodels.YouTubeBrowseViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun YouTubeBrowseScreen(
    navController: NavController,
    viewModel: YouTubeBrowseViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val browseResult by viewModel.result.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
        val lazyGridState = rememberLazyGridState()
        val snapLayoutInfoProvider =
            remember(lazyGridState) {
                SnapLayoutInfoProvider(
                    lazyGridState = lazyGridState,
                    positionInLayout = { layoutSize, itemSize ->
                        (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                    },
                )
            }
        LazyColumn(
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            if (browseResult == null) {
                item {
                    ShimmerHost(
                        modifier = Modifier.animateItem(),
                    ) {
                        TextPlaceholder(
                            height = 36.dp,
                            modifier =
                                Modifier
                                    .padding(12.dp)
                                    .width(250.dp),
                        )
                        LazyRow {
                            items(4) {
                                GridItemPlaceHolder()
                            }
                        }
                    }
                }
            }

            browseResult?.items?.fastForEach {
                if (it.items.isNotEmpty()) {
                    it.title?.let { title ->
                        item {
                            NavigationTitle(title)
                        }
                    }
                    if (it.items.all { item -> item is SongItem }) {
                        item {
                            LazyHorizontalGrid(
                                state = lazyGridState,
                                rows = GridCells.Fixed(4),
                                flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
                                contentPadding =
                                    WindowInsets.systemBars
                                        .only(WindowInsetsSides.Horizontal)
                                        .asPaddingValues(),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(ListItemHeight * 4)
                                        .animateItem(),
                            ) {
                                items(
                                    items = it.items,
                                ) { song ->
                                    Box(Modifier.width(350.dp)) {
                                        YouTubeListItem(
                                            item = song as SongItem,
                                            isActive = mediaMetadata?.id == song.id,
                                            isPlaying = isPlaying,
                                            isSwipeable = false,
                                            trailingContent = {
                                                IconButton(
                                                    onClick = {
                                                        menuState.show {
                                                            YouTubeSongMenu(
                                                                song = song,
                                                                navController = navController,
                                                                onDismiss = menuState::dismiss,
                                                            )
                                                        }
                                                    },
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.more_vert),
                                                        contentDescription = null,
                                                    )
                                                }
                                            },
                                            modifier =
                                                Modifier
                                                    .clickable {
                                                        if (song.id == mediaMetadata?.id) {
                                                            playerConnection.player.togglePlayPause()
                                                        } else {
                                                            playerConnection.playQueue(
                                                                YouTubeQueue.radio(
                                                                    song.toMediaMetadata(),
                                                                ),
                                                            )
                                                        }
                                                    }.animateItem(),
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            LazyRow {
                                items(
                                    items = it.items,
                                ) { item ->
                                    YouTubeGridItem(
                                        item = item,
                                        isActive =
                                            when (item) {
                                                is AlbumItem -> mediaMetadata?.album?.id == item.id
                                                else -> false
                                            },
                                        isPlaying = isPlaying,
                                        coroutineScope = coroutineScope,
                                        modifier =
                                            Modifier
                                                .combinedClickable(
                                                    onClick = {
                                                        when (item) {
                                                            is AlbumItem -> navController.navigate("album/${item.id}")
                                                            is ArtistItem -> navController.navigate("artist/${item.id}")
                                                            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                                            is PodcastItem -> navController.navigate("podcast/${android.net.Uri.encode(item.browseId)}")
                                                            is EpisodeItem -> {
                                                                playerConnection.playQueue(
                                                                    ListQueue(
                                                                        title = item.podcast?.name ?: item.title,
                                                                        items = listOf(item.toMediaItem()),
                                                                    ),
                                                                )
                                                            }
                                                            else -> item
                                                        }
                                                    },
                                                    onLongClick =
                                                        if (item is PodcastItem || item is EpisodeItem) {
                                                            null
                                                        } else {
                                                            {
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                menuState.show {
                                                                    when (item) {
                                                                        is SongItem -> {
                                                                            YouTubeSongMenu(
                                                                                song = item,
                                                                                navController = navController,
                                                                                onDismiss = menuState::dismiss,
                                                                            )
                                                                        }

                                                                        is AlbumItem -> {
                                                                            YouTubeAlbumMenu(
                                                                                albumItem = item,
                                                                                navController = navController,
                                                                                onDismiss = menuState::dismiss,
                                                                            )
                                                                        }

                                                                        is ArtistItem -> {
                                                                            YouTubeArtistMenu(
                                                                                artist = item,
                                                                                onDismiss = menuState::dismiss,
                                                                            )
                                                                        }

                                                                        is PlaylistItem -> {
                                                                            YouTubePlaylistMenu(
                                                                                playlist = item,
                                                                                coroutineScope = coroutineScope,
                                                                                onDismiss = menuState::dismiss,
                                                                            )
                                                                        }

                                                                        is PodcastItem, is EpisodeItem -> Unit
                                                                    }
                                                                }
                                                            }
                                                        },
                                                ).animateItem(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    TopAppBar(
        title = { Text(browseResult?.title.orEmpty()) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}
