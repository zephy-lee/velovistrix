package com.regnius.photoprism.core.network

import com.regnius.photoprism.core.network.dto.AlbumDto
import com.regnius.photoprism.core.network.dto.ClientConfigDto
import com.regnius.photoprism.core.network.dto.PhotoDto
import com.regnius.photoprism.core.network.dto.SessionRequestDto
import com.regnius.photoprism.core.network.dto.SessionResponseDto
import com.regnius.photoprism.core.network.dto.StatusDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * PhotoPrism REST API — **읽기 전용**.
 *
 * 이 인터페이스에는 쓰기 엔드포인트를 의도적으로 하나도 선언하지 않는다.
 * 삭제/아카이브/수정 API 가 애초에 존재하지 않으면, 버그로라도 사용자의 원본을
 * 건드리는 일이 구조적으로 불가능해진다. §1 설계 원칙 1.
 */
interface PhotoPrismApi {

    /** 인증 없이도 호출 가능 — 서버 존재 확인과 버전 판별에 쓴다. */
    @GET("config")
    suspend fun getConfig(): ClientConfigDto

    /**
     * `{"status":"operational"}` 만 돌려주는 가벼운 엔드포인트.
     *
     * 진단용이다. `/config` 가 404 일 때 이게 200 이면 **PhotoPrism 은 거기
     * 있는데 config 만 막힌 것**이고, 이것도 404 면 그 주소에 PhotoPrism 이
     * 없는 것이다. 두 경우의 처방이 완전히 다르므로 구분해서 안내한다.
     */
    @GET("status")
    suspend fun getStatus(): StatusDto

    @POST("session")
    suspend fun createSession(@Body body: SessionRequestDto): SessionResponseDto

    @GET("albums")
    suspend fun getAlbums(
        @Query("count") count: Int,
        @Query("offset") offset: Int = 0,
        @Query("type") type: String = "album",
        @Query("order") order: String = "name",
        @Query("q") query: String? = null,
    ): List<AlbumDto>

    @GET("albums/{uid}")
    suspend fun getAlbum(@Path("uid") uid: String): AlbumDto

    /**
     * 사진 검색 — 앨범 상세, 전체 타임라인, 즐겨찾기, 검색이 전부 이걸 쓴다.
     *
     * @param scope  앨범 UID (`s` 파라미터). Phase 0.3 에서 실측 확인 대상.
     * @param query  `type:image`, `favorite:true` 등 PhotoPrism 필터 문법.
     * @param merged true 여야 RAW+JPEG 가 한 항목으로 합쳐진다. §4.3
     */
    @GET("photos")
    suspend fun getPhotos(
        @Query("count") count: Int,
        @Query("offset") offset: Int = 0,
        @Query("s") scope: String? = null,
        @Query("q") query: String? = null,
        @Query("order") order: String = "newest",
        @Query("merged") merged: Boolean = true,
    ): List<PhotoDto>

    /**
     * [getPhotos] 와 같은 엔드포인트지만 **응답 헤더까지** 받는다. 페이징 전용.
     *
     * `merged=true` 는 요청한 개수보다 **적게** 돌려준다 — RAW+JPEG 나 Live
     * Photo 처럼 여러 파일이 한 항목으로 합쳐지기 때문이다 (실측: count=120 →
     * 107건). 그래서 "받은 개수 < 요청 개수" 를 목록의 끝으로 판정하면 **첫
     * 페이지에서 곧바로 페이징이 멈춘다.**
     *
     * 서버는 `x-count`(실제로 스캔한 행 수)와 `x-limit`(요청한 개수)를 헤더로
     * 주므로, 끝 판정은 **`x-count < x-limit`** 으로 해야 정확하다.
     * 이 값은 병합 전 기준이라 `offset` 과 단위가 같다.
     *
     * 진단 화면은 4xx 를 예외로 받아 "서버가 이 문법을 거부했다" 를 판별하므로
     * [getPhotos] 를 그대로 쓴다. 두 메서드를 나눠 둔 이유가 이것이다.
     */
    @GET("photos")
    suspend fun searchPhotosPaged(
        @Query("count") count: Int,
        @Query("offset") offset: Int = 0,
        @Query("s") scope: String? = null,
        @Query("q") query: String? = null,
        @Query("order") order: String = "newest",
        @Query("merged") merged: Boolean = true,
    ): Response<List<PhotoDto>>

    @GET("photos/{uid}")
    suspend fun getPhoto(@Path("uid") uid: String): PhotoDto
}

/**
 * PhotoPrism 검색 필터 문법. Phase 0.3 에서 실서버(260827)로 검증했다.
 *
 * **부정 문법은 쓸 수 없다.** `type:!video` 와 `type:-video` 는 오류를 내지 않고
 * 조용히 **0건**을 돌려주고, `-type:video` / `!type:video` 는 거부된다.
 * 그래서 이미지 탭을 "video 가 아닌 것 전부"로 정의할 방법이 없고,
 * 타입을 하나씩 나열하는 화이트리스트가 유일한 수단이다.
 *
 * 화이트리스트의 대가: PhotoPrism 이 나중에 새 타입을 추가하면 이미지 탭에서
 * **조용히 사라진다.** 그래서 진단 화면이 "비-video 전체 개수 == 이미지 탭 개수"를
 * 검증한다. 그 단계가 실패하면 여기에 타입을 추가해야 한다는 신호다.
 */
object PhotoQuery {
    /**
     * 이미지 탭. §5.3
     *
     * vector(SVG)·document(PDF)가 포함된 이유: PhotoPrism 이 둘 다 래스터
     * 미리보기를 생성해두어 일반 썸네일처럼 표시되고, 빼두면 앨범 장수와
     * 탭에 보이는 장수가 어긋난다. (실측: 데모 서버 163건 중 4건이 이 둘)
     */
    const val IMAGES = "type:image|raw|live|animated|vector|document"

    const val VIDEOS = "type:video"
    const val FAVORITES = "favorite:true"

    /** OR 문법을 못 쓰는 서버를 만났을 때의 축소 폴백. */
    const val IMAGES_SIMPLE = "type:image"
}
