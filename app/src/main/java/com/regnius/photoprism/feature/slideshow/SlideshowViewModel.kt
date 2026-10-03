package com.regnius.photoprism.feature.slideshow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.regnius.photoprism.core.data.FeedKey
import com.regnius.photoprism.core.data.PhotoFeedController
import com.regnius.photoprism.core.model.Photo
import com.regnius.photoprism.core.settings.AppSettingsRepository
import com.regnius.photoprism.core.settings.SlideshowInterval
import com.regnius.photoprism.core.ui.ThumbnailLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 슬라이드쇼 (Phase 3.1).
 *
 * 목록은 [PhotoFeedController] 에서 받는다 — 뷰어와 같은 이유로, 그리드가
 * 이미 로드해 둔 **같은** 페이징 스트림을 그대로 물려받아야 슬라이드쇼를
 * 시작하는 순간 네트워크를 다시 치지 않는다(§12.5).
 */
@HiltViewModel
class SlideshowViewModel @Inject constructor(
    private val controller: PhotoFeedController,
    val thumbnails: ThumbnailLoader,
    settings: AppSettingsRepository,
) : ViewModel() {

    fun feed(key: FeedKey): Flow<PagingData<Photo>> = controller.feed(key)

    val interval: StateFlow<SlideshowInterval> = settings.slideshowInterval
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SlideshowInterval.S5)

    val kenBurns: StateFlow<Boolean> = settings.slideshowKenBurns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val shuffle: StateFlow<Boolean> = settings.slideshowShuffle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
