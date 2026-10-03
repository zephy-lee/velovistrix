package com.regnius.photoprism.feature.login

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.regnius.photoprism.R

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LoginScreen(
    onAuthenticated: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    state.certificatePrompt?.let { prompt ->
        CertificateTrustDialog(
            prompt = prompt,
            onTrust = { viewModel.trustCertificate(prompt.fingerprint) },
            onDismiss = viewModel::dismissCertificatePrompt,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // 키보드가 뜰 때 OS 리사이즈(adjustResize)와 TextField의 기본
            // "포커스된 항목으로 스크롤" 동작이 서로 안 맞물려 화면이 출렁이던
            // 문제 — WindowInsetsAnimation(키보드 자체의 등장 애니메이션)에
            // 맞춰 콘텐츠를 같은 타이밍으로 밀어 올린다.
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            stringResource_appName(),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            stringResource(R.string.unofficial_short),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.serverUrl,
            onValueChange = viewModel::onServerUrlChange,
            label = { Text(stringResource(R.string.login_server_address)) },
            placeholder = { Text("photos.example.com") },
            singleLine = true,
            enabled = !state.busy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { viewModel.probeServer() }),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.stage == LoginStage.EnterServer) {
            Button(
                onClick = viewModel::probeServer,
                enabled = state.canSubmitServer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                BusyLabel(state.busy, stringResource(R.string.login_check_server))
            }
        }

        // 서버가 확인된 뒤에만 보여준다.
        if (state.serverName != null) {
            InfoRow(
                icon = Icons.Filled.Info,
                text = "${state.serverName} · " +
                    (state.serverVersion ?: stringResource(R.string.login_version_unknown)),
            )
        }
        if (state.cleartextWarning) {
            InfoRow(
                icon = Icons.Filled.Warning,
                tint = Color(0xFFFFA726),
                text = stringResource(R.string.login_plain_http_warning),
            )
        }
        state.versionWarning?.let {
            InfoRow(icon = Icons.Filled.Warning, tint = Color(0xFFFFA726), text = it)
        }

        when (state.stage) {
            LoginStage.PublicReady -> {
                // public 모드 — 입력할 자격증명이 없다.
                InfoRow(
                    icon = Icons.Filled.Info,
                    text = stringResource(R.string.login_public_mode),
                )
                Button(
                    onClick = { viewModel.connectPublic(onAuthenticated) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    BusyLabel(state.busy, stringResource(R.string.login_connect))
                }
            }

            LoginStage.EnterCredentials, LoginStage.Connecting -> {
                val passwordBringIntoViewRequester = remember { BringIntoViewRequester() }
                val focusManager = LocalFocusManager.current
                val scope = rememberCoroutineScope()
                var passwordFocused by remember { mutableStateOf(false) }
                // 포커스가 옮겨간 "시점"엔 아직 키보드가 안 올라와 있어 그 순간
                // 기준으로 스크롤하면 소용이 없다 — 키보드 높이(px) 자체를 계속
                // 관찰해서, 키보드가 올라오는 애니메이션 도중에도 여러 번 다시
                // 맞춘다.
                val imeBottomPx = WindowInsets.ime.getBottom(LocalDensity.current)
                LaunchedEffect(passwordFocused, imeBottomPx) {
                    if (passwordFocused && imeBottomPx > 0) {
                        passwordBringIntoViewRequester.bringIntoView()
                    }
                }

                OutlinedTextField(
                    value = state.username,
                    onValueChange = viewModel::onUsernameChange,
                    label = { Text(stringResource(R.string.login_username)) },
                    singleLine = true,
                    enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    // FocusRequester로 특정 필드를 직접 지정하는 대신 Compose 자체
                    // 포커스 순회(moveFocus)를 쓴다 — 새 InputConnection을 만드는
                    // FocusRequester.requestFocus() 와 달리, 같은 포커스 세션 안에서
                    // 다음 필드로 넘어가는 방식이라 IME 가 안 끊길 가능성이 높다
                    // (Next 이동 시 키보드가 잠깐 내려갔다 올라오는 문제의 알려진 완화책).
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                var passwordVisible by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text(stringResource(if (state.useAppPassword) R.string.login_app_password else R.string.login_password)) },
                    singleLine = true,
                    enabled = !state.busy,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(
                                    if (passwordVisible) R.string.login_hide_password else R.string.login_show_password,
                                ),
                            )
                        }
                    },
                    // keyboardType 을 Password 가 아니라 Text 로 둔다 — 마스킹(●●●)은
                    // keyboardType 이 아니라 위 visualTransformation 이 담당하므로
                    // 기능상 손해는 없다. 사용자명(Text)에서 이 필드로 Next 이동할 때
                    // 키보드가 잠깐 내려갔다 올라오던 문제가, 두 필드의 키보드 *타입*이
                    // 달라(Text -> Password) IME가 EditorInfo 변경으로 재시작하기
                    // 때문으로 보여서 — 타입을 통일해 재시작 자체를 없앤다.
                    // autoCorrectEnabled=false 로 자동완성/학습 예측만 그대로 끈다.
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { viewModel.login(onAuthenticated) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(passwordBringIntoViewRequester)
                        .onFocusEvent { focusState ->
                            passwordFocused = focusState.isFocused
                            if (focusState.isFocused) {
                                scope.launch { passwordBringIntoViewRequester.bringIntoView() }
                            }
                        },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = state.useAppPassword,
                        onClick = { viewModel.onUseAppPasswordChange(!state.useAppPassword) },
                        label = { Text(stringResource(R.string.login_app_password)) },
                        leadingIcon = { Icon(Icons.Filled.Lock, null, Modifier.size(16.dp)) },
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.login_app_password_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { viewModel.login(onAuthenticated) },
                    enabled = state.canSubmitCredentials,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    BusyLabel(state.busy, stringResource(R.string.login_sign_in))
                }
            }

            LoginStage.EnterServer -> Unit
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        // 시도한 URL 을 그대로 보여준다.
        //
        // 스킴(http/https)과 경로를 여러 조합으로 자동 시도하는 구조라, 실패했을 때
        // "무엇을 시도했는지" 를 감추면 사용자는 다음에 뭘 바꿔야 할지 알 수 없다.
        // 주소 오타인지, 포트가 다른지, 프록시가 /api 를 막는지가 이 목록에서 갈린다.
        if (state.attempts.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.login_attempted_addresses),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.attempts.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun stringResource_appName(): String =
    androidx.compose.ui.res.stringResource(R.string.app_name_full)

@Composable
private fun BusyLabel(busy: Boolean, label: String) {
    if (busy) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Spacer(Modifier.size(8.dp))
    }
    Text(label)
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = tint)
    }
}

/**
 * 셀프사인 인증서 승인 대화상자. §4.5
 *
 * 지문을 **그대로 보여주고** 사용자가 직접 대조하게 한다. "무시하고 계속"
 * 버튼만 두면 사용자는 아무것도 검증하지 않고 누르게 되고, 그건 trust-all 과
 * 실질적으로 같아진다.
 */
@Composable
private fun CertificateTrustDialog(
    prompt: CertificatePrompt,
    onTrust: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cert_untrusted_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.cert_untrusted_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.cert_untrusted_check),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    prompt.fingerprint,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                )
                Text(
                    stringResource(R.string.cert_subject, prompt.subject),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onTrust) { Text(stringResource(R.string.cert_trust)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
