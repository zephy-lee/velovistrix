package com.regnius.photoprism.feature.transfer

import androidx.lifecycle.ViewModel
import com.regnius.photoprism.core.transfer.MediaTransferManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * [TransferUi] 가 쓰는 얇은 창구 (Phase 4.4~4.5).
 *
 * 상태는 전부 [MediaTransferManager](싱글턴)에 있다 — 전송이 화면보다 오래
 * 살아야 하기 때문이다. 이 ViewModel 은 그걸 Compose 가 구독할 수 있는 형태로
 * 그대로 통과시키기만 한다.
 */
@HiltViewModel
class TransferViewModel @Inject constructor(
    private val transfers: MediaTransferManager,
) : ViewModel() {
    val progress = transfers.progress
    val events = transfers.events

    fun cancel() = transfers.cancel()
}
