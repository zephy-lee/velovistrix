package com.regnius.photoprism.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import com.regnius.photoprism.R

/**
 * 로딩 · 빈 상태 · 오류를 한 곳에서 처리한다 (Phase 1.14).
 *
 * 화면마다 따로 만들면 "앨범은 재시도 버튼이 있는데 사진은 없는" 식으로
 * 어긋난다. 특히 **오류에 재시도 경로를 반드시 주는 것**이 중요하다.
 * 셀프호스팅 서버는 잠깐 안 뜨는 일이 흔한데, 그때마다 앱을 껐다 켜야 하면
 * 서버가 아니라 앱이 불안정하다고 느끼게 된다.
 */
@Composable
fun FeedStateOverlay(
    loadState: CombinedLoadStates,
    itemCount: Int,
    emptyMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val refresh = loadState.refresh
    when {
        refresh is LoadState.Loading && itemCount == 0 ->
            Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

        refresh is LoadState.Error && itemCount == 0 ->
            Box(modifier.fillMaxSize(), Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text(stringResource(R.string.common_load_failed), style = MaterialTheme.typography.titleMedium)
                    Text(
                        refresh.error.message ?: refresh.error.javaClass.simpleName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
                }
            }

        refresh is LoadState.NotLoading && itemCount == 0 ->
            Box(modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
    }
}
