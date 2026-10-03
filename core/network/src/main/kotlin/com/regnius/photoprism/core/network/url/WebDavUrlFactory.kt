package com.regnius.photoprism.core.network.url

import com.regnius.photoprism.core.model.MediaFile
import okhttp3.HttpUrl

/**
 * 동영상 트랜스코딩 스트리밍(`/api/v1/videos/...`)이 실패했을 때 폴백으로 쓰는
 * WebDAV URL. §17.1
 *
 * PhotoPrism 은 원본 폴더를 `{server}/originals/` 에 WebDAV 로 그대로 노출한다.
 * [MediaFile.name] 은 (root 가 `"/"` 인 일반 원본 파일이라면) 그 밑의 상대
 * 경로이므로, 트랜스코딩을 거치지 않고 원본 파일을 직접 재생할 수 있다 —
 * 트랜스코딩 자체가 실패 원인이라는 가설(§17.1)의 우회책이다.
 */
class WebDavUrlFactory(private val serverBase: HttpUrl) {

    /**
     * @return WebDAV로 원본을 받을 수 있는 URL. `root`가 표준 원본 위치
     *   (`"/"` 또는 비어 있음)가 아니거나 이름이 비어 있으면 null — 이런
     *   파일은 WebDAV 경로를 안전하게 추정할 수 없다.
     */
    fun original(file: MediaFile): String? {
        if (!file.root.isNullOrBlank() && file.root != "/") return null
        val segments = file.name.trim('/').split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return null
        return serverBase.newBuilder()
            .addPathSegment("originals")
            .apply { segments.forEach { addPathSegment(it) } }
            .build()
            .toString()
    }
}
