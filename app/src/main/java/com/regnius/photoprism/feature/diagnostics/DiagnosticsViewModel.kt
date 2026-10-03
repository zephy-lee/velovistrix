package com.regnius.photoprism.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.regnius.photoprism.core.auth.Credentials
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val runner: ApiProbeRunner,
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    fun onServerUrlChange(value: String) = _state.update { it.copy(serverUrl = value) }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value) }
    fun onUseAppPasswordChange(value: Boolean) = _state.update { it.copy(useAppPassword = value) }

    fun run() {
        val s = _state.value
        if (s.isRunning) return

        val credentials = if (s.useAppPassword) {
            Credentials.AppPassword(s.username.trim(), s.password)
        } else {
            Credentials.Password(s.username.trim(), s.password)
        }

        viewModelScope.launch {
            _state.update { it.copy(isRunning = true, steps = emptyList(), summary = "") }
            runner.run(s.serverUrl, credentials) { step -> upsert(step) }
            _state.update { current ->
                current.copy(
                    isRunning = false,
                    summary = "통과 ${current.passCount} · 경고 ${current.warnCount} · 실패 ${current.failCount}",
                )
            }
        }
    }

    /** 같은 id 의 단계는 갱신하고, 없으면 추가한다 (Running → Pass 전이). */
    private fun upsert(step: ProbeStep) = _state.update { current ->
        val index = current.steps.indexOfFirst { it.id == step.id }
        val steps = if (index >= 0) {
            current.steps.toMutableList().apply { this[index] = step }
        } else {
            current.steps + step
        }
        current.copy(steps = steps)
    }

    /** 결과를 이슈에 붙여넣기 좋은 텍스트로. §4.3 표 갱신에 쓴다. */
    fun asReport(): String = buildString {
        appendLine("# PhotoPrism API 실측 결과 (Phase 0.3)")
        appendLine()
        _state.value.steps.forEach { step ->
            appendLine("## [${step.status}] ${step.title}")
            appendLine(step.purpose)
            if (step.detail.isNotBlank()) appendLine("→ ${step.detail}")
            if (step.evidence.isNotBlank()) {
                appendLine("```")
                appendLine(step.evidence)
                appendLine("```")
            }
            appendLine()
        }
    }
}
