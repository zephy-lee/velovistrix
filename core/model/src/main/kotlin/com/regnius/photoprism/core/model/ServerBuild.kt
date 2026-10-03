package com.regnius.photoprism.core.model

/**
 * PhotoPrism 의 날짜 기반 빌드 식별자.
 *
 * 서버는 `240915-e1a2b3c4d` 또는 `260601-a7d098548` 같은 문자열을 보고한다.
 * 앞 6자리가 `YYMMDD` 이고 뒤는 커밋 해시다.
 *
 * §4.1 — 이 앱은 [MINIMUM] 이상만 지원한다. 그 아래는 인증 방식이 달라
 * (`X-Auth-Token`) Bearer 단일 구현으로는 동작하지 않으므로, 폴백을 넣는 대신
 * 로그인 단계에서 명확히 차단한다.
 */
@JvmInline
value class ServerBuild(val yymmdd: Int) : Comparable<ServerBuild> {

    override fun compareTo(other: ServerBuild): Int = yymmdd.compareTo(other.yymmdd)

    /** `260601` -> `2026-06-01` */
    fun toIsoDate(): String {
        val yy = yymmdd / 10_000
        val mm = (yymmdd / 100) % 100
        val dd = yymmdd % 100
        return "20%02d-%02d-%02d".format(yy, mm, dd)
    }

    override fun toString(): String = yymmdd.toString()

    companion object {
        /** 지원 하한 — 2026-06-01 릴리스. */
        val MINIMUM = ServerBuild(260_601)

        private val LEADING_DIGITS = Regex("""^(\d{6})(?:[-.].*)?$""")

        /**
         * 서버가 보고한 버전 문자열에서 빌드 날짜를 뽑는다.
         *
         * PhotoPrism 은 개발 빌드에서 `develop`, `latest` 같은 값을 주기도 한다.
         * 그 경우 날짜를 알 수 없으므로 null 을 돌려주고, 호출부는 차단이 아니라
         * "확인 불가"로 처리해 사용자가 진행할 수 있게 한다. 최신 개발 빌드를
         * 쓰는 사용자를 막아버리는 편이 오탐 비용이 더 크기 때문이다.
         */
        fun parseOrNull(raw: String?): ServerBuild? {
            val v = raw?.trim().orEmpty()
            if (v.isEmpty()) return null
            val digits = LEADING_DIGITS.find(v)?.groupValues?.get(1) ?: return null
            val n = digits.toIntOrNull() ?: return null
            val mm = (n / 100) % 100
            val dd = n % 100
            // YYMMDD 형태가 아닌 6자리 숫자(예: 빌드번호)를 날짜로 오해하지 않도록 검증한다.
            if (mm !in 1..12 || dd !in 1..31) return null
            return ServerBuild(n)
        }
    }
}

/** 서버 버전 확인 결과. */
sealed interface ServerSupport {
    /** 지원 범위. */
    data class Supported(val build: ServerBuild) : ServerSupport

    /** 하한 미만 — 로그인을 진행하지 않는다. */
    data class TooOld(val build: ServerBuild, val minimum: ServerBuild = ServerBuild.MINIMUM) : ServerSupport

    /** `develop` 등 날짜를 못 읽은 경우 — 경고만 하고 진행을 허용한다. */
    data class Unknown(val reported: String?) : ServerSupport

    companion object {
        fun of(reportedVersion: String?): ServerSupport {
            val build = ServerBuild.parseOrNull(reportedVersion)
                ?: return Unknown(reportedVersion)
            return if (build >= ServerBuild.MINIMUM) Supported(build) else TooOld(build)
        }
    }
}
