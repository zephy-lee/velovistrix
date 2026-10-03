package com.regnius.photoprism.feature.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.regnius.photoprism.R
import com.regnius.photoprism.core.model.MediaType
import com.regnius.photoprism.core.model.Photo
import com.regnius.photoprism.feature.photos.PhotoFeedViewModel

/**
 * 아래에서 위로 올렸을 때 나오는 EXIF 정보 (Phase 1.13).
 *
 * 값이 없는 항목은 "—" 로 채우지 않고 **줄 자체를 숨긴다.** 스캔한 사진이나
 * 메신저로 받은 이미지는 EXIF 가 거의 없어서, 빈 줄을 남기면 정보 시트가
 * 대시로만 가득 찬 화면이 된다.
 *
 * 그리드 목록 응답에는 카메라/EXIF 정보가 없는 경우가 많아, 상세
 * 조회(`getPhotoDetails`)로 받은 값으로만 채운다. 목록에서 받은 [photo] 를
 * 먼저 보여줬다가 상세 조회가 끝나면 다른 값으로 바뀌는 식이면 "카메라 정보가
 * 있다 없어졌다" 하는 것처럼 보인다 — 그래서 상세 조회가 끝나기 전까지는
 * 로딩 표시만 하고, 끝난 뒤에 값을 **한 번만** 그린다(상세 조회가 실패하면
 * 그때는 목록 값이라도 보여준다).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoInfoSheet(
    photo: Photo,
    onDismiss: () -> Unit,
    viewModel: PhotoFeedViewModel = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var detailedPhoto by remember(photo.uid) { mutableStateOf<Photo?>(null) }

    LaunchedEffect(photo.uid) {
        detailedPhoto = viewModel.getPhotoDetails(photo.uid)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // §17.32 — **기다리지 않는다.** 목록에서 온 [photo] 에 이미 보여줄 것이
        // 거의 다 들어 있으므로 그것으로 즉시 그리고, 서버 상세가 도착하면
        // 그때 더 채운다.
        //
        // 예전에는 상세 조회가 끝날 때까지 진행 표시만 띄우고 아무것도 안
        // 그렸다. 인터넷은 되는데 **내 서버만** 못 붙는 상황(집 밖, VPN 끊김,
        // 서버 다운)에서는 요청이 타임아웃까지 매달려서, 이미 손에 쥔 정보를
        // 두고도 스피너만 돌았다.
        val displayPhoto = detailedPhoto ?: photo

        // 어느 파일을 "이 항목의 파일 정보"로 보여줄지 고른다.
        // 비디오면 비디오 파일을, 아니면 메인(Primary) 파일을 우선한다.
        val videoFile = displayPhoto.files.firstOrNull { it.isVideo }
        val infoFile = if (displayPhoto.type == MediaType.VIDEO) {
            videoFile
        } else {
            displayPhoto.files.firstOrNull { it.isPrimary }
        } ?: displayPhoto.files.firstOrNull()

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                displayPhoto.title.ifBlank { stringResource(R.string.info_untitled) },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.width(8.dp))

            InfoRow(stringResource(R.string.info_taken), displayPhoto.takenAtLocal?.replace('T', ' ')?.removeSuffix("Z"))
            InfoRow(stringResource(R.string.info_camera), listOfNotNull(displayPhoto.cameraMake, displayPhoto.cameraModel).joinToString(" ").ifBlank { null })
            InfoRow(stringResource(R.string.info_lens), displayPhoto.lens)
            InfoRow(
                stringResource(R.string.info_exposure),
                listOfNotNull(
                    displayPhoto.exposure?.let { "$it s" },
                    displayPhoto.fNumber?.let { "f/%.1f".format(it) },
                    displayPhoto.iso?.let { "ISO $it" },
                ).joinToString(" · ").ifBlank { null },
            )
            InfoRow(
                stringResource(R.string.info_resolution),
                (infoFile?.width?.takeIf { it > 0 } ?: displayPhoto.width.takeIf { it > 0 })?.let { w ->
                    val h = infoFile?.height?.takeIf { it > 0 } ?: displayPhoto.height
                    "$w × $h"
                },
            )
            InfoRow(
                stringResource(R.string.info_location),
                if (displayPhoto.latitude != null && displayPhoto.longitude != null) {
                    "%.5f, %.5f".format(displayPhoto.latitude, displayPhoto.longitude)
                } else null,
            )
            InfoRow(stringResource(R.string.info_type), displayPhoto.type.apiValue.ifBlank { null })

            // 비디오 파일이 있으면 (동영상 타입이거나 Live Photo 등) 관련 정보를 보여준다.
            if (videoFile != null) {
                InfoRow(stringResource(R.string.info_duration), videoFile.durationNanos.takeIf { it > 0 }?.let { formatDuration(it) })
                InfoRow(
                    stringResource(R.string.info_video_codec),
                    listOfNotNull(
                        videoFile.codec?.uppercase(),
                        videoFile.videoProfile?.let { "($it)" }
                    ).joinToString(" ").ifBlank { null }
                )
                InfoRow(stringResource(R.string.info_audio_codec), videoFile.audioCodec?.uppercase())
                InfoRow(stringResource(R.string.info_frame_rate), videoFile.fps?.let { "%.2f fps".format(it) })
            }
            InfoRow(stringResource(R.string.info_file), infoFile?.name)
            InfoRow(stringResource(R.string.info_size), infoFile?.sizeBytes?.takeIf { it > 0 }?.let { formatBytes(it) })
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toFloat())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toFloat())
    else -> "%.0f KB".format(bytes / 1024f)
}

/** [durationNanos] 는 PhotoPrism 의 `Duration` 필드(나노초) 그대로다. */
private fun formatDuration(durationNanos: Long): String {
    val totalSeconds = durationNanos / 1_000_000_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
