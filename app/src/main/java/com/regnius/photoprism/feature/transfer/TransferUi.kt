package com.regnius.photoprism.feature.transfer

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.regnius.photoprism.R
import com.regnius.photoprism.core.transfer.TransferEvent
import com.regnius.photoprism.core.transfer.TransferKind

/**
 * 저장·공유의 **진행 표시와 결과 안내** (Phase 4.4~4.5).
 *
 * 앱 전체에 하나만 둔다(`AppNavigation` 의 최상단). 이유가 두 가지다:
 *
 * 1. 전송은 앱 스코프에서 돈다 — 그리드에서 저장을 시작하고 뷰어로 들어가도
 *    계속 받는다. 진행 표시가 특정 화면에 묶여 있으면 그때 사라져서, 받는
 *    중인지 끝난 건지 알 수 없다.
 * 2. **Share Sheet 가 두 번 뜨는 것을 막는다.** 결과 이벤트를 화면마다
 *    수집하면, 화면 전환 중 두 화면이 동시에 조합된 순간에 같은 이벤트를 둘이
 *    받아 선택창이 두 번 열린다.
 */
@Composable
fun TransferUi(
    modifier: Modifier = Modifier,
    viewModel: TransferViewModel = hiltViewModel(),
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val savedOne = stringResource(R.string.transfer_saved_one)
    val failedAll = stringResource(R.string.transfer_failed)
    val cancelled = stringResource(R.string.transfer_cancelled)
    val busy = stringResource(R.string.transfer_busy)
    val shareTitle = stringResource(R.string.action_share)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TransferEvent.Saved -> {
                    val message = when {
                        event.failed > 0 ->
                            context.getString(R.string.transfer_saved_partial, event.saved, event.failed)
                        event.saved == 1 -> savedOne
                        else -> context.resources.getQuantityString(
                            R.plurals.transfer_saved_many, event.saved, event.saved,
                        )
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }

                is TransferEvent.ShareReady -> {
                    // 여러 장이면 ACTION_SEND_MULTIPLE 이어야 한다 —
                    // ACTION_SEND 에 목록을 넣으면 받는 앱이 첫 장만 가져간다.
                    val intent = if (event.uris.size == 1) {
                        Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_STREAM, event.uris.single())
                        }
                    } else {
                        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(event.uris))
                        }
                    }.apply {
                        type = event.mimeType
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(Intent.createChooser(intent, shareTitle)) }
                        .onFailure { Toast.makeText(context, failedAll, Toast.LENGTH_SHORT).show() }
                }

                TransferEvent.Failed -> Toast.makeText(context, failedAll, Toast.LENGTH_SHORT).show()
                TransferEvent.Cancelled -> Toast.makeText(context, cancelled, Toast.LENGTH_SHORT).show()
                TransferEvent.Busy -> Toast.makeText(context, busy, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 진행 표시는 화면 **맨 아래**에 둔다. 위쪽은 선택 바가 쓰고 있고, 저장은
    // 보통 선택 모드에서 시작하므로 둘이 겹치면 개수가 가려진다.
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible = progress != null) {
            val current = progress
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when {
                                current == null -> ""
                                current.total > 1 -> stringResource(
                                    if (current.kind == TransferKind.SAVE) {
                                        R.string.transfer_saving_many
                                    } else {
                                        R.string.transfer_sharing_many
                                    },
                                    current.done + 1,
                                    current.total,
                                )
                                else -> stringResource(
                                    if (current.kind == TransferKind.SAVE) {
                                        R.string.transfer_saving_one
                                    } else {
                                        R.string.transfer_sharing_one
                                    },
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::cancel) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                    // 파일 하나의 진행률을 서버가 길이를 줄 때만 쓴다. 모를 때는
                    // 막대를 흐르게 둔다 — 0% 에 멈춘 막대는 멈춘 것처럼 보인다.
                    val fraction = current?.let { p ->
                        if (p.total > 1) {
                            (p.done + (p.fileFraction ?: 0f)) / p.total
                        } else {
                            p.fileFraction
                        }
                    }
                    if (fraction != null) {
                        LinearProgressIndicator(
                            progress = { fraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
