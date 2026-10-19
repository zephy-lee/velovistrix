package com.regnius.photoprism.feature.photos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.regnius.photoprism.R

/**
 * 선택 모드의 상단 바 (Phase 4.3~4.5).
 *
 * **그리드 안에 겹쳐 그린다.** 화면의 `TopAppBar` 를 바꿔치기하는 것이 Material
 * 의 표준 패턴이지만, 이 앱에서 사진 그리드를 품은 화면이 다섯 군데다(사진 탭 ·
 * 즐겨찾기 · 앨범 두 탭 · 라벨·인물 결과 · 검색 결과). 그 다섯의 상단 바를 전부
 * 선택 모드용으로 분기하면 같은 코드가 다섯 벌 생기고, 하나를 고칠 때 넷을
 * 잊는다. 그리드 자신이 띄우면 **어느 화면에서든 똑같이** 동작한다.
 */
@Composable
fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.selection_clear))
            }
            Text(
                pluralStringResource(R.plurals.selection_count, count, count),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 4.dp),
            )
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share))
                }
                IconButton(onClick = onSave) {
                    Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.action_save))
                }
            }
        }
    }
}
