package com.regnius.photoprism.core.data

import com.regnius.photoprism.core.model.MediaType
import com.regnius.photoprism.core.network.dto.AlbumDto
import com.regnius.photoprism.core.network.dto.FileDto
import com.regnius.photoprism.core.network.dto.PhotoDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {

    @Test
    fun `그리드 해시는 primary 이미지 파일에서 고른다`() {
        // RAW+JPEG 가 병합된 항목에서 RAW 해시를 고르면 썸네일이 안 나온다.
        val dto = PhotoDto(
            uid = "p1", hash = "photohash", type = "image",
            files = listOf(
                FileDto(uid = "f1", hash = "rawhash", primary = false),
                FileDto(uid = "f2", hash = "jpeghash", primary = true),
            ),
        )
        assertEquals("jpeghash", dto.toDomain().hash)
    }

    @Test
    fun `primary 가 없으면 비디오가 아닌 첫 파일로 폴백한다`() {
        val dto = PhotoDto(
            uid = "p1", hash = "photohash", type = "live",
            files = listOf(
                FileDto(uid = "f1", hash = "videohash", video = true),
                FileDto(uid = "f2", hash = "stillhash", video = false),
            ),
        )
        assertEquals("stillhash", dto.toDomain().hash)
    }

    @Test
    fun `파일이 없으면 photo 의 Hash 로 폴백한다`() {
        assertEquals("photohash", PhotoDto(uid = "p1", hash = "photohash").toDomain().hash)
    }

    @Test
    fun `Live Photo 는 재생용 비디오 해시를 노출한다`() {
        val dto = PhotoDto(
            uid = "p1", type = "live",
            files = listOf(
                FileDto(uid = "f1", hash = "still", primary = true),
                FileDto(uid = "f2", hash = "movie", video = true),
            ),
        )
        val photo = dto.toDomain()
        assertEquals(MediaType.LIVE, photo.type)
        assertEquals("movie", photo.videoHash)
    }

    @Test
    fun `위치가 0 0 이면 없는 것으로 취급한다`() {
        // PhotoPrism 은 위치가 없을 때 0,0 을 준다. 그대로 두면 기니 만
        // 앞바다에 찍힌 사진이 된다.
        val photo = PhotoDto(uid = "p1", lat = 0.0, lng = 0.0).toDomain()
        assertNull(photo.latitude)
        assertNull(photo.longitude)
    }

    @Test
    fun `실제 좌표는 보존한다`() {
        val photo = PhotoDto(uid = "p1", lat = 52.50665, lng = 13.33252).toDomain()
        assertEquals(52.50665, photo.latitude!!, 1e-6)
    }

    @Test
    fun `빈 EXIF 값은 null 로 정리한다`() {
        // 정보 시트가 빈 줄을 감출 수 있도록 여기서 정규화한다.
        val photo = PhotoDto(uid = "p1", cameraMake = "", iso = 0, fNumber = 0f).toDomain()
        assertNull(photo.cameraMake)
        assertNull(photo.iso)
        assertNull(photo.fNumber)
    }

    @Test
    fun `앨범 커버 해시를 Thumb 필드에서 가져온다`() {
        // 전용 커버 엔드포인트는 플레이스홀더 SVG 를 주므로 쓰지 않는다.
        val album = AlbumDto(uid = "a1", title = "Berlin", thumb = "coverhash").toDomain()
        assertEquals("coverhash", album.coverHash)
    }

    @Test
    fun `Thumb 가 비면 coverHash 는 null 이다`() {
        assertNull(AlbumDto(uid = "a1", thumb = "").toDomain().coverHash)
    }

    @Test
    fun `제목이 없는 앨범은 빈 제목 그대로 둔다`() {
        // 예전에는 여기서 "(제목 없음)" 을 채웠다. 다국어를 넣으면서 화면 쪽으로
        // 옮겼다 — 이 모듈은 순수 Kotlin/JVM 이라 안드로이드 리소스가 없고,
        // 사람이 읽을 대체 문구는 언어마다 달라야 하기 때문이다(§11.8).
        assertEquals("", AlbumDto(uid = "a1", title = "").toDomain().title)
    }
}
