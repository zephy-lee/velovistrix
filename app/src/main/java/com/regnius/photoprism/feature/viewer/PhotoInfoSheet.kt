package com.regnius.photoprism.feature.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
 * 조회(`getPhotoDetails`)로 받은 값으로 채운다.
 *
 * **Phase 3.7 — 값을 두 번 누르면 복사되고, 위치는 한 번 누르면 지도가 열린다.**
 * 두 동작을 한 줄에 겹쳐 둔 이유는, 정보 시트의 줄이 원래 "읽는 것" 이라
 * 한 번 누르는 제스처가 비어 있었기 때문이다. 복사를 두 번 누르기로 둔 것은
 * 사용자 지정(2026-10-10)이고, 위치만 한 번 누르기를 추가로 쓴다 — 지도는
 * 화면이 바뀌는 큰 동작이라 실수로 눌리면 곤란하지만, 거기만 아이콘이 붙어
 * 있어 "누를 수 있는 줄" 이라는 신호가 명확하다.
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
    val context = LocalContext.current

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
        //
        // GIF 만 예외다 (§22.4) — primary 가 정지 사이드카(`*.gif.jpg`)라
        // 그대로 보여주면 이름·크기·해상도가 전부 그 JPEG 것이 된다. 저장
        // 되는 파일(`TransferTargets.fileFor`)과 같은 것을 보여줘야 한다.
        val videoFile = displayPhoto.files.firstOrNull { it.isVideo }
        val infoFile = when (displayPhoto.type) {
            MediaType.VIDEO -> videoFile
            MediaType.ANIMATED -> displayPhoto.animatedFile
            else -> displayPhoto.files.firstOrNull { it.isPrimary }
        } ?: displayPhoto.files.firstOrNull { it.isPrimary }
            ?: displayPhoto.files.firstOrNull()

        val copiedMessage = stringResource(R.string.info_copied)
        val copy: (String) -> Unit = { value -> copyToClipboard(context, value, copiedMessage) }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val title = displayPhoto.title.ifBlank { stringResource(R.string.info_untitled) }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.copyOnDoubleTap(displayPhoto.title.ifBlank { null }, copy),
            )
            Spacer(Modifier.width(8.dp))

            InfoRow(
                stringResource(R.string.info_taken),
                displayPhoto.takenAtLocal?.replace('T', ' ')?.removeSuffix("Z"),
                onCopy = copy,
            )
            // 카메라는 복사가 특히 쓸모 있는 줄이다 — 같은 기종으로 찍은
            // 사진을 찾으려면 이 문자열을 검색창에 그대로 넣으면 된다
            // (사용자 요청, 2026-10-10). 그래서 화면에 보이는 그대로,
            // 검색 문법을 덧붙이지 않고 복사한다.
            InfoRow(
                stringResource(R.string.info_camera),
                listOfNotNull(displayPhoto.cameraMake, displayPhoto.cameraModel)
                    .joinToString(" ").ifBlank { null },
                onCopy = copy,
            )
            InfoRow(stringResource(R.string.info_lens), displayPhoto.lens, onCopy = copy)
            InfoRow(
                stringResource(R.string.info_exposure),
                listOfNotNull(
                    displayPhoto.exposure?.let { "$it s" },
                    displayPhoto.fNumber?.let { "f/%.1f".format(it) },
                    displayPhoto.iso?.let { "ISO $it" },
                ).joinToString(" · ").ifBlank { null },
                onCopy = copy,
            )
            InfoRow(
                stringResource(R.string.info_resolution),
                (infoFile?.width?.takeIf { it > 0 } ?: displayPhoto.width.takeIf { it > 0 })?.let { w ->
                    val h = infoFile?.height?.takeIf { it > 0 } ?: displayPhoto.height
                    "$w × $h"
                },
                onCopy = copy,
            )

            val latitude = displayPhoto.latitude
            val longitude = displayPhoto.longitude
            if (latitude != null && longitude != null) {
                val noMapApp = stringResource(R.string.info_no_map_app)
                val chooserTitle = stringResource(R.string.info_open_in_maps)
                InfoRow(
                    stringResource(R.string.info_location),
                    // 복사되는 값과 보이는 값을 같게 둔다 — 소수점 5자리는
                    // 1m 안쪽이라 어디에 붙여넣어도 같은 지점을 가리킨다.
                    "%.5f, %.5f".format(latitude, longitude),
                    onCopy = copy,
                    onTap = {
                        openMap(context, latitude, longitude, title, chooserTitle, noMapApp)
                    },
                    trailingIcon = Icons.Filled.Map,
                    trailingIconDescription = chooserTitle,
                )
            }

            InfoRow(stringResource(R.string.info_type), displayPhoto.type.apiValue.ifBlank { null }, onCopy = copy)

            // 비디오 파일이 있으면 (동영상 타입이거나 Live Photo 등) 관련 정보를 보여준다.
            if (videoFile != null) {
                InfoRow(
                    stringResource(R.string.info_duration),
                    videoFile.durationNanos.takeIf { it > 0 }?.let { formatDuration(it) },
                    onCopy = copy,
                )
                InfoRow(
                    stringResource(R.string.info_video_codec),
                    listOfNotNull(
                        videoFile.codec?.uppercase(),
                        videoFile.videoProfile?.let { "($it)" }
                    ).joinToString(" ").ifBlank { null },
                    onCopy = copy,
                )
                InfoRow(stringResource(R.string.info_audio_codec), videoFile.audioCodec?.uppercase(), onCopy = copy)
                InfoRow(
                    stringResource(R.string.info_frame_rate),
                    videoFile.fps?.let { "%.2f fps".format(it) },
                    onCopy = copy,
                )
            }
            InfoRow(stringResource(R.string.info_file), infoFile?.name, onCopy = copy)
            InfoRow(
                stringResource(R.string.info_size),
                infoFile?.sizeBytes?.takeIf { it > 0 }?.let { formatBytes(it) },
                onCopy = copy,
            )

            // 두 번 누르기는 보이지 않는 제스처라 알려주지 않으면 아무도 못
            // 찾는다. 줄마다 아이콘을 붙이는 대신 맨 아래 한 줄로 둔다 —
            // 정보 시트는 읽는 화면이고, 줄마다 버튼이 붙으면 값을 읽기가
            // 더 어려워진다.
            Text(
                stringResource(R.string.info_copy_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/**
 * 정보 한 줄.
 *
 * @param onCopy 값을 두 번 눌렀을 때 클립보드로 보낼 함수.
 * @param onTap 한 번 눌렀을 때. 위치 줄만 쓴다(지도 열기).
 */
@Composable
private fun InfoRow(
    label: String,
    value: String?,
    onCopy: (String) -> Unit,
    onTap: (() -> Unit)? = null,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailingIconDescription: String? = null,
) {
    if (value.isNullOrBlank()) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .pointerInput(value, onTap) {
                detectTapGestures(
                    onDoubleTap = { onCopy(value) },
                    // onTap 이 null 이면 아무것도 넘기지 않는다 — 넘기면
                    // detectTapGestures 가 한 번 누르기를 기다리느라 두 번
                    // 누르기 판정이 늦어진다.
                    onTap = onTap?.let { tap -> { tap() } },
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        if (trailingIcon != null) {
            Icon(
                trailingIcon,
                contentDescription = trailingIconDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp).size(18.dp),
            )
        }
    }
}

/** 제목처럼 [InfoRow] 가 아닌 글자에도 같은 "두 번 눌러 복사" 를 붙인다. */
@Composable
private fun Modifier.copyOnDoubleTap(value: String?, onCopy: (String) -> Unit): Modifier =
    if (value.isNullOrBlank()) {
        this
    } else {
        this.pointerInput(value) { detectTapGestures(onDoubleTap = { onCopy(value) }) }
    }

/**
 * 클립보드에 넣고 **API 32 이하에서만** 토스트를 띄운다.
 *
 * 안드로이드 13(API 33)부터는 시스템이 복사될 때마다 자기 안내를 띄운다.
 * 거기서 우리 토스트까지 띄우면 같은 말이 두 번 뜬다. 이 앱의 minSdk 는 31
 * 이라 안내가 아예 없는 버전(31·32)이 지원 범위에 들어 있어, 그쪽만 채운다.
 */
private fun copyToClipboard(context: Context, value: String, message: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(null, value))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

/**
 * 지도 앱으로 좌표를 넘긴다 (Phase 3.7).
 *
 * **항상 선택창을 띄운다**(사용자 요청, 2026-10-10). 기본 앱이 정해져 있으면
 * `startActivity` 는 묻지 않고 그 앱으로 바로 가는데, 사진의 위치는 구글 지도로
 * 볼 때와 다른 앱으로 볼 때가 각각 쓸모가 있어서 매번 고르게 두는 편이 낫다.
 *
 * `geo:` 를 받는 앱이 하나도 없으면 웹 주소로 폴백한다 — 그러면 브라우저가
 * 받는다. 지도 앱이 없는 기기(태블릿·커스텀 롬)에서 "아무 일도 안 일어나는"
 * 것을 막는 길이다. 조회가 가능한 이유는 매니페스트의 `<queries>` 에 geo
 * 인텐트를 선언해 뒀기 때문이다 — 안드로이드 11+ 는 선언 없이는 다른 앱이
 * 보이지 않는다.
 */
private fun openMap(
    context: Context,
    latitude: Double,
    longitude: Double,
    label: String?,
    chooserTitle: String,
    failureMessage: String,
) {
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse(MapLink.geoUri(latitude, longitude, label)))
    val hasMapApp = context.packageManager.queryIntentActivities(geo, 0).isNotEmpty()
    val target = if (hasMapApp) {
        geo
    } else {
        Intent(Intent.ACTION_VIEW, Uri.parse(MapLink.webUrl(latitude, longitude)))
    }

    runCatching { context.startActivity(Intent.createChooser(target, chooserTitle)) }
        .onFailure { Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show() }
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
