package com.regnius.photoprism.feature.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.regnius.photoprism.R
import com.regnius.photoprism.core.auth.AuthRepository
import com.regnius.photoprism.core.auth.Credentials
import com.regnius.photoprism.core.auth.LoginResult
import com.regnius.photoprism.core.auth.ProbeResult
import com.regnius.photoprism.core.model.ServerSupport
import com.regnius.photoprism.core.network.dto.ClientConfigDto
import com.regnius.photoprism.core.network.tls.CertificatePinStore
import com.regnius.photoprism.core.network.tls.UntrustedCertificateException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import javax.inject.Inject

/** 로그인은 두 단계다: ① 서버 확인(프로브) → ② 자격증명. */
enum class LoginStage { EnterServer, EnterCredentials, PublicReady, Connecting }

data class LoginUiState(
    val stage: LoginStage = LoginStage.EnterServer,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val useAppPassword: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val cleartextWarning: Boolean = false,
    val versionWarning: String? = null,
    val serverName: String? = null,
    val serverVersion: String? = null,
    /** 인증서를 신뢰할지 사용자에게 물어야 할 때 채워진다. */
    val certificatePrompt: CertificatePrompt? = null,
    /** 실패했을 때 시도한 URL 목록. 사용자가 원인을 짚을 수 있도록 그대로 보여준다. */
    val attempts: List<String> = emptyList(),
) {
    val canSubmitServer get() = serverUrl.isNotBlank() && !busy
    val canSubmitCredentials get() = username.isNotBlank() && password.isNotEmpty() && !busy
}

data class CertificatePrompt(
    val fingerprint: String,
    val subject: String,
    val issuer: String,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    // 오류 문구가 그대로 화면에 뜬다 — ViewModel 은 Compose 밖이라
    // stringResource 를 못 쓰므로 Context 로 번역을 꺼낸다(§11.8).
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val pinStore: CertificatePinStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var probedUrl: HttpUrl? = null
    private var probedConfig: ClientConfigDto? = null

    fun onServerUrlChange(v: String) = _state.update {
        it.copy(serverUrl = v, error = null, attempts = emptyList(), stage = LoginStage.EnterServer)
    }
    fun onUsernameChange(v: String) = _state.update { it.copy(username = v, error = null) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, error = null) }
    fun onUseAppPasswordChange(v: Boolean) = _state.update { it.copy(useAppPassword = v) }
    fun dismissCertificatePrompt() = _state.update { it.copy(certificatePrompt = null) }

    /** 사용자가 지문을 확인하고 승인했다. 그 인증서 하나만 신뢰 목록에 넣는다. */
    fun trustCertificate(fingerprint: String) {
        pinStore.pin(fingerprint)
        _state.update { it.copy(certificatePrompt = null) }
        probeServer()
    }

    /**
     * 자격증명을 받기 **전에** 서버를 확인한다 (§4.5).
     *
     * 오타 난 주소에 비밀번호까지 입력하고 나서 실패를 보는 것만큼 짜증나는
     * 흐름이 없다. 또 여기서 public 모드를 감지해야 자격증명 입력을 통째로
     * 건너뛸 수 있다.
     */
    fun probeServer() {
        val input = _state.value.serverUrl
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, attempts = emptyList()) }
            when (val result = authRepository.probe(input)) {
                is ProbeResult.InvalidUrl -> _state.update {
                    it.copy(busy = false, error = context.getString(R.string.login_error_bad_address))
                }

                is ProbeResult.Unreachable -> {
                    val cause = result.cause
                    if (cause is UntrustedCertificateException) {
                        _state.update {
                            it.copy(
                                busy = false,
                                certificatePrompt = CertificatePrompt(
                                    cause.fingerprint, cause.subject, cause.issuer,
                                ),
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                busy = false,
                                error = if (result.hintRes != 0) {
                                    context.getString(result.hintRes)
                                } else {
                                    context.getString(R.string.login_error_unreachable, cause.message.orEmpty())
                                },
                                attempts = result.attempts.map { a -> a.line(context) },
                            )
                        }
                    }
                }

                is ProbeResult.Reachable -> {
                    probedUrl = result.baseUrl
                    probedConfig = result.config

                    when (val support = result.support) {
                        is ServerSupport.TooOld -> {
                            _state.update {
                                it.copy(
                                    busy = false,
                                    error = context.getString(
                                        R.string.login_error_server_too_old,
                                        support.build.toIsoDate(),
                                        support.minimum.toIsoDate(),
                                    ),
                                )
                            }
                            return@launch
                        }
                        is ServerSupport.Unknown -> _state.update {
                            it.copy(
                                versionWarning = context.getString(
                                    R.string.login_error_version_unknown,
                                    support.reported
                                        ?: context.getString(R.string.login_error_version_unknown_none),
                                ),
                            )
                        }
                        is ServerSupport.Supported -> _state.update { it.copy(versionWarning = null) }
                    }

                    // public 모드면 입력할 자격증명이 없다.
                    val isPublic = result.config.public
                    _state.update {
                        it.copy(
                            busy = false,
                            stage = if (isPublic) LoginStage.PublicReady else LoginStage.EnterCredentials,
                            cleartextWarning = result.isCleartext,
                            serverName = result.config.name,
                            serverVersion = result.config.version,
                        )
                    }
                }
            }
        }
    }

    /** public 모드 서버에 자격증명 없이 접속한다. */
    fun connectPublic(onSuccess: () -> Unit) {
        val url = probedUrl ?: return
        val config = probedConfig ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, stage = LoginStage.Connecting) }
            handle(authRepository.connectPublic(url, config), onSuccess)
        }
    }

    fun login(onSuccess: () -> Unit) {
        val url = probedUrl ?: return
        val s = _state.value
        val credentials = if (s.useAppPassword) {
            Credentials.AppPassword(s.username.trim(), s.password)
        } else {
            Credentials.Password(s.username.trim(), s.password)
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            handle(authRepository.login(url, credentials), onSuccess)
        }
    }

    private fun handle(result: LoginResult, onSuccess: () -> Unit) {
        when (result) {
            is LoginResult.Success -> onSuccess()
            LoginResult.InvalidCredentials -> _state.update {
                it.copy(busy = false, stage = LoginStage.EnterCredentials, error = context.getString(R.string.login_error_bad_credentials))
            }
            is LoginResult.MissingPreviewToken -> _state.update {
                it.copy(
                    busy = false,
                    stage = LoginStage.EnterCredentials,
                    error = context.getString(R.string.login_error_no_preview_token),
                )
            }
            is LoginResult.Failed -> _state.update {
                it.copy(busy = false, stage = LoginStage.EnterCredentials, error = context.getString(R.string.login_error_generic, result.cause.message.orEmpty()))
            }
        }
    }
}
