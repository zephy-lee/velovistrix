package com.regnius.photoprism.core.transfer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.regnius.photoprism.core.di.ApplicationScope
import com.regnius.photoprism.core.model.Photo
import com.regnius.photoprism.core.network.PhotoPrismClientProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 지금 무엇을 하는 중인가 — 진행 표시의 내용이다. */
enum class TransferKind { SAVE, SHARE }

/**
 * @param done 끝낸 개수. @param total 전체 개수.
 * @param fileFraction 지금 받고 있는 파일의 진행률(0~1). 서버가 길이를 안 주면 null.
 */
data class TransferProgress(
    val kind: TransferKind,
    val done: Int,
    val total: Int,
    val fileFraction: Float? = null,
)

sealed interface TransferEvent {
    /** 갤러리 저장 끝. [failed] 가 0 이 아니면 일부만 됐다는 뜻이다. */
    data class Saved(val saved: Int, val failed: Int) : TransferEvent

    /**
     * 공유할 파일이 준비됐다 — **Share Sheet 는 UI 가 띄운다.**
     * `Intent` 를 띄우려면 화면이 필요하고, 이 클래스는 화면을 모른다.
     */
    data class ShareReady(val uris: List<Uri>, val mimeType: String) : TransferEvent

    /** 한 장도 못 받았다. */
    data object Failed : TransferEvent

    data object Cancelled : TransferEvent

    /** 이미 다른 전송이 돌고 있다 — 새 요청을 무시했다. */
    data object Busy : TransferEvent
}

/**
 * 원본 파일을 받아 **갤러리에 저장**하거나 **공유**한다 (Phase 4.4~4.5).
 *
 * 이 앱이 처음으로 기기에 쓰는 기능이다. 읽기 전용 원칙은 *서버* 에 쓰지 않는
 * 다는 뜻이므로(§1 설계 원칙 1) 로컬 저장은 그 선을 넘지 않는다 — 실제로 쓰는
 * 서버 호출은 `GET /api/v1/dl/{hash}` 하나뿐이다.
 *
 * **왜 Coil 이 아니라 OkHttp 직접인가.** 썸네일·뷰어 이미지는 Coil 이 받지만
 * 그건 `fit_1920` 같은 **변환된** 이미지다. 저장은 EXIF 가 살아 있는 원본이어야
 * 하고, 원본 엔드포인트는 다운로드 토큰을 쓰며 응답이 수십 MB 라 이미지 캐시에
 * 넣을 것도 아니다.
 *
 * **WorkManager 를 쓰지 않는다.** 사용자가 화면 앞에서 기다리는 작업이고
 * (§4.4 "진행 표시·취소"), 앱을 떠난 뒤에도 계속돼야 하는 종류가 아니다.
 * WorkManager 는 Phase 4.6 "명시적 오프라인 보관" 전용으로 남겨 둔다 — 거기는
 * 수백 장을 몇 분에 걸쳐 받는 작업이라 성격이 다르다.
 *
 * 전송은 **한 번에 하나**다. 둘을 동시에 돌리면 진행 표시가 둘 중 어느 것인지
 * 알 수 없고, 취소 버튼도 어느 쪽을 취소하는지 모호해진다.
 */
@Singleton
class MediaTransferManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val clientProvider: PhotoPrismClientProvider,
    @param:ApplicationScope private val appScope: CoroutineScope,
) {
    private val _progress = MutableStateFlow<TransferProgress?>(null)
    val progress: StateFlow<TransferProgress?> = _progress.asStateFlow()

    private val _events = MutableSharedFlow<TransferEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<TransferEvent> = _events.asSharedFlow()

    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    /** 갤러리(`Pictures/VeloVistrix`, `Movies/VeloVistrix`)에 원본을 저장한다. */
    fun saveToGallery(photos: List<Photo>) = start(TransferKind.SAVE, photos) { targets ->
        var saved = 0
        var failed = 0
        targets.forEachIndexed { index, target ->
            _progress.value = TransferProgress(TransferKind.SAVE, index, targets.size)
            val ok = runCatching { saveOne(target) { fraction ->
                _progress.value = TransferProgress(TransferKind.SAVE, index, targets.size, fraction)
            } }.getOrElse { e ->
                if (e is CancellationException) throw e
                false
            }
            if (ok) saved++ else failed++
        }
        if (saved == 0) TransferEvent.Failed else TransferEvent.Saved(saved, failed)
    }

    /**
     * 원본을 캐시에 받아 두고 그 URI 를 돌려준다 — Share Sheet 는 UI 가 띄운다.
     *
     * 캐시에 두는 이유: 공유는 **받는 앱이 읽을 수 있는 곳**이어야 하고, 갤러리에
     * 저장하는 것과는 다른 동작이다("공유했더니 갤러리에 사진이 늘어났다" 는
     * 기대 밖이다). `FileProvider` 로 그 디렉터리만 임시 권한으로 연다.
     */
    fun share(photos: List<Photo>) = start(TransferKind.SHARE, photos) { targets ->
        clearShareCache()
        val uris = mutableListOf<Uri>()
        val mimes = mutableListOf<String>()
        targets.forEachIndexed { index, target ->
            _progress.value = TransferProgress(TransferKind.SHARE, index, targets.size)
            val uri = runCatching { shareOne(target) { fraction ->
                _progress.value = TransferProgress(TransferKind.SHARE, index, targets.size, fraction)
            } }.getOrElse { e ->
                if (e is CancellationException) throw e
                null
            }
            if (uri != null) {
                uris += uri
                mimes += target.mimeType
            }
        }
        if (uris.isEmpty()) {
            TransferEvent.Failed
        } else {
            TransferEvent.ShareReady(uris, TransferTargets.shareMimeType(mimes))
        }
    }

    fun cancel() {
        job?.cancel()
    }

    private fun start(
        kind: TransferKind,
        photos: List<Photo>,
        block: suspend (List<Target>) -> TransferEvent,
    ) {
        if (isRunning) {
            _events.tryEmit(TransferEvent.Busy)
            return
        }
        val factory = clientProvider.downloadUrlFactory()
        val targets = photos.mapNotNull { photo ->
            val file = TransferTargets.fileFor(photo) ?: return@mapNotNull null
            val hash = file.hash.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Target(
                url = factory?.original(hash) ?: return@mapNotNull null,
                displayName = TransferTargets.displayName(photo, file),
                mimeType = TransferTargets.mimeType(photo, file),
                isVideo = TransferTargets.isVideo(photo, file),
            )
        }
        if (targets.isEmpty()) {
            _events.tryEmit(TransferEvent.Failed)
            return
        }

        // appScope 다 — 화면을 떠나도(뷰어를 닫거나 탭을 옮겨도) 받던 것이
        // 끊기지 않는다. 취소는 사용자가 명시적으로 눌렀을 때만 일어난다.
        job = appScope.launch {
            _progress.value = TransferProgress(kind, 0, targets.size)
            try {
                val event = block(targets)
                _events.emit(event)
            } catch (_: CancellationException) {
                _events.emit(TransferEvent.Cancelled)
            } finally {
                _progress.value = null
            }
        }
    }

    /**
     * 받아서 `MediaStore` 에 넣는다.
     *
     * **권한이 필요 없다.** API 29+ 에서 자기가 만든 항목은
     * `WRITE_EXTERNAL_STORAGE` 없이 쓸 수 있고, 이 앱의 minSdk 는 31 이다.
     *
     * `IS_PENDING` 으로 감싸는 것이 핵심이다. 받는 중인 반쪽 파일이 갤러리에
     * 보이지 않게 하고, **취소·실패 시 그 행을 지운다** — 안 지우면 0바이트
     * 항목이 갤러리에 영구히 남는다.
     */
    private suspend fun saveOne(target: Target, onProgress: (Float?) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            val collection = if (target.isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val relativePath = if (target.isVideo) {
                "${Environment.DIRECTORY_MOVIES}/$ALBUM_DIRECTORY"
            } else {
                "${Environment.DIRECTORY_PICTURES}/$ALBUM_DIRECTORY"
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, target.displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, target.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val item = resolver.insert(collection, values) ?: return@withContext false
            var ok = false
            try {
                resolver.openOutputStream(item)?.use { output ->
                    download(target.url, onProgress) { buffer, length -> output.write(buffer, 0, length) }
                } ?: return@withContext false
                ok = true
            } finally {
                if (ok) {
                    resolver.update(
                        item,
                        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                        null,
                        null,
                    )
                } else {
                    // 취소든 실패든 반쪽 항목을 남기지 않는다.
                    runCatching { resolver.delete(item, null, null) }
                }
            }
            ok
        }

    private suspend fun shareOne(target: Target, onProgress: (Float?) -> Unit): Uri? =
        withContext(Dispatchers.IO) {
            val dir = shareDir()
            val file = File(dir, target.displayName)
            var ok = false
            try {
                file.outputStream().use { output ->
                    download(target.url, onProgress) { buffer, length -> output.write(buffer, 0, length) }
                }
                ok = true
            } finally {
                if (!ok) runCatching { file.delete() }
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }

    private inline fun download(
        url: String,
        onProgress: (Float?) -> Unit,
        write: (ByteArray, Int) -> Unit,
    ) {
        val response = okHttpClient.newCall(Request.Builder().url(url).build()).execute()
        response.use {
            if (!it.isSuccessful) throw IllegalStateException("HTTP ${it.code}")
            val body = it.body
            val total = body.contentLength().takeIf { len -> len > 0 }
            var received = 0L
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            body.byteStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    write(buffer, read)
                    received += read
                    onProgress(total?.let { len -> received.toFloat() / len })
                }
            }
        }
    }

    private fun shareDir(): File =
        File(context.cacheDir, SHARE_DIRECTORY).apply { mkdirs() }

    /**
     * 지난번에 공유한 파일을 지운다.
     *
     * 캐시라 OS 가 언제든 비울 수 있지만, 그때까지 원본 수십 MB 가 쌓여 있는
     * 것은 설정 화면의 "캐시 용량" 숫자와도 어긋난다. 새 공유를 시작할 때
     * 치우는 것이 가장 예측 가능한 시점이다 — 공유가 끝난 직후에 지우면
     * 받는 앱이 아직 URI 를 읽고 있을 수 있다.
     */
    private fun clearShareCache() {
        runCatching { shareDir().listFiles()?.forEach { it.delete() } }
    }

    private data class Target(
        val url: String,
        val displayName: String,
        val mimeType: String,
        val isVideo: Boolean,
    )

    private companion object {
        /** 갤러리에 만들 폴더 이름. 앱 이름 그대로 — 사용자가 찾기 쉬워야 한다. */
        const val ALBUM_DIRECTORY = "VeloVistrix"
        const val SHARE_DIRECTORY = "share"
    }
}
