package com.regnius.photoprism.feature.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.regnius.photoprism.core.data.FeedKey
import com.regnius.photoprism.core.data.PhotoFeedController
import com.regnius.photoprism.core.data.toDomain
import com.regnius.photoprism.core.model.Photo
import com.regnius.photoprism.core.settings.AppSettingsRepository
import com.regnius.photoprism.core.ui.GridDensity
import com.regnius.photoprism.core.ui.SharedPhotoTransition
import com.regnius.photoprism.core.ui.ThumbnailLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 사진 목록을 쓰는 모든 화면이 공유하는 ViewModel.
 *
 * 전체 타임라인 · 앨범 이미지 탭 · 앨범 동영상 탭 · 즐겨찾기 · 검색 결과가 전부
 * 같은 엔드포인트에 파라미터만 다르게 던지므로, [FeedKey] 를 받아 하나로 처리한다.
 */
@HiltViewModel
class PhotoFeedViewModel @Inject constructor(
    private val controller: PhotoFeedController,
    private val clientProvider: com.regnius.photoprism.core.network.PhotoPrismClientProvider,
    val thumbnails: ThumbnailLoader,
    private val sharedPhoto: SharedPhotoTransition,
    settings: AppSettingsRepository,
) : ViewModel() {

    /** 그리드 ↔ 뷰어 전환의 주인공이 될 사진. §17.45 */
    val sharedPhotoUid: StateFlow<String?> = sharedPhoto.photoUid

    fun showSharedPhoto(uid: String) = sharedPhoto.show(uid)

    fun feed(key: FeedKey): Flow<PagingData<Photo>> = controller.feed(key)

    /** 당겨서 새로고침 직전에 호출 — [PhotoFeedController.invalidateFeed] 참고. */
    suspend fun invalidateFeed(key: FeedKey) = controller.invalidateFeed(key)

    /** 사진의 모든 메타데이터를 포함한 상세 정보를 가져온다. */
    suspend fun getPhotoDetails(uid: String): Photo? = runCatching {
        clientProvider.currentApi?.getPhoto(uid)?.toDomain()
    }.getOrNull()

    /**
     * 앨범 탭·검색 결과)이 전부 여기서 읽으므로, 설정 화면에서 바꾸면 다음에
     * 그 화면을 열 때 바로 반영된다.
     */
    val gridDensity: StateFlow<GridDensity> = settings.gridDensity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GridDensity.DEFAULT)
}
