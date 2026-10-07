/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.baskt.music.lyrics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.baskt.music.constants.LyricsClickKey
import com.baskt.music.constants.LyricsLineBlurKey
import com.baskt.music.constants.LyricsLineSpacingKey
import com.baskt.music.constants.LyricsRomanizeChineseKey
import com.baskt.music.constants.LyricsRomanizeHindiKey
import com.baskt.music.constants.LyricsRomanizeJapaneseKey
import com.baskt.music.constants.LyricsRomanizeKoreanKey
import com.baskt.music.constants.LyricsRomanizeOtherLanguagesKey
import com.baskt.music.constants.LyricsScrollKey
import com.baskt.music.constants.LyricsTextSizeKey
import com.baskt.music.constants.LyricsV2BounceFactorKey
import com.baskt.music.constants.LyricsV2FillTransitionWidthKey
import com.baskt.music.constants.LyricsV2GlowFactorKey
import com.baskt.music.constants.LyricsV2LrcBounceEnabledKey
import com.baskt.music.db.MusicDatabase
import com.baskt.music.utils.dataStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRenderingRepository
    @Inject
    constructor(
        private val database: MusicDatabase,
        @ApplicationContext context: Context,
    ) {
        private val preferences = context.dataStore.data

        fun observeLyrics(mediaId: String): Flow<String?> =
            database
                .lyrics(mediaId)
                .map { entity -> entity?.lyrics }
                .distinctUntilChanged()

        fun observePreferences(): Flow<LyricsRenderingPreferences> =
            preferences
                .map { values ->
                    LyricsRenderingPreferences(
                        clickEnabled = values[LyricsClickKey] ?: true,
                        scrollEnabled = values[LyricsScrollKey] ?: true,
                        textSizeSp = values[LyricsTextSizeKey] ?: 26f,
                        lineSpacing = values[LyricsLineSpacingKey] ?: 1.3f,
                        lineBlurEnabled = values[LyricsLineBlurKey] ?: true,
                        v2BounceFactor = values[LyricsV2BounceFactorKey] ?: 1f,
                        v2GlowFactor = values[LyricsV2GlowFactorKey] ?: 1f,
                        v2FillTransitionWidthDp = values[LyricsV2FillTransitionWidthKey] ?: 8f,
                        v2LrcBounceEnabled = values[LyricsV2LrcBounceEnabledKey] ?: true,
                        romanization =
                            LyricsRomanizationPreferences(
                                romanizeJapanese = values[LyricsRomanizeJapaneseKey] ?: true,
                                romanizeKorean = values[LyricsRomanizeKoreanKey] ?: true,
                                romanizeChinese = values[LyricsRomanizeChineseKey] ?: true,
                                romanizeHindi = values[LyricsRomanizeHindiKey] ?: true,
                                romanizeOther = values[LyricsRomanizeOtherLanguagesKey] ?: true,
                            ),
                    )
                }.distinctUntilChanged()
    }
