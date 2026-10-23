package com.regnius.photoprism.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * GIF 의 움직이는 원본 고르기 (§22).
 *
 * 서버는 GIF 파일에 `Video: true` 를 주지 않고 정지 사이드카(`*.gif.jpg`)를
 * primary 로 준다 — 데모 서버 응답 그대로의 모양으로 확인한다.
 */
class PhotoAnimatedFileTest {

    private fun file(hash: String, name: String, mime: String, primary: Boolean) = MediaFile(
        uid = "f-$hash", hash = hash, name = name, mimeType = mime,
        width = 480, height = 360, sizeBytes = 1, isPrimary = primary, isVideo = false,
        durationNanos = 0, codec = null, videoProfile = null, audioCodec = null, fps = null, root = "/",
    )

    private fun photo(type: MediaType, files: List<MediaFile>) = Photo(
        uid = "p1", title = "Newtons Cradle", hash = "h", type = type, takenAtLocal = null,
        favorite = false, width = 480, height = 360, cameraMake = null, cameraModel = null,
        lens = null, iso = null, fNumber = null, exposure = null, latitude = null, longitude = null,
        files = files,
    )

    private val sidecar = file("jpg1", "GIF/Newtons-Cradle.gif.jpg", "image/jpeg", primary = true)
    private val gif = file("gif1", "GIF/Newtons-Cradle.gif", "image/gif", primary = false)

    @Test
    fun `picks the gif, not the primary still sidecar`() {
        assertEquals("gif1", photo(MediaType.ANIMATED, listOf(sidecar, gif)).animatedFile?.hash)
    }

    @Test
    fun `falls back to the extension when the server sent no mime`() {
        val noMime = file("gif1", "GIF/Newtons-Cradle.GIF", "", primary = false).copy(mimeType = null)

        assertEquals("gif1", photo(MediaType.ANIMATED, listOf(sidecar, noMime)).animatedFile?.hash)
    }

    @Test
    fun `null when the list response dropped the gif file`() {
        assertNull(photo(MediaType.ANIMATED, listOf(sidecar)).animatedFile)
    }

    @Test
    fun `null for anything that is not animated`() {
        assertNull(photo(MediaType.IMAGE, listOf(sidecar, gif)).animatedFile)
    }
}
