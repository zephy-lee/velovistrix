package com.regnius.photoprism.feature.diagnostics

/**
 * Phase 0.3 — 실서버 실측 결과 한 줄.
 *
 * 계획서 §4.3 의 엔드포인트 표에는 "Phase 0 에서 확인 필요" 로 표시된 항목이
 * 여럿 있다. PhotoPrism 은 릴리스마다 파라미터가 미묘하게 달라서 문서만 보고
 * 확정할 수 없기 때문이다. 이 진단 도구가 그 표를 실제 응답으로 메운다.
 */
data class ProbeStep(
    val id: String,
    val title: String,
    val purpose: String,
    val status: Status = Status.Pending,
    val detail: String = "",
    val evidence: String = "",
) {
    enum class Status { Pending, Running, Pass, Warn, Fail, Skipped }
}

/** 진단 실행 전체 상태. */
data class DiagnosticsState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val useAppPassword: Boolean = false,
    val isRunning: Boolean = false,
    val steps: List<ProbeStep> = emptyList(),
    val summary: String = "",
) {
    val passCount get() = steps.count { it.status == ProbeStep.Status.Pass }
    val failCount get() = steps.count { it.status == ProbeStep.Status.Fail }
    val warnCount get() = steps.count { it.status == ProbeStep.Status.Warn }
}
