/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.baskt.music.ui.screens.library

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.baskt.music.LocalPlayerAwareWindowInsets
import com.baskt.music.LocalPlayerConnection
import com.baskt.music.R
import com.baskt.music.extensions.toMediaItem
import com.baskt.music.playback.queues.ListQueue
import com.baskt.music.podcast.PodcastLibraryAction
import com.baskt.music.podcast.PodcastLibraryEvent
import com.baskt.music.podcast.PodcastLibraryItemUiModel
import com.baskt.music.podcast.PodcastLibraryScreenState
import com.baskt.music.ui.component.LocalMenuState
import com.baskt.music.ui.component.MediaDetailStatePanel
import com.baskt.music.ui.component.MenuSurfaceSection
import com.baskt.music.ui.component.NewMenuItem
import com.baskt.music.viewmodels.PodcastLibraryViewModel

@Composable
fun LibraryPodcastsScreen(
    navController: NavController,
    viewModel: PodcastLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val refreshState by viewModel.refreshState.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalMenuState.current
    val snackbarHostState = remember { SnackbarHostState() }
    val unknownErrorMessage = stringResource(R.string.error_unknown)
    val loginRequiredMessage = stringResource(R.string.not_logged_in_youtube)
    val syncDisabledMessage = stringResource(R.string.sync_disabled)
    val noEpisodesMessage = stringResource(R.string.podcast_has_no_episodes)
    val onRefresh = remember(viewModel) { { viewModel.onAction(PodcastLibraryAction.Refresh) } }
    val onRefreshErrorShown = remember(viewModel) { { viewModel.onRefreshErrorShown() } }
    val onPodcastClick =
        remember(navController) {
            { browseId: String -> navController.navigate("podcast/${Uri.encode(browseId)}") }
        }
    val onPodcastPlay =
        remember(viewModel) {
            { browseId: String -> viewModel.onAction(PodcastLibraryAction.PlayPodcast(browseId)) }
        }
    val onPodcastRemove =
        remember(viewModel) {
            { browseId: String -> viewModel.onAction(PodcastLibraryAction.RemovePodcast(browseId)) }
        }
    val onPodcastMenuClick =
        remember(menuState, onPodcastRemove) {
            { podcast: PodcastLibraryItemUiModel ->
                menuState.show {
                    PodcastLibraryMenu(
                        onRemove = { onPodcastRemove(podcast.browseId) },
                        onDismiss = menuState::dismiss,
                    )
                }
            }
        }

    LaunchedEffect(
        viewModel,
        playerConnection,
        unknownErrorMessage,
        loginRequiredMessage,
        syncDisabledMessage,
        noEpisodesMessage,
    ) {
        viewModel.events.collect { event ->
            when (event) {
                is PodcastLibraryEvent.Play -> {
                    playerConnection?.playQueue(
                        ListQueue(
                            title = event.request.title,
                            items = event.request.items.map { metadata -> metadata.toMediaItem() },
                            startIndex = event.request.startIndex,
                        ),
                    )
                }

                is PodcastLibraryEvent.ShowMessage -> {
                    val message =
                        when (event.messageResId) {
                            R.string.not_logged_in_youtube -> loginRequiredMessage
                            R.string.sync_disabled -> syncDisabledMessage
                            R.string.podcast_has_no_episodes -> noEpisodesMessage
                            else -> unknownErrorMessage
                        }
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LibraryRefreshContainer(
            state = refreshState,
            onRefresh = onRefresh,
            onErrorShown = onRefreshErrorShown,
            modifier = Modifier.fillMaxSize(),
        ) {
            LibraryPodcastsContent(
                state = state,
                onRetry = onRefresh,
                onPodcastClick = onPodcastClick,
                onPodcastPlay = onPodcastPlay,
                onPodcastMenuClick = onPodcastMenuClick,
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
        )
    }
}

@Composable
private fun BoxScope.LibraryPodcastsContent(
    state: PodcastLibraryScreenState,
    onRetry: () -> Unit,
    onPodcastClick: (String) -> Unit,
    onPodcastPlay: (String) -> Unit,
    onPodcastMenuClick: (PodcastLibraryItemUiModel) -> Unit,
) {
    val bottomPadding =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()

    when (state) {
        PodcastLibraryScreenState.Loading -> {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        PodcastLibraryScreenState.Empty -> {
            MediaDetailStatePanel(
                title = stringResource(R.string.podcast),
                description = stringResource(R.string.browse_empty_description),
                iconRes = R.drawable.mic,
                actionLabel = stringResource(R.string.retry),
                onAction = onRetry,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        is PodcastLibraryScreenState.Error -> {
            MediaDetailStatePanel(
                title = stringResource(R.string.podcast),
                description = stringResource(state.messageResId),
                iconRes = R.drawable.error,
                actionLabel = stringResource(R.string.retry),
                onAction = onRetry,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        is PodcastLibraryScreenState.Success -> {
            LazyColumn(
                contentPadding =
                    PaddingValues(
                        start = 24.dp,
                        top = LibraryHeaderContentPadding,
                        end = 24.dp,
                        bottom = bottomPadding + 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = state.uiState.podcasts,
                    key = PodcastLibraryItemUiModel::browseId,
                    contentType = { "library_podcast" },
                ) { podcast ->
                    val onClick = remember(podcast.browseId, onPodcastClick) { { onPodcastClick(podcast.browseId) } }
                    val onPlay = remember(podcast.browseId, onPodcastPlay) { { onPodcastPlay(podcast.browseId) } }
                    val onMenuClick = remember(podcast, onPodcastMenuClick) { { onPodcastMenuClick(podcast) } }
                    PodcastLibraryCard(
                        podcast = podcast,
                        onClick = onClick,
                        onPlay = onPlay,
                        onMenuClick = onMenuClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun PodcastLibraryCard(
    podcast: PodcastLibraryItemUiModel,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRemote = podcast.isSavedRemotely
    LibraryMediaListCard(
        title = podcast.title,
        subtitle = podcast.author ?: stringResource(R.string.podcast),
        thumbnailUrl = podcast.thumbnailUrl,
        sourceLabel = stringResource(if (isRemote) R.string.youtube_synced else R.string.personal_label),
        sourceColor = if (isRemote) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
        onClick = onClick,
        onPlay = onPlay,
        onMenuClick = onMenuClick,
        modifier = modifier,
    )
}

@Composable
private fun PodcastLibraryMenu(
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
        NewMenuItem(
            headlineContent = { Text(stringResource(R.string.remove_from_library)) },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.library_add_check),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
            onClick = {
                onDismiss()
                onRemove()
            },
        )
    }
}
