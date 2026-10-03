package com.regnius.photoprism.feature.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.regnius.photoprism.BuildConfig

/**
 * Phase 0 진단 화면.
 *
 * 이 화면은 최종 제품에 남지 않는다 — Phase 1 에서 실제 로그인/앨범 화면으로
 * 대체되고, 진단은 설정의 개발자 메뉴로 내려간다. 지금 이게 존재하는 이유는
 * Phase 0 의 완료 기준(§6)을 사람이 눈으로 확인할 수 있게 만들기 위해서다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("Phase 0 · API 실측", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "flavor=${BuildConfig.STORE_NAME} · 최소 서버 ${BuildConfig.MIN_SERVER_BUILD}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = state.serverUrl,
                    onValueChange = viewModel::onServerUrlChange,
                    label = { Text("서버 주소") },
                    placeholder = { Text("photos.example.com 또는 http://192.168.0.10:2342") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = state.username,
                    onValueChange = viewModel::onUsernameChange,
                    label = { Text("사용자명") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text(if (state.useAppPassword) "앱 패스워드" else "비밀번호") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = state.useAppPassword,
                        onClick = { viewModel.onUseAppPasswordChange(!state.useAppPassword) },
                        label = { Text("앱 패스워드 사용") },
                    )
                    Spacer(Modifier.fillMaxWidth(0.04f))
                    Text(
                        "Settings > Account 에서 발급",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Button(
                    onClick = viewModel::run,
                    enabled = !state.isRunning && state.serverUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.fillMaxWidth(0.03f))
                    }
                    Text(if (state.isRunning) "실측 중…" else "실측 실행")
                }
            }
            if (state.summary.isNotBlank()) {
                item {
                    Text(state.summary, style = MaterialTheme.typography.titleSmall)
                }
            }
            items(state.steps, key = { it.id }) { step -> ProbeStepCard(step) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ProbeStepCard(step: ProbeStep) {
    val accent = when (step.status) {
        ProbeStep.Status.Pass -> Color(0xFF66BB6A)
        ProbeStep.Status.Warn -> Color(0xFFFFA726)
        ProbeStep.Status.Fail -> Color(0xFFEF5350)
        ProbeStep.Status.Running -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (step.status) {
                        ProbeStep.Status.Pass -> "PASS"
                        ProbeStep.Status.Warn -> "WARN"
                        ProbeStep.Status.Fail -> "FAIL"
                        ProbeStep.Status.Running -> "····"
                        ProbeStep.Status.Skipped -> "SKIP"
                        ProbeStep.Status.Pending -> "····"
                    },
                    color = accent,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.fillMaxWidth(0.03f))
                Text(step.title, style = MaterialTheme.typography.titleSmall)
            }
            Text(
                step.purpose,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (step.detail.isNotBlank()) {
                Text(step.detail, style = MaterialTheme.typography.bodyMedium)
            }
            if (step.evidence.isNotBlank()) {
                Text(
                    step.evidence,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
