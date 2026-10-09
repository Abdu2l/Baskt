/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.baskt.music.ui.player

import android.content.res.Configuration
import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import coil3.request.SuccessResult
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.C
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_READY
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import com.baskt.music.LocalDatabase
import com.baskt.music.LocalPlayerConnection
import com.baskt.music.R
import com.baskt.music.constants.BlurRadiusKey
import com.baskt.music.playback.VisualizerState
import com.baskt.music.constants.DisableBlurKey
import com.baskt.music.constants.EnableHapticFeedbackKey
import com.baskt.music.constants.LyricsBackgroundStyle
import com.baskt.music.constants.LyricsBackgroundStyleKey
import com.baskt.music.constants.LyricsMode
import com.baskt.music.constants.LyricsModeKey
import com.baskt.music.constants.PlayerBackgroundStyle
import com.baskt.music.constants.PlayerBackgroundStyleKey
import com.baskt.music.constants.PlayerCustomBlurKey
import com.baskt.music.constants.PlayerCustomBrightnessKey
import com.baskt.music.constants.PlayerCustomContrastKey
import com.baskt.music.constants.PlayerCustomImageUriKey
import com.baskt.music.constants.ShowLyricsPlayerControlsKey
import com.baskt.music.extensions.togglePlayPause
import com.baskt.music.models.MediaMetadata
import com.baskt.music.ui.component.LocalMenuState
import com.baskt.music.ui.component.LyricsEnhanced
import com.baskt.music.ui.component.LyricsV2
import com.baskt.music.ui.component.PlayerSliderTrack
import com.baskt.music.ui.menu.LyricsMenu
import com.baskt.music.ui.theme.PlayerColorExtractor
import com.baskt.music.utils.makeTimeString
import com.baskt.music.utils.rememberEnumPreference
import com.baskt.music.utils.rememberPreference
import com.baskt.music.viewmodels.LyricsRenderScreenState
import com.baskt.music.viewmodels.LyricsRenderViewModel
import kotlin.coroutines.cancellation.CancellationException

private val AppleMusicFallbackGradient =
    listOf(
        Color(0xFF202020),
        Color(0xFF141414),
        Color(0xFF050505),
    )

@Suppress("UNUSED_PARAMETER")
@Composable
fun LyricsScreen(
    mediaMetadata: MediaMetadata,
    onBackClick: () -> Unit,
    navController: NavController,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    onQueueClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    backHandlerEnabled: Boolean = true,
    lyricsRenderViewModel: LyricsRenderViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val view = LocalView.current

    val playbackState by playerConnection.playbackState.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val deviceMusicVolumeController = rememberDeviceMusicVolumeController()
    val onVolumeChange =
        remember(deviceMusicVolumeController) {
            { volume: Float ->
                deviceMusicVolumeController.setVolumeFraction(volume)
            }
        }
    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val lyricsRenderState by lyricsRenderViewModel.state.collectAsStateWithLifecycle()

    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
    val lyricsMode by rememberEnumPreference(LyricsModeKey, LyricsMode.ENHANCED)
    val playerBackground by rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)
    val configuredLyricsBackground by rememberEnumPreference(LyricsBackgroundStyleKey, LyricsBackgroundStyle.DEFAULT)
    val lyricsBackground = configuredLyricsBackground.resolveFor(playerBackground)
    val disableBlur by rememberPreference(DisableBlurKey, false)
    val blurRadius by rememberPreference(BlurRadiusKey, 48f)
    val playerCustomImageUri by rememberPreference(PlayerCustomImageUriKey, "")
    val playerCustomBlur by rememberPreference(PlayerCustomBlurKey, 0f)
    val playerCustomContrast by rememberPreference(PlayerCustomContrastKey, 1f)
    val playerCustomBrightness by rememberPreference(PlayerCustomBrightnessKey, 1f)
    val foregroundColor =
        if (lyricsBackground == LyricsBackgroundStyle.FOLLOW_THEME) {
            MaterialTheme.colorScheme.onSurface
        } else {
            Color.White
        }
    val showPlayerControlsState =
        rememberPreference(ShowLyricsPlayerControlsKey, true)
    val showPlayerControls by showPlayerControlsState
    val onShowPlayerControlsChange =
        remember(showPlayerControlsState) {
            { showControls: Boolean ->
                showPlayerControlsState.value = showControls
            }
        }

    val hapticClick =
        remember(enableHapticFeedback, view) {
            {
                if (enableHapticFeedback) {
                    view.performHapticFeedback(
                        HapticFeedbackConstants.CONTEXT_CLICK,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                    )
                }
            }
        }
    val lyricsHelper =
        remember(context) {
            EntryPointAccessors
                .fromApplication(
                    context.applicationContext,
                    com.baskt.music.di.LyricsHelperEntryPoint::class.java,
                ).lyricsHelper()
    }

    LaunchedEffect(mediaMetadata.id, currentLyrics?.lyrics) {
        if (mediaMetadata.isPodcast) return@LaunchedEffect
        if (currentLyrics != null) return@LaunchedEffect
        try {
            val existingLyrics =
                withContext(Dispatchers.IO) {
                    database.lyrics(mediaMetadata.id).first()
                }
            if (existingLyrics != null) return@LaunchedEffect

            val lyrics =
                withContext(Dispatchers.IO) {
                    lyricsHelper.getLyrics(mediaMetadata)
                }
            withContext(Dispatchers.IO) {
                database.query {
                    insertLyricsIfAbsent(
                        id = mediaMetadata.id,
                        lyrics = lyrics,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    val positionState = remember(mediaMetadata.id) { mutableLongStateOf(0L) }
    val durationState = remember(mediaMetadata.id) { mutableLongStateOf(C.TIME_UNSET) }
    var sliderPosition by remember(mediaMetadata.id) { mutableStateOf<Long?>(null) }
    var gradientColors by remember(mediaMetadata.thumbnailUrl) { mutableStateOf(AppleMusicFallbackGradient) }

    val lyricsDurationMs = durationState.longValue.takeIf { it != C.TIME_UNSET } ?: 0L
    LaunchedEffect(mediaMetadata.id, lyricsDurationMs) {
        lyricsRenderViewModel.bind(mediaMetadata.id, lyricsDurationMs)
    }

    val gradientColorsCache =
        remember {
            object : LinkedHashMap<String, List<Color>>(20, 0.75f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<String, List<Color>>) = size > 20
            }
        }
    val fallbackColor = remember { Color.Black.toArgb() }

    LaunchedEffect(mediaMetadata.id, mediaMetadata.thumbnailUrl, lyricsBackground) {
        if (lyricsBackground != LyricsBackgroundStyle.DEFAULT &&
            lyricsBackground != LyricsBackgroundStyle.COLORING &&
            lyricsBackground != LyricsBackgroundStyle.VISUALIZER
        ) {
            gradientColors = AppleMusicFallbackGradient
            return@LaunchedEffect
        }
        val thumbnailUrl = mediaMetadata.thumbnailUrl
        if (thumbnailUrl == null) {
            gradientColors = AppleMusicFallbackGradient
            return@LaunchedEffect
        }

        gradientColorsCache[thumbnailUrl]?.let {
            gradientColors = it
            return@LaunchedEffect
        }

        gradientColors = AppleMusicFallbackGradient

        val request =
            ImageRequest
                .Builder(context)
                .data(thumbnailUrl)
                .size(Size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE))
                .allowHardware(false)
                .build()

        val extractedColors =
            try {
                val image =
                    withContext(Dispatchers.IO) {
                        context.imageLoader.execute(request)
                    }.image
                if (image == null) {
                    null
                } else {
                    val bitmap = image.toBitmap()
                    withContext(Dispatchers.Default) {
                        val palette =
                            Palette
                                .from(bitmap)
                                .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                                .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                                .generate()
                        PlayerColorExtractor.extractGradientColors(
                            palette = palette,
                            fallbackColor = fallbackColor,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }

        gradientColors = extractedColors ?: AppleMusicFallbackGradient
        gradientColorsCache[thumbnailUrl] = gradientColors
    }

    LaunchedEffect(player, playbackState, mediaMetadata.id) {
        if (playbackState != STATE_READY && playbackState != STATE_BUFFERING) return@LaunchedEffect
        while (isActive) {
            positionState.longValue = player.currentPosition.coerceAtLeast(0L)
            durationState.longValue = player.duration
            delay(250)
        }
    }

    val showLyricsMenu = {
        menuState.show {
            LyricsMenu(
                lyricsProvider = { currentLyrics },
                mediaMetadataProvider = { mediaMetadata },
                lyricsSyncOffset = lyricsSyncOffset,
                onLyricsSyncOffsetChange = onLyricsSyncOffsetChange,
                showPlayerControlsState = showPlayerControlsState,
                onShowPlayerControlsChange = onShowPlayerControlsChange,
                onDismiss = menuState::dismiss,
            )
        }
    }

    val isLoading = playbackState == STATE_BUFFERING || sliderPosition != null
    val orientation = LocalConfiguration.current.orientation

    BackHandler(enabled = backHandlerEnabled, onBack = onBackClick)

    Box(
        modifier =
            modifier
                .fillMaxSize(),
    ) {
        LyricsScreenBackground(
            style = lyricsBackground,
            mediaMetadata = mediaMetadata,
            gradientColors = gradientColors,
            disableBlur = disableBlur,
            blurRadius = blurRadius,
            playerCustomImageUri = playerCustomImageUri,
            playerCustomBlur = playerCustomBlur,
            playerCustomContrast = playerCustomContrast,
            playerCustomBrightness = playerCustomBrightness,
            isPlaying = isPlaying,
        )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .consumeUnhandledPointerInput(),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            AppleMusicGrabber(onClick = onBackClick)
            AppleMusicTrackHeader(
                mediaMetadata = mediaMetadata,
                foregroundColor = foregroundColor,
                onMoreClick = showLyricsMenu,
                onDismissClick = onBackClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
            )

            if (orientation == Configuration.ORIENTATION_LANDSCAPE && showPlayerControls) {
                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 36.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppleMusicLyricsPane(
                        lyricsMode = lyricsMode,
                        lyricsState = lyricsRenderState,
                        foregroundColor = foregroundColor,
                        sliderPositionProvider = { sliderPosition },
                        lyricsSyncOffset = lyricsSyncOffset,
                        modifier =
                            Modifier
                                .weight(1.15f)
                                .fillMaxHeight()
                                .padding(end = 32.dp),
                    )

                    Column(
                        modifier =
                            Modifier
                                .weight(0.85f)
                                .widthIn(max = 420.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        AppleMusicControls(
                            positionProvider = { positionState.longValue },
                            durationProvider = { durationState.longValue },
                            sliderPosition = sliderPosition,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            volume = deviceMusicVolumeController.volumeFraction,
                            onPositionChange = { sliderPosition = it },
                            onPositionChangeFinished = {
                                sliderPosition?.let {
                                    player.seekTo(it)
                                    positionState.longValue = it
                                }
                                sliderPosition = null
                            },
                            onVolumeChange = onVolumeChange,
                            onPreviousClick = {
                                hapticClick()
                                playerConnection.seekToPrevious()
                            },
                            onPlayPauseClick = {
                                hapticClick()
                                player.togglePlayPause()
                            },
                            onNextClick = {
                                hapticClick()
                                playerConnection.seekToNext()
                            },
                            foregroundColor = foregroundColor,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                AppleMusicLyricsPane(
                    lyricsMode = lyricsMode,
                    lyricsState = lyricsRenderState,
                    foregroundColor = foregroundColor,
                    sliderPositionProvider = { sliderPosition },
                    lyricsSyncOffset = lyricsSyncOffset,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                )

                if (showPlayerControls) {
                    AppleMusicControls(
                        positionProvider = { positionState.longValue },
                        durationProvider = { durationState.longValue },
                        sliderPosition = sliderPosition,
                        isPlaying = isPlaying,
                        isLoading = isLoading,
                        volume = deviceMusicVolumeController.volumeFraction,
                        onPositionChange = { sliderPosition = it },
                        onPositionChangeFinished = {
                            sliderPosition?.let {
                                player.seekTo(it)
                                positionState.longValue = it
                            }
                            sliderPosition = null
                        },
                        onVolumeChange = onVolumeChange,
                        onPreviousClick = {
                            hapticClick()
                            playerConnection.seekToPrevious()
                        },
                        onPlayPauseClick = {
                            hapticClick()
                            player.togglePlayPause()
                        },
                        onNextClick = {
                            hapticClick()
                            playerConnection.seekToNext()
                        },
                        foregroundColor = foregroundColor,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 40.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsScreenBackground(
    style: LyricsBackgroundStyle,
    mediaMetadata: MediaMetadata,
    gradientColors: List<Color>,
    disableBlur: Boolean,
    blurRadius: Float,
    playerCustomImageUri: String,
    playerCustomBlur: Float,
    playerCustomContrast: Float,
    playerCustomBrightness: Float,
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    if (style == LyricsBackgroundStyle.FOLLOW_THEME) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        Color.Black
                    },
                ),
    ) {
        when (style) {
            LyricsBackgroundStyle.DEFAULT -> {
                AppleMusicBackground(
                    mediaMetadata = mediaMetadata,
                    gradientColors = gradientColors,
                )
            }

            LyricsBackgroundStyle.FOLLOW_THEME -> Unit

            LyricsBackgroundStyle.VISUALIZER -> {
                VisualizerBackground(
                    artworkUrl = mediaMetadata.thumbnailUrl,
                    gradientColors = gradientColors,
                    isPlaying = isPlaying,
                )
            }

            LyricsBackgroundStyle.ENERGETIC -> {
                EnergeticBackground(
                    artworkUrl = mediaMetadata.thumbnailUrl,
                    gradientColors = gradientColors,
                    isPlaying = isPlaying,
                )
            }

            LyricsBackgroundStyle.COLORING,
            LyricsBackgroundStyle.CUSTOM,
            -> {
                PlayerBackground(
                    playerBackground =
                        if (style == LyricsBackgroundStyle.CUSTOM) {
                            PlayerBackgroundStyle.CUSTOM
                        } else {
                            PlayerBackgroundStyle.COLORING
                        },
                    mediaMetadata = mediaMetadata,
                    gradientColors = gradientColors,
                    disableBlur = disableBlur,
                    blurRadius = blurRadius,
                    playerCustomImageUri = playerCustomImageUri,
                    playerCustomBlur = playerCustomBlur,
                    playerCustomContrast = playerCustomContrast,
                    playerCustomBrightness = playerCustomBrightness,
                )
            }
        }
    }
}

@Composable
private fun AppleMusicBackground(
    mediaMetadata: MediaMetadata,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val colors = if (gradientColors.isNotEmpty()) gradientColors else AppleMusicFallbackGradient
    val backgroundBrush =
        remember(colors) {
            Brush.verticalGradient(
                listOf(
                    colors.getOrElse(0) { AppleMusicFallbackGradient[0] }.copy(alpha = 0.88f),
                    colors.getOrElse(1) { AppleMusicFallbackGradient[1] }.copy(alpha = 0.76f),
                    colors.getOrElse(2) { AppleMusicFallbackGradient[2] }.copy(alpha = 0.96f),
                ),
            )
        }
    val bottomScrim =
        remember {
            Brush.verticalGradient(
                listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.28f),
                ),
            )
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(AppleMusicFallbackGradient.last()),
    ) {
        AnimatedContent(
            targetState = mediaMetadata.thumbnailUrl,
            transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(700)) },
            label = "lyrics-apple-background",
        ) { thumbnailUrl ->
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .blur(46.dp)
                            .alpha(0.62f),
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(backgroundBrush),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f)),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(bottomScrim),
        )
    }
}

@Composable
private fun VisualizerBackground(
    artworkUrl: String?,
    gradientColors: List<Color>,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val magnitudes by VisualizerState.magnitudes.collectAsStateWithLifecycle()
    val energy by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(900),
        label = "visualizer-energy",
    )
    val bass =
        remember(magnitudes, energy) {
            val raw = (magnitudes.getOrElse(0) { 0.15f } + magnitudes.getOrElse(1) { 0.15f }) / 2f
            0.15f + (raw - 0.15f) * energy
        }
    // Slow orbital drift so the backdrop stays liquid even when paused.
    val spin = rememberInfiniteTransition(label = "visualizer-spin")
    val driftX1 by spin.animateFloat(-1f, 1f, infiniteRepeatable(tween(28000, easing = LinearEasing)), label = "driftX1")
    val driftY1 by spin.animateFloat(1f, -1f, infiniteRepeatable(tween(34000, easing = LinearEasing)), label = "driftY1")
    val driftX2 by spin.animateFloat(1f, -1f, infiniteRepeatable(tween(31000, easing = LinearEasing)), label = "driftX2")
    val driftY2 by spin.animateFloat(-1f, 1f, infiniteRepeatable(tween(25000, easing = LinearEasing)), label = "driftY2")

    val palette = remember(gradientColors) {
        if (gradientColors.size >= 3) {
            gradientColors.take(3)
        } else {
            AppleMusicFallbackGradient
        }
    }
    val washA =
        remember(palette) {
            Brush.linearGradient(
                listOf(
                    palette.getOrElse(0) { AppleMusicFallbackGradient[0] },
                    palette.getOrElse(1) { AppleMusicFallbackGradient[1] },
                ),
            )
        }
    val washB =
        remember(palette) {
            Brush.linearGradient(
                listOf(
                    palette.getOrElse(2) { AppleMusicFallbackGradient[2] },
                    palette.getOrElse(0) { AppleMusicFallbackGradient[0] },
                ),
            )
        }
    val scrim =
        remember {
            Brush.verticalGradient(
                listOf(
                    Color.Black.copy(alpha = 0.45f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.55f),
                ),
            )
        }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        val base = remember(density, maxWidth, maxHeight) {
            with(density) { minOf(maxWidth, maxHeight).toPx() }
        }
        // Blurred artwork base, gently larger than the screen.
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.25f
                        scaleY = 1.25f
                    }
                    .blur(46.dp)
                    .alpha(0.60f),
        )
        // Corner-free radial washes drifting against each other: no edges,
        // no triangles, just liquid color. Audio only breathes intensity.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = driftX1 * base * 0.06f
                        translationY = driftY1 * base * 0.06f
                        scaleX = 1.35f + 0.25f * bass
                        scaleY = 1.35f + 0.25f * bass
                    }
                    .blur(90.dp)
                    .alpha(0.55f + 0.15f * bass)
                    .background(washA),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = driftX2 * base * 0.06f
                        translationY = driftY2 * base * 0.06f
                        scaleX = 1.45f + 0.20f * (1f - bass)
                        scaleY = 1.45f + 0.20f * (1f - bass)
                    }
                    .blur(90.dp)
                    .alpha(0.45f + 0.12f * bass)
                    .background(washB),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f)),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(scrim),
        )
    }
}

@Composable
private fun EnergeticBackground(
    artworkUrl: String?,
    gradientColors: List<Color>,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    // Cover-warping shader look (monochrome.tf style): the artwork itself
    // flows like liquid and breathes with the kick. Pre-Android 13 falls
    // back to the ambient Flow backdrop.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        VisualizerBackground(
            artworkUrl = artworkUrl,
            gradientColors = gradientColors,
            isPlaying = isPlaying,
            modifier = modifier,
        )
        return
    }
    val context = LocalContext.current
    var cover by remember(artworkUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(artworkUrl) {
        cover =
            withContext(Dispatchers.IO) {
                runCatching {
                    if (artworkUrl.isNullOrBlank()) {
                        return@runCatching null
                    }
                    val loader = context.applicationContext.imageLoader
                    val request =
                        ImageRequest
                            .Builder(context.applicationContext)
                            .data(artworkUrl)
                            .size(256, 256)
                            .allowHardware(false)
                            .build()
                    val result = loader.execute(request)
                    if (result is SuccessResult) {
                        result.image.toBitmap().asImageBitmap()
                    } else {
                        null
                    }
                }.getOrNull()
            }
    }
    val magnitudes by VisualizerState.magnitudes.collectAsStateWithLifecycle()
    val energy by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(900),
        label = "warp-energy",
    )
    val scrim =
        remember {
            Brush.verticalGradient(
                listOf(
                    Color.Black.copy(alpha = 0.45f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.60f),
                ),
            )
        }
    val bitmap = cover
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (bitmap != null) {
            WarpCanvas(
                bitmap = bitmap,
                magnitudes = magnitudes,
                energy = energy,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            VisualizerBackground(
                artworkUrl = artworkUrl,
                gradientColors = gradientColors,
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(scrim),
        )
    }
}

@Composable
private fun WarpCanvas(
    bitmap: ImageBitmap,
    magnitudes: FloatArray,
    energy: Float,
    modifier: Modifier = Modifier,
) {
    val shader = remember(bitmap) { RuntimeShader(WARP_SHADER) }
    val latestMagnitudes by rememberUpdatedState(magnitudes)
    var timeSeconds by remember { mutableFloatStateOf(0f) }
    var beat by remember { mutableFloatStateOf(0f) }
    var bassAverage by remember { mutableFloatStateOf(0.15f) }
    LaunchedEffect(Unit) {
        var startNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (startNanos == 0L) startNanos = now
                timeSeconds = (now - startNanos) / 1_000_000_000f
                val mags = latestMagnitudes
                val bass = mags.getOrElse(0) { 0.15f }
                bassAverage += (bass - bassAverage) * 0.06f
                val rawKick = ((bass - bassAverage * 1.35f) * energy).coerceIn(0f, 1f)
                beat += (rawKick - beat) * 0.45f
            }
        }
    }
    Canvas(modifier = modifier) {
        shader.setFloatUniform("uRes", size.width, size.height)
        shader.setFloatUniform("uTime", timeSeconds)
        shader.setFloatUniform("uBeat", beat)
        shader.setInputShader(
            "uTex",
            ImageShader(bitmap, TileMode.Clamp, TileMode.Clamp),
        )
        drawRect(brush = ShaderBrush(shader))
    }
}

private const val WARP_SHADER = """
uniform shader uTex;
uniform float uTime;
uniform float uBeat;
uniform float2 uRes;

vec2 warpHash(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return fract(sin(p) * 43758.5453);
}

float warpNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = dot(warpHash(i), f);
    float b = dot(warpHash(i + vec2(1.0, 0.0)), f - vec2(1.0, 0.0));
    float c = dot(warpHash(i + vec2(0.0, 1.0)), f - vec2(0.0, 1.0));
    float d = dot(warpHash(i + vec2(1.0, 1.0)), f - vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float warpFbm(vec2 p) {
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        v += amp * warpNoise(p);
        p *= 2.03;
        amp *= 0.5;
    }
    return v;
}

half4 main(float2 fragCoord) {
    vec2 uv = fragCoord / uRes;
    float t = uTime * 0.05;
    vec2 q = vec2(
        warpFbm(uv * 2.0 + t),
        warpFbm(uv * 2.0 + vec2(5.2, 1.3) - t * 0.8)
    );
    vec2 r = vec2(
        warpFbm(uv * 2.0 + 2.5 * q + vec2(1.7, 9.2) + 0.2 * t + uBeat * 0.25),
        warpFbm(uv * 2.0 + 2.5 * q + vec2(8.3, 2.8) - 0.15 * t)
    );
    float zoom = 1.0 + 0.12 * sin(uTime * 0.15) + uBeat * 0.10;
    vec2 warpUv = (uv - 0.5) / zoom + 0.5 + 0.18 * (r - 0.5);
    vec3 col = uTex.eval(warpUv * uRes).rgb;
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(lum), col, 1.25);
    float d = distance(uv, vec2(0.5));
    col *= 1.0 - 0.55 * d * d;
    return half4(col, 1.0);
}
"""


@Composable
private fun AppleMusicGrabber(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val closeDescription = stringResource(R.string.close)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(44.dp)
                .semantics { contentDescription = closeDescription }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
    )
}

@Composable
private fun AppleMusicTrackHeader(
    mediaMetadata: MediaMetadata,
    foregroundColor: Color,
    onMoreClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artistText =
        remember(mediaMetadata.id, mediaMetadata.artists) {
            mediaMetadata.artists.joinToString { it.name }
        }

    Row(
        modifier = modifier.heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(foregroundColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = mediaMetadata.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (mediaMetadata.thumbnailUrl == null) {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = foregroundColor.copy(alpha = 0.72f),
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = mediaMetadata.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = foregroundColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artistText,
                style = MaterialTheme.typography.bodyLarge,
                color = foregroundColor.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        AppleMusicHeaderIconButton(
            iconRes = R.drawable.close,
            contentDescription = stringResource(R.string.close),
            foregroundColor = foregroundColor,
            onClick = onDismissClick,
        )

        Spacer(modifier = Modifier.width(4.dp))

        AppleMusicHeaderIconButton(
            iconRes = R.drawable.more_horiz,
            contentDescription = stringResource(R.string.more_options),
            foregroundColor = foregroundColor,
            onClick = onMoreClick,
        )
    }
}

@Composable
private fun AppleMusicHeaderIconButton(
    iconRes: Int,
    contentDescription: String,
    foregroundColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 24.dp),
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(foregroundColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = foregroundColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun AppleMusicLyricsPane(
    lyricsMode: LyricsMode,
    lyricsState: LyricsRenderScreenState,
    foregroundColor: Color,
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    modifier: Modifier = Modifier,
) {
    LyricsContent(
        lyricsMode = lyricsMode,
        lyricsState = lyricsState,
        sliderPositionProvider = sliderPositionProvider,
        lyricsSyncOffset = lyricsSyncOffset,
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        textColor = foregroundColor,
    )
}

@Composable
private fun AppleMusicControls(
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    sliderPosition: Long?,
    isPlaying: Boolean,
    isLoading: Boolean,
    volume: Float,
    onPositionChange: (Long) -> Unit,
    onPositionChangeFinished: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    foregroundColor: Color,
    modifier: Modifier = Modifier,
) {
    val position = positionProvider()
    val duration = durationProvider()
    val hasDuration = duration != C.TIME_UNSET && duration > 0L
    val safeDuration = if (hasDuration) duration else 1L
    val currentPosition = (sliderPosition ?: position).coerceIn(0L, safeDuration)
    val remainingPosition = (safeDuration - currentPosition).coerceAtLeast(0L)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppleMusicSlider(
            value = currentPosition.toFloat(),
            valueRange = 0f..safeDuration.toFloat(),
            activeColor = foregroundColor.copy(alpha = 0.94f),
            inactiveColor = foregroundColor.copy(alpha = 0.28f),
            trackHeight = 8.dp,
            onValueChange = { onPositionChange(it.toLong()) },
            onValueChangeFinished = onPositionChangeFinished,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = makeTimeString(currentPosition),
                style = MaterialTheme.typography.labelMedium,
                color = foregroundColor.copy(alpha = 0.54f),
            )
            Text(
                text = if (hasDuration) "-${makeTimeString(remainingPosition)}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = foregroundColor.copy(alpha = 0.54f),
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppleMusicTransportButton(
                iconRes = R.drawable.skip_previous,
                contentDescription = stringResource(R.string.widget_previous),
                iconSize = 44.dp,
                touchSize = 68.dp,
                foregroundColor = foregroundColor,
                onClick = onPreviousClick,
            )
            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier.size(74.dp),
            ) {
                if (isLoading) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(42.dp),
                        color = foregroundColor,
                    )
                } else {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription =
                            if (isPlaying) {
                                stringResource(R.string.widget_pause)
                            } else {
                                stringResource(R.string.play)
                            },
                        tint = foregroundColor,
                        modifier = Modifier.size(54.dp),
                    )
                }
            }
            AppleMusicTransportButton(
                iconRes = R.drawable.skip_next,
                contentDescription = stringResource(R.string.next),
                iconSize = 44.dp,
                touchSize = 68.dp,
                foregroundColor = foregroundColor,
                onClick = onNextClick,
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp, bottom = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.volume_off),
                contentDescription = stringResource(R.string.minimum_volume),
                tint = foregroundColor.copy(alpha = 0.66f),
                modifier = Modifier.size(17.dp),
            )
            AppleMusicSlider(
                value = volume.coerceIn(0f, 1f),
                valueRange = 0f..1f,
                activeColor = foregroundColor.copy(alpha = 0.88f),
                inactiveColor = foregroundColor.copy(alpha = 0.24f),
                trackHeight = 8.dp,
                onValueChange = onVolumeChange,
                onValueChangeFinished = {},
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
            )
            Icon(
                painter = painterResource(R.drawable.volume_up),
                contentDescription = stringResource(R.string.maximum_volume),
                tint = foregroundColor.copy(alpha = 0.66f),
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun AppleMusicTransportButton(
    iconRes: Int,
    contentDescription: String?,
    iconSize: Dp,
    touchSize: Dp,
    foregroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(touchSize),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = foregroundColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppleMusicSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    activeColor: Color,
    inactiveColor: Color,
    trackHeight: Dp,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeStart = valueRange.start
    val safeEnd = valueRange.endInclusive.coerceAtLeast(safeStart + 1f)
    val safeRange = safeStart..safeEnd
    val sliderColors =
        SliderDefaults.colors(
            activeTrackColor = activeColor,
            activeTickColor = activeColor,
            thumbColor = Color.Transparent,
            inactiveTrackColor = inactiveColor,
        )

    Slider(
        value = value.coerceIn(safeRange),
        valueRange = safeRange,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        colors = sliderColors,
        thumb = { Spacer(modifier = Modifier.size(0.dp)) },
        track = { sliderState ->
            PlayerSliderTrack(
                sliderState = sliderState,
                colors = sliderColors,
                trackHeight = trackHeight,
            )
        },
        modifier = modifier.height(28.dp),
    )
}

@Composable
private fun LyricsContent(
    lyricsMode: LyricsMode,
    lyricsState: LyricsRenderScreenState,
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    when (lyricsMode) {
        LyricsMode.V2 -> {
            LyricsV2(
                lyricsState = lyricsState,
                sliderPositionProvider = sliderPositionProvider,
                lyricsSyncOffset = lyricsSyncOffset,
                modifier = modifier,
                textColorOverride = textColor,
            )
        }

        LyricsMode.ENHANCED -> {
            LyricsEnhanced(
                lyricsState = lyricsState,
                sliderPositionProvider = sliderPositionProvider,
                lyricsSyncOffset = lyricsSyncOffset,
                modifier = modifier,
                textColorOverride = textColor,
            )
        }
    }
}
