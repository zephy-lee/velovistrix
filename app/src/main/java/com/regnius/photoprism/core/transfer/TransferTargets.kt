package com.regnius.photoprism.core.transfer

import com.regnius.photoprism.core.model.MediaFile
import com.regnius.photoprism.core.model.MediaType
import com.regnius.photoprism.core.model.Photo

/**
 * "이 사진을 저장/공유하면 **무슨 파일**이 되는가" — 순수 함수만 모았다
 * (Phase 4.4~4.5).
 *
 * 안드로이드 API 를 안 쓰므로 에뮬레이터 없이 검증된다. 여기가 틀리면 조용히
 * 나쁜 일이 생긴다 — 확장자가 어긋난 파일이 갤러리에 들어가면 다른 앱이
 * 열지 못하고, 그걸 알려주는 오류도 없다.
 */
object TransferTargets {

    /**
     * 받아올 파일.
     *
     * 동영상이면 동영상 파일을, 아니면 primary 파일을 고른다. **primary 가
     * 중요하다** — RAW+JPEG 가 한 항목으로 합쳐진 경우(`merged=true`, §4.3)
     * 파일이 여럿인데, 사람이 "이 사진" 이라고 할 때 기대하는 것은 JPEG 쪽이다.
     * RAW 를 내주면 갤러리 앱이 못 여는 수십 MB 파일이 들어간다.
     *
     * **GIF 은 예외다** (§22.4) — `animated` 항목의 primary 는 정지
     * 사이드카(`*.gif.jpg`)라, 위 규칙대로 받으면 갤러리에 **안 움직이는
     * JPEG** 가 들어간다. 움직이는 원본을 받아야 한다.
     */
    fun fileFor(photo: Photo): MediaFile? = when (photo.type) {
        MediaType.VIDEO -> photo.videoFile ?: photo.files.firstOrNull()
        MediaType.ANIMATED -> photo.animatedFile ?: primaryStill(photo)
        else -> primaryStill(photo)
    }

    private fun primaryStill(photo: Photo): MediaFile? =
        photo.files.firstOrNull { it.isPrimary && !it.isVideo }
            ?: photo.files.firstOrNull { !it.isVideo }
            ?: photo.files.firstOrNull()

    /**
     * 저장될 파일 이름.
     *
     * 서버의 [MediaFile.name] 은 원본 폴더 기준 **상대 경로**라
     * (`2026/09/IMG_1234.JPG`) 그대로 쓰면 갤러리에 폴더가 생긴다. 마지막
     * 조각만 쓴다.
     *
     * 이름이 없거나 확장자가 없으면 UID 와 MIME 으로 만든다 — 이름 없는 파일을
     * `MediaStore` 에 넣으면 삽입 자체가 거부된다.
     */
    fun displayName(photo: Photo, file: MediaFile): String {
        val base = file.name.trimEnd('/').substringAfterLast('/').trim()
        if (base.isNotBlank() && base.contains('.') && !base.endsWith('.')) return base
        val stem = base.removeSuffix(".").ifBlank { photo.uid }
        return "$stem.${extensionFor(mimeType(photo, file))}"
    }

    /**
     * MIME 타입. 서버가 안 주면 확장자로, 그것도 모르면 종류별 기본값으로.
     *
     * `MediaStore` 는 MIME 과 확장자가 어긋나면 받아주긴 하지만, 그 뒤 갤러리
     * 앱이 썸네일을 못 만든다. 그래서 **둘을 같은 함수에서 결정**한다.
     */
    fun mimeType(photo: Photo, file: MediaFile): String {
        file.mimeType?.takeIf { it.isNotBlank() && it.contains('/') }?.let { return it }
        val ext = file.name.substringAfterLast('.', "").lowercase()
        return MIME_BY_EXTENSION[ext] ?: if (isVideo(photo, file)) "video/mp4" else "image/jpeg"
    }

    /** 갤러리의 어느 컬렉션에 넣을지 — 사진과 동영상이 다른 테이블이다. */
    fun isVideo(photo: Photo, file: MediaFile): Boolean =
        file.isVideo || photo.type == MediaType.VIDEO || mimeOfName(file).startsWith("video/")

    /**
     * 여러 장을 한 번에 공유할 때 Share Sheet 에 넘길 타입.
     *
     * 전부 같은 종류면 그 타입을, 섞여 있으면 공통 상위를 쓴다. 전체
     * 와일드카드까지 떨어지면 받는 앱 목록이 넓어지는 대신 정확도가 떨어지므로,
     * 사진끼리는 image, 동영상끼리는 video 로 좁혀 둔다.
     */
    fun shareMimeType(mimeTypes: List<String>): String {
        val distinct = mimeTypes.filter { it.contains('/') }.distinct()
        if (distinct.isEmpty()) return "*/*"
        if (distinct.size == 1) return distinct.single()
        val families = distinct.map { it.substringBefore('/') }.distinct()
        return if (families.size == 1) "${families.single()}/*" else "*/*"
    }

    private fun mimeOfName(file: MediaFile): String =
        MIME_BY_EXTENSION[file.name.substringAfterLast('.', "").lowercase()].orEmpty()

    private fun extensionFor(mime: String): String =
        MIME_BY_EXTENSION.entries.firstOrNull { it.value == mime }?.key
            ?: if (mime.startsWith("video/")) "mp4" else "jpg"

    /**
     * 확장자 → MIME. `MimeTypeMap`(안드로이드)을 쓰지 않는 이유는 이 파일을
     * 순수 JVM 으로 유지해 테스트하기 위해서다. PhotoPrism 이 다루는 범위만
     * 담으면 충분하다.
     */
    private val MIME_BY_EXTENSION = mapOf(
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "webp" to "image/webp",
        "gif" to "image/gif",
        "heic" to "image/heic",
        "heif" to "image/heif",
        "avif" to "image/avif",
        "tif" to "image/tiff",
        "tiff" to "image/tiff",
        "dng" to "image/x-adobe-dng",
        "mp4" to "video/mp4",
        "mov" to "video/quicktime",
        "m4v" to "video/x-m4v",
        "mkv" to "video/x-matroska",
        "webm" to "video/webm",
        "avi" to "video/x-msvideo",
        "3gp" to "video/3gpp",
    )
}
