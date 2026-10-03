package com.regnius.photoprism.fake

import com.regnius.photoprism.core.auth.Credentials
import com.regnius.photoprism.core.auth.Session
import com.regnius.photoprism.core.auth.SessionSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeSessionSource(
    initial: Session? = null,
    private var credentials: Credentials? = null,
) : SessionSource {

    private val _session = MutableStateFlow(initial)
    override val session: StateFlow<Session?> = _session

    var clearCount = 0
        private set
    var updatedTokens = mutableListOf<String>()
        private set

    override fun storedCredentials(): Credentials? = credentials

    override fun updateAccessToken(token: String) {
        updatedTokens += token
        _session.value = _session.value?.copy(accessToken = token)
    }

    override fun clear() {
        clearCount++
        _session.value = null
    }

    companion object {
        fun session(token: String = "token-1") = Session(
            serverUrl = "https://example.com/",
            username = "demo",
            accessToken = token,
            previewToken = "preview-1",
            downloadToken = "download-1",
            serverVersion = "260601-abc",
        )
    }
}
