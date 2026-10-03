package com.regnius.photoprism.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 세션과 자격증명을 기기에만 저장한다.
 *
 * Android Keystore 로 감싼 [EncryptedSharedPreferences] 를 쓴다. 평문
 * SharedPreferences 에 토큰을 두면 루팅된 기기나 백업 경로로 새어나갈 수 있다.
 * 백업은 매니페스트에서도 차단해 두었다 (`data_extraction_rules.xml`).
 *
 * 자격증명을 저장하는 이유는 하나뿐이다 — 토큰이 만료됐을 때
 * SessionAuthenticator 가 사용자를 다시 귀찮게 하지 않고 조용히 재로그인하기
 * 위해서다. 그 외 용도로는 읽지 않는다.
 *
 * NOTE: androidx.security-crypto(Jetpack Security)는 deprecated 상태다.
 * 다만 AndroidX 가 아직 동등한 대체 API 를 내놓지 않았고, 직접 Keystore 로
 * 구현하면 이 클래스가 다루는 것보다 훨씬 많은 실수 여지가 생긴다.
 * 대체재가 나오면 이 클래스 **한 곳만** 바꾸면 되도록 SessionSource 인터페이스
 * 뒤에 숨겨 두었다. 그때까지는 의도적으로 유지한다.
 */
@Suppress("DEPRECATION")
@Singleton
class SessionStore @Inject constructor(
    private val context: Context,
) : SessionSource {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _session = MutableStateFlow<Session?>(null)
    override val session: StateFlow<Session?> = _session.asStateFlow()

    /** 앱 시작 시 1회 호출. 저장된 세션이 있으면 메모리로 올린다. */
    fun load(): Session? {
        val stored = readSession()
        _session.value = stored
        return stored
    }

    fun save(session: Session, credentials: Credentials?) {
        prefs.edit().apply {
            putString(KEY_SERVER_URL, session.serverUrl)
            putString(KEY_USERNAME, session.username)
            putString(KEY_ACCESS_TOKEN, session.accessToken)
            putString(KEY_PREVIEW_TOKEN, session.previewToken)
            putString(KEY_DOWNLOAD_TOKEN, session.downloadToken)
            putString(KEY_SERVER_VERSION, session.serverVersion)
            putBoolean(KEY_IS_PUBLIC, session.isPublic)
            if (credentials != null) {
                putString(KEY_CRED_KIND, credentials.kind)
                putString(KEY_CRED_SECRET, credentials.secret)
            }
        }.apply()
        _session.value = session
    }

    /** 토큰만 교체한다 (재인증 성공 시). 자격증명은 건드리지 않는다. */
    override fun updateAccessToken(token: String) {
        val current = _session.value ?: return
        val updated = current.copy(accessToken = token)
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
        _session.value = updated
    }

    /** 재인증에 쓸 자격증명. 없으면 null → 사용자에게 재로그인을 요구한다. */
    override fun storedCredentials(): Credentials? {
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val secret = prefs.getString(KEY_CRED_SECRET, null) ?: return null
        return when (prefs.getString(KEY_CRED_KIND, null)) {
            "app_password" -> Credentials.AppPassword(username, secret)
            "password" -> Credentials.Password(username, secret)
            else -> null
        }
    }

    override fun clear() {
        prefs.edit().clear().apply()
        _session.value = null
    }

    private fun readSession(): Session? {
        val serverUrl = prefs.getString(KEY_SERVER_URL, null) ?: return null
        val isPublic = prefs.getBoolean(KEY_IS_PUBLIC, false)
        // public 모드에서는 accessToken 이 없는 것이 정상이므로 토큰 부재로
        // 세션을 버리면 안 된다.
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        if (token == null && !isPublic) return null
        return Session(
            serverUrl = serverUrl,
            username = prefs.getString(KEY_USERNAME, "").orEmpty(),
            accessToken = token.orEmpty(),
            previewToken = prefs.getString(KEY_PREVIEW_TOKEN, "").orEmpty(),
            downloadToken = prefs.getString(KEY_DOWNLOAD_TOKEN, "").orEmpty(),
            serverVersion = prefs.getString(KEY_SERVER_VERSION, null),
            isPublic = isPublic,
        )
    }

    private companion object {
        const val FILE_NAME = "photoprism_session"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_USERNAME = "username"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_PREVIEW_TOKEN = "preview_token"
        const val KEY_DOWNLOAD_TOKEN = "download_token"
        const val KEY_SERVER_VERSION = "server_version"
        const val KEY_IS_PUBLIC = "is_public"
        const val KEY_CRED_KIND = "cred_kind"
        const val KEY_CRED_SECRET = "cred_secret"
    }
}
