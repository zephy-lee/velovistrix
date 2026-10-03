package com.regnius.photoprism.core.network.url

import com.regnius.photoprism.core.model.ThumbSize
import okhttp3.HttpUrl

/**
 * 썸네일 / 비디오 스트림 URL 을 만든다. §4.2
 *
 * 핵심: 이 URL 에는 previewToken 이 이미 들어있으므로 **인증 헤더가 필요 없다.**
 * 덕분에 Coil 의 이미지 요청이 완전히 무상태 GET 이 되고, 디스크 캐시가 잘 듣는다.
 *
 * 단 previewToken 은 서버에서 회전될 수 있다. 그때 URL 이 통째로 바뀌어 캐시가
 * 전부 무효화되는 것을 막으려고, Coil 에는 [diskCacheKey] 를 따로 지정한다.
 */
class ThumbUrlFactory(
    private val apiBase: HttpUrl,
    private val previewToken: String,
) {
    fun thumb(fileHash: String, size: ThumbSize): String =
        apiBase.newBuilder()
            .addPathSegment("t")
            .addPathSegment(fileHash)
            .addPathSegment(previewToken)
            .addPathSegment(size.apiValue)
            .build()
            .toString()

    fun albumCover(albumUid: String, size: ThumbSize = ThumbSize.ALBUM_COVER): String =
        apiBase.newBuilder()
            .addPathSegment("albums")
            .addPathSegment(albumUid)
            .addPathSegment("t")
            .addPathSegment(previewToken)
            .addPathSegment(size.apiValue)
            .build()
            .toString()

    /** 트랜스코딩은 AVC 만 지원된다. */
    fun video(fileHash: String, format: String = "avc"): String =
        apiBase.newBuilder()
            .addPathSegment("videos")
            .addPathSegment(fileHash)
            .addPathSegment(previewToken)
            .addPathSegment(format)
            .build()
            .toString()

    companion object {
        /**
         * 토큰과 무관한 캐시 키. 썸네일 내용은 파일 해시에 종속이라 절대 안 바뀐다.
         * 토큰이 회전돼도 이 키 덕에 디스크 캐시가 살아남는다.
         */
        fun diskCacheKey(fileHash: String, size: ThumbSize): String = "${fileHash}_${size.apiValue}"

        /**
         * 썸네일 URL 을 되읽는다 — `.../t/{hash}/{token}/{size}`.
         *
         * 이미 만들어진 요청에서 "같은 사진의 다른 크기" 를 만들어내야 할 때
         * 쓴다(§17.36). 썸네일(`t`) 경로가 아니거나 크기 이름을 모르면 null.
         */
        fun parseThumb(url: String): Pair<String, ThumbSize>? {
            val segments = url.substringBefore('?').split('/').filter { it.isNotEmpty() }
            val i = segments.indexOf("t")
            if (i < 0 || i + 3 >= segments.size) return null
            val hash = segments[i + 1]
            val size = ThumbSize.entries.firstOrNull { it.apiValue == segments[i + 3] } ?: return null
            return hash to size
        }

        /** 같은 사진의 다른 크기 URL. */
        fun withSize(url: String, size: ThumbSize): String {
            val cut = url.lastIndexOf('/')
            return if (cut < 0) url else url.substring(0, cut + 1) + size.apiValue
        }

        fun albumCoverDiskCacheKey(albumUid: String, size: ThumbSize): String =
            "album_${albumUid}_${size.apiValue}"
    }
}
