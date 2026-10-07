/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.baskt.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.baskt.music.aicontentfilter.FilterAiContentUseCase
import com.baskt.music.aicontentfilter.LoadAiContentFilterPolicyUseCase
import com.baskt.music.constants.HideExplicitKey
import com.baskt.music.constants.HideVideoKey
import com.baskt.music.db.MusicDatabase
import com.baskt.music.extensions.filterBlockedArtists
import com.baskt.music.innertube.YouTube
import com.baskt.music.innertube.pages.BrowseResult
import com.baskt.music.utils.dataStore
import com.baskt.music.utils.get
import com.baskt.music.utils.reportException
import javax.inject.Inject

@HiltViewModel
class YouTubeBrowseViewModel
    @Inject
    constructor(
        @ApplicationContext val context: Context,
        private val database: MusicDatabase,
        savedStateHandle: SavedStateHandle,
        private val loadAiContentFilterPolicy: LoadAiContentFilterPolicyUseCase,
        private val filterAiContent: FilterAiContentUseCase,
    ) : ViewModel() {
        private val browseId = savedStateHandle.get<String>("browseId")!!
        private val params = savedStateHandle.get<String>("params")

        val result = MutableStateFlow<BrowseResult?>(null)

        init {
            viewModelScope.launch {
                YouTube
                    .browse(browseId, params)
                    .onSuccess {
                        val hideVideo = context.dataStore.get(HideVideoKey, false)
                        val aiContentFilterPolicy = loadAiContentFilterPolicy()
                        val contentFilteredResult =
                            it
                                .filterExplicit(context.dataStore.get(HideExplicitKey, false))
                                .filterVideo(hideVideo)
                                .filterBlockedArtists(database.getBlockedArtistIds().toSet())
                        result.value =
                            contentFilteredResult.copy(
                                items =
                                    contentFilteredResult.items.mapNotNull { section ->
                                        section
                                            .copy(items = filterAiContent(section.items, aiContentFilterPolicy))
                                            .takeIf { filteredSection -> filteredSection.items.isNotEmpty() }
                                    },
                            )
                    }.onFailure {
                        reportException(it)
                    }
            }
        }
    }
