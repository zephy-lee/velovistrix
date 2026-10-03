package com.regnius.photoprism.feature.viewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlinx.coroutines.launch

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f
private const val DOUBLE_TAP_SCALE = 2.5f

/** 스케일이 이 값 아래로 남으면 손을 뗐을 때 1.0 으로 붙여준다 (핀치 미세 잔량 정리). */
private const val SETTLE_SCALE_THRESHOLD = 1.05f

/** 이 거리 이상 위로 끌면 정보 시트를 연다. */
private const val SWIPE_UP_THRESHOLD_PX = 140f

/** 뷰어 한 페이지의 제스처 상태. 부모(Pager)가 읽어야 해서 밖으로 뺀다. */
class ZoomState {
    var scale by mutableFloatStateOf(MIN_SCALE)
        internal set
    var offset by mutableStateOf(Offset.Zero)
        internal set

    val isZoomed: Boolean get() = scale > MIN_SCALE + 0.01f

    internal fun reset() {
        scale = MIN_SCALE
        offset = Offset.Zero
    }
}

/**
 * 핀치 줌 · 더블탭 줌 · 패닝 · 스와이프 다운 닫기 · 스와이프 업 정보 (§5.4).
 *
 * 이 화면의 유일한 난제는 **제스처 충돌**이다. 같은 손가락 움직임이 상황에 따라
 * 페이지 넘김 · 이미지 패닝 · 화면 닫기 · 정보 열기 중 무엇이든 될 수 있다.
 * 우선순위를 코드가 아니라 표로 먼저 정해두고 그대로 구현한다:
 *
 * | 상태 | 수평 드래그 | 수직 드래그 |
 * |---|---|---|
 * | 줌 = 1.0 | **Pager 로 넘긴다** (여기서 소비 안 함) | 아래로 → 닫기 · 위로 → 정보 |
 * | 줌 > 1.0 | 이미지 패닝 | 이미지 패닝 |
 * | 줌 > 1.0 & 좌우 끝 도달 | **Pager 로 넘긴다** | 패닝 |
 *
 * 표의 "Pager 로 넘긴다" 를 구현하려면 해당 이벤트를 **소비하지 않아야** 한다.
 * 그래서 표준 `detectTransformGestures` 를 쓰지 않는다 — 그건 모든 제스처를
 * 무조건 소비해서 Pager 가 영영 동작하지 않게 만든다.
 *
 * **[photoKey] 가 핵심이다.** 제스처를 처리하는 `pointerInput` 과, 사진이
 * 바뀔 때 줌을 초기화하는 `LaunchedEffect` 는 전부 이 키로 묶인다. 이전에는
 * Coil 의 [model]([coil3.request.ImageRequest])을 키로 썼는데, 이 객체는
 * 참조 동일성으로 비교되고 **호출부가 재구성될 때마다 새로 만들어진다.**
 * 그 결과 핀치 도중 `pagerEnabled` 가 바뀌어 상위가 재구성될 때마다 `model`
 * 이 "다른 객체" 로 인식되어 진행 중이던 `pointerInput` 코루틴이 취소·재시작
 * 됐다 — 핀치가 중간에 끊기고, 더블탭 직후 `LaunchedEffect` 가 다시 실행되며
 * `zoomState.reset()` 이 불려 방금 확대한 화면이 도로 원래대로 돌아가는
 * 깜빡임의 원인이었다. `photoKey` 는 문자열(UID)이라 내용이 같으면 항상
 * 같은 값으로 비교되므로, 사진이 실제로 바뀔 때만 재시작된다.
 *
 * 실제로 그릴 내용은 [content] 슬롯으로 받는다 — 원래는 `AsyncImage` 를
 * 직접 그렸지만, Live Photo(Phase 2.8)가 정지 프레임 위에 무음 반복 영상을
 * 겹쳐 그리면서도 **같은 핀치/패닝 변환**을 받아야 해서 내용 자체를
 * 호출부가 결정하게 뺐다. [content] 에 넘어오는 [Modifier] 에 이미 확대/이동
 * `graphicsLayer` 가 적용돼 있으므로, 호출부는 그걸 최상위 노드에 그대로
 * 붙이기만 하면 된다.
 */
@Composable
fun ZoomableImage(
    photoKey: Any,
    zoomState: ZoomState,
    onDismissDrag: (Float) -> Unit,
    onDismissRelease: (Float) -> Unit,
    onToggleChrome: () -> Unit,
    onSwipeUpInfo: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    // 더블탭 애니메이션의 진행률(0~1)만 담는다. scale/offset 은 이 진행률을
    // 시작값과 목표값 사이로 보간해 zoomState 에 직접 써 넣는다 — 그래야
    // graphicsLayer 가 실제로 읽는 값과 애니메이션이 같은 값을 가리킨다.
    val zoomProgress = remember { Animatable(1f) }
    val touchSlop = LocalViewConfiguration.current.touchSlop

    // 다른 사진으로 넘어가면 줌을 초기화한다. 확대된 채로 페이지가 바뀌면
    // 다음 사진이 엉뚱하게 잘린 상태로 나타난다.
    LaunchedEffect(photoKey) {
        zoomState.reset()
        zoomProgress.snapTo(1f)
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(photoKey) {
                detectTapGestures(
                    onTap = { onToggleChrome() },
                    onDoubleTap = { tapPoint ->
                        val zoomingIn = !zoomState.isZoomed
                        val startScale = zoomState.scale
                        val startOffset = zoomState.offset
                        val targetScale = if (zoomingIn) DOUBLE_TAP_SCALE else MIN_SCALE
                        val targetOffset = if (zoomingIn) {
                            // 두드린 지점이 화면 중앙으로 오도록 이동시킨다.
                            val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                            ((center - tapPoint) * (DOUBLE_TAP_SCALE - 1f))
                                .clampToBounds(DOUBLE_TAP_SCALE, containerSize)
                        } else {
                            Offset.Zero
                        }
                        scope.launch {
                            zoomProgress.snapTo(0f)
                            zoomProgress.animateTo(1f, tween(220)) {
                                zoomState.scale = lerp(startScale, targetScale, value)
                                zoomState.offset = Offset(
                                    lerp(startOffset.x, targetOffset.x, value),
                                    lerp(startOffset.y, targetOffset.y, value),
                                )
                            }
                        }
                    },
                )
            }
            .pointerInput(photoKey) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var verticalAccum = 0f
                    var verticalDirection: VerticalDrag? = null
                    var totalDrag = Offset.Zero

                    while (true) {
                        val event = awaitPointerEvent()
                        val active = event.changes.filter { it.pressed }
                        if (active.isEmpty()) break

                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val multiTouch = active.size >= 2

                        when {
                            // ── 핀치: 항상 줌으로 처리한다 ──
                            multiTouch -> {
                                val newScale = (zoomState.scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
                                val centroid = event.calculateCentroid()
                                val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                                // 손가락 사이 지점을 기준으로 확대되도록 offset 을 보정한다.
                                val focus = centroid - center
                                val ratio = newScale / zoomState.scale
                                zoomState.offset = ((zoomState.offset + focus) * ratio - focus + panChange)
                                    .clampToBounds(newScale, containerSize)
                                zoomState.scale = newScale
                                event.changes.forEach { it.consume() }
                            }

                            // ── 확대 상태의 한 손가락: 패닝 ──
                            zoomState.isZoomed -> {
                                // touchSlop 을 넘기 전에는 패닝으로 확정하지 않는다 — 안 그러면
                                // 확대된 상태에서의 더블탭(원래 크기로 되돌리는 탭) 두 번째
                                // 손가락 down 의 미세한 떨림까지 매 프레임 소비해 버려서,
                                // 같은 노드의 detectTapGestures 가 "탭이 취소됐다"고 보고
                                // 더블탭 자체를 인식하지 못한다. 확대 안 된 상태의 else 분기는
                                // 이미 이 방식이라, 여기도 똑같이 맞춘다.
                                totalDrag += panChange
                                if (abs(totalDrag.x) > touchSlop || abs(totalDrag.y) > touchSlop) {
                                    val proposed = zoomState.offset + panChange
                                    val clamped = proposed.clampToBounds(zoomState.scale, containerSize)
                                    zoomState.offset = clamped
                                    // 좌우 끝에 닿아 더 못 미는 수평 성분은 소비하지 않는다.
                                    // 그래야 Pager 가 이어받아 다음 사진으로 넘어간다.
                                    val hitHorizontalBound = abs(clamped.x - proposed.x) > 0.01f
                                    if (!hitHorizontalBound || abs(panChange.y) > abs(panChange.x)) {
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }

                            // ── 줌 1.0 의 한 손가락 ──
                            else -> {
                                totalDrag += panChange
                                if (verticalDirection == null) {
                                    // 수평이 우세하면 여기서 손을 뗀다 → Pager 가 페이지를 넘긴다.
                                    if (abs(totalDrag.x) > touchSlop && abs(totalDrag.x) > abs(totalDrag.y)) {
                                        break
                                    }
                                    if (abs(totalDrag.y) > touchSlop) {
                                        verticalDirection = if (totalDrag.y > 0) VerticalDrag.DOWN_TO_CLOSE
                                        else VerticalDrag.UP_TO_INFO
                                    }
                                }
                                when (verticalDirection) {
                                    VerticalDrag.DOWN_TO_CLOSE -> {
                                        verticalAccum += panChange.y
                                        onDismissDrag(verticalAccum)
                                        event.changes.forEach { it.consume() }
                                    }
                                    VerticalDrag.UP_TO_INFO -> {
                                        verticalAccum += panChange.y
                                        event.changes.forEach { it.consume() }
                                    }
                                    null -> Unit
                                }
                            }
                        }
                    }

                    when (verticalDirection) {
                        VerticalDrag.DOWN_TO_CLOSE -> onDismissRelease(verticalAccum)
                        VerticalDrag.UP_TO_INFO ->
                            if (-verticalAccum > SWIPE_UP_THRESHOLD_PX) onSwipeUpInfo()
                        null -> Unit
                    }

                    // 손가락을 뗐을 때 살짝만 확대돼 있으면(핀치 잔량) 1.0 으로 붙인다.
                    // 정확히 1.0 을 만들기가 어려운 핀치 특성상, 이게 없으면 사용자가
                    // 의도치 않게 "약간 확대된" 채로 남아 Pager 스와이프가 막힌다.
                    if (zoomState.scale in MIN_SCALE..SETTLE_SCALE_THRESHOLD) {
                        zoomState.reset()
                    }
                }
            },
    ) {
        content(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoomState.scale
                    scaleY = zoomState.scale
                    translationX = zoomState.offset.x
                    translationY = zoomState.offset.y
                },
        )
    }
}

private enum class VerticalDrag { DOWN_TO_CLOSE, UP_TO_INFO }

/**
 * 확대된 이미지가 화면 밖으로 빠져나가지 않도록 이동량을 제한한다.
 * 이게 없으면 사진을 밀어서 화면 밖으로 완전히 보내버릴 수 있다.
 */
private fun Offset.clampToBounds(scale: Float, size: IntSize): Offset {
    if (size == IntSize.Zero) return this
    val maxX = (size.width * (scale - 1f)) / 2f
    val maxY = (size.height * (scale - 1f)) / 2f
    return Offset(x.coerceIn(-maxX, maxX), y.coerceIn(-maxY, maxY))
}
