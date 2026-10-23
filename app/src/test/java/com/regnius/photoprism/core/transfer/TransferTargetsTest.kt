package com.regnius.photoprism.core.transfer

import com.regnius.photoprism.core.model.MediaFile
import com.regnius.photoprism.core.model.MediaType
import com.regnius.photoprism.core.model.Photo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 저장·공유 대상 결정 (Phase 4.4~4.5).
 *
 * 여기가 틀리면 조용히 나쁜 일이 생긴다 — 확장자가 어긋난 파일이 갤러리에
 * 들어가면 다른 앱이 못 열고, 그걸 알려주는 오류는 없다. RAW 를 내주는 실수도
 * 같은 부류다(수십 MB 짜리가 들어가는데 아무 경고가 없다).
 */
class TransferTargetsTest {

    private fun file(
        name: String,
        mime: String? = null,
        primary: Boolean = true,
        video: Boolean = false,
        hash: String = "h1",
    ) = MediaFile(
        uid = "f-$name", hash = hash, name = name, mimeType = mime,
        width = 100, height = 100, sizeBytes = 1, isPrimary = primary,
        isVideo = video, durationNanos = 0, codec = null, videoProfile = null,
        audioCodec = null, fps = null, root = "/",
    )

    private fun photo(
        type: MediaType = MediaType.IMAGE,
        files: List<MediaFile>,
        uid: String = "pabc123",
    ) = Photo(
        uid = uid, title = "t", hash = "h", type = type, takenAtLocal = null,
        favorite = false, width = 100, height = 100, cameraMake = null,
        cameraModel = null, lens = null, iso = null, fNumber = null,
        exposure = null, latitude = null, longitude = null, files = files,
    )

    @Test
    fun `RAW 와 JPEG 가 합쳐진 항목에서는 JPEG 를 받는다`() {
        // merged=true 로 한 항목이 된 경우(§4.3) 파일이 여럿이다. RAW 를
        // 내주면 갤러리 앱이 못 여는 수십 MB 파일이 들어간다.
        val target = TransferTargets.fileFor(
            photo(files = listOf(
                file("IMG_1.DNG", "image/x-adobe-dng", primary = false),
                file("IMG_1.JPG", "image/jpeg", primary = true),
            )),
        )

        assertEquals("IMG_1.JPG", target?.name)
    }

    @Test
    fun `동영상 항목은 동영상 파일을 받는다`() {
        val target = TransferTargets.fileFor(
            photo(type = MediaType.VIDEO, files = listOf(
                file("clip.JPG", "image/jpeg", primary = true),
                file("clip.MP4", "video/mp4", primary = false, video = true),
            )),
        )

        assertEquals("clip.MP4", target?.name)
    }

    @Test
    fun `GIF 은 정지 사이드카가 아니라 움직이는 원본을 받는다`() {
        // animated 항목의 primary 는 *.gif.jpg 다 (§22.4). 그걸 받으면
        // 갤러리에 안 움직이는 JPEG 가 들어가는데, 아무 오류도 안 난다.
        val gifPhoto = photo(type = MediaType.ANIMATED, files = listOf(
            file("GIF/Cradle.gif.jpg", "image/jpeg", primary = true),
            file("GIF/Cradle.gif", "image/gif", primary = false, hash = "gif1"),
        ))

        val target = TransferTargets.fileFor(gifPhoto)

        assertEquals("GIF/Cradle.gif", target?.name)
        assertEquals("Cradle.gif", TransferTargets.displayName(gifPhoto, target!!))
        assertEquals("image/gif", TransferTargets.mimeType(gifPhoto, target))
        // 갤러리의 사진 컬렉션에 들어가야 한다 — MP4 로 변환해 받는 것이 아니다.
        assertFalse(TransferTargets.isVideo(gifPhoto, target))
    }

    @Test
    fun `GIF 파일이 응답에 없으면 정지 사진이라도 받는다`() {
        // 목록 응답이 GIF 파일을 빠뜨리는 경우가 있다 (§17.8). 그때 아무것도
        // 안 주면 저장 버튼이 조용히 실패한다.
        val target = TransferTargets.fileFor(
            photo(type = MediaType.ANIMATED, files = listOf(
                file("GIF/Cradle.gif.jpg", "image/jpeg", primary = true),
            )),
        )

        assertEquals("GIF/Cradle.gif.jpg", target?.name)
    }

    @Test
    fun `이름은 경로가 아니라 파일 이름만 쓴다`() {
        // 서버의 Name 은 원본 폴더 기준 상대 경로다. 그대로 쓰면 갤러리에
        // 폴더가 생긴다.
        val f = file("2026/09/IMG_1234.JPG", "image/jpeg")

        assertEquals("IMG_1234.JPG", TransferTargets.displayName(photo(files = listOf(f)), f))
    }

    @Test
    fun `확장자가 없으면 MIME 으로 붙인다`() {
        // 이름 없는 파일은 MediaStore 삽입 자체가 거부된다.
        val f = file("scan", "image/png")
        assertEquals("scan.png", TransferTargets.displayName(photo(files = listOf(f)), f))

        val blank = file("", "video/mp4")
        assertEquals("pabc123.mp4", TransferTargets.displayName(photo(files = listOf(blank)), blank))
    }

    @Test
    fun `서버가 MIME 을 안 주면 확장자로 알아낸다`() {
        val f = file("IMG_1.HEIC", mime = null)
        assertEquals("image/heic", TransferTargets.mimeType(photo(files = listOf(f)), f))

        val unknown = file("weird.xyz", mime = null)
        assertEquals("image/jpeg", TransferTargets.mimeType(photo(files = listOf(unknown)), unknown))
    }

    @Test
    fun `동영상 판정은 플래그와 타입과 확장자를 모두 본다`() {
        val flagged = file("a.mp4", video = true)
        assertTrue(TransferTargets.isVideo(photo(files = listOf(flagged)), flagged))

        // 플래그가 없는 동영상이 실제로 온다(§Media.videoFile 주석) — 그때도
        // 사진 컬렉션에 넣으면 갤러리에서 재생이 안 된다.
        val byName = file("b.mov", mime = null, video = false)
        assertTrue(TransferTargets.isVideo(photo(files = listOf(byName)), byName))

        val image = file("c.jpg", "image/jpeg")
        assertFalse(TransferTargets.isVideo(photo(files = listOf(image)), image))
    }

    @Test
    fun `여러 장 공유의 타입은 공통 상위로 좁힌다`() {
        assertEquals("image/jpeg", TransferTargets.shareMimeType(listOf("image/jpeg", "image/jpeg")))
        assertEquals("image/*", TransferTargets.shareMimeType(listOf("image/jpeg", "image/png")))
        assertEquals("*/*", TransferTargets.shareMimeType(listOf("image/jpeg", "video/mp4")))
        assertEquals("*/*", TransferTargets.shareMimeType(emptyList()))
    }
}
