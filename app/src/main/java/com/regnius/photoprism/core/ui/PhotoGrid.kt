package com.regnius.photoprism.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import coil3.compose.AsyncImage
import com.regnius.photoprism.R
import com.regnius.photoprism.core.model.MediaType
import com.regnius.photoprism.core.model.Photo
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 전체 타임라인 · 앨범 이미지 탭 · 앨범 동영상 탭 · 즐겨찾기 · 검색 결과가
 * **전부 이 하나**를 재사용한다. 화면마다 그리드를 복사해두면 성능 튜닝을
 * 네 번 해야 하고, 한 곳만 고치는 실수가 반드시 생긴다.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PhotoGrid(
    items: LazyPagingItems<Photo>,
    thumbnails: ThumbnailLoader,
    onPhotoClick: (index: Int, photo: Photo) -> Unit,
    modifier: Modifier = Modifier,
    density: GridDensity = GridDensity.DEFAULT,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    gridState: LazyGridState = rememberLazyGridState(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onVisibleStartIndexChange: (Int) -> Unit = {},
    /** 전환의 주인공이 될 사진. §17.45 — 뷰어가 페이지를 넘기면 따라 바뀐다. */
    sharedPhotoUid: String? = null,
    onSharedPhotoChange: (String) -> Unit = {},
) {
    val formatMonth = rememberMonthFormatter()

    // 그리드 ↔ 뷰어를 잇는 Shared Element 는 **한 칸만** 필요하다(§5.5) —
    // 사용자가 누른 그 칸이다. 그런데 예전에는 보이는 모든 칸에 걸어 뒀다.
    //
    // `Modifier.sharedBounds` 는 `composed` 모디파이어라 매번 다시
    // materialize 되고, 그 안에서 `SnapshotStateMap.put` 으로 공유 요소
    // 레지스트리를 갱신한다. 이 맵은 **영속 해시맵**이라 put 마다 새 맵을
    // 만든다. 칸이 78개(가로 6칸)만 돼도 스크롤 중 그 비용이 프레임을
    // 통째로 잡아먹었다 — 실측으로 90 퍼센타일 프레임이 113ms → 17ms,
    // 같은 시간에 그려낸 프레임이 191장 → 830장이 됐다(§17.34).

    // 이미 불러온 만큼만으로 월별 구간을 나눈다 (Phase 2.5).
    //
    // 서버가 이미 최신순/오래된순으로 정렬해 주므로 여기서 다시 정렬하지
    // 않는다 — 그냥 순서대로 훑으면서 월이 바뀌는 지점에 헤더를 끼워 넣는다.
    // itemCount 가 바뀔 때(페이지 도착)마다 처음부터 다시 계산하는데, 이
    // 앱이 다루는 규모(§12.5 목표 1만 장)에서는 문자열 비교 몇만 번이라
    // 눈에 띄는 비용이 아니다. 더 커지면 마지막 라벨만 기억해 증분으로
    // 이어붙이는 식으로 바꾸면 된다.
    //
    // **진짜 sticky(스크롤 중 고정)는 아니다.** Compose 의 LazyVerticalGrid 는
    // LazyColumn 의 stickyHeader 같은 걸 기본 제공하지 않는다 — 헤더는 그냥
    // 목록 사이에 끼워진 전체 폭 행이고, 스크롤하면 같이 지나간다. 고정 헤더는
    // 커스텀 레이아웃이 필요한 별도 작업이라 미뤄 뒀다.
    val groupedEntries = remember(items.itemCount) { groupByMonth(items) }

    ThumbnailPrefetch(items, groupedEntries, gridState, thumbnails, density)

    // 슬라이드쇼가 "0번부터"가 아니라 **지금 보고 있는 자리부터** 시작할 수
    // 있도록, 화면 맨 위 사진이 목록의 몇 번째인지 알려준다(Phase 3.1).
    //
    // 그리드의 인덱스와 사진의 인덱스는 다르다 — 사이사이에 날짜 헤더가
    // 끼어 있어서다. [GridEntry.Item] 이 원래 사진 인덱스를 들고 있으므로
    // 그걸 그대로 쓰고, 맨 위가 헤더면 그 아래 첫 사진을 찾는다.
    val latestVisibleStartIndexChange by rememberUpdatedState(onVisibleStartIndexChange)
    LaunchedEffect(gridState, groupedEntries) {
        snapshotFlow { gridState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { entryIndex ->
                latestVisibleStartIndexChange(firstPhotoIndexFrom(groupedEntries, entryIndex))
            }
    }

    Box(modifier) {
    LazyVerticalGrid(
        // 고정 열 수로 하면 태블릿·가로모드에서 썸네일이 거대해진다.
        // Adaptive 는 화면 폭에 따라 열 수를 스스로 정한다.
        columns = GridCells.Adaptive(minSize = density.minCellDp.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(
            items = groupedEntries,
            // PhotoPrism UID 는 전역 유일하므로 이것만으로 충분하다.
            // 키가 없으면 페이지가 추가될 때 전체가 재구성된다.
            key = { entry ->
                when (entry) {
                    is GridEntry.Header -> "header-${entry.month}-${entry.occurrence}"
                    is GridEntry.Item -> entry.uid
                }
            },
            span = { entry ->
                if (entry is GridEntry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1)
            },
            contentType = { entry -> if (entry is GridEntry.Header) "header" else "photo" },
        ) { entry ->
            when (entry) {
                is GridEntry.Header -> DateHeader(formatMonth(entry.month))
                is GridEntry.Item -> {
                    // 실제 렌더링 시점에 items[index] 로 접근해야 한다 — 이게
                    // Paging 에게 "이 위치까지 봤다" 를 알려 다음 페이지 로드를
                    // 트리거하는 신호다. groupByMonth() 계산에 쓴 peek() 값은
                    // 이 목적으로 쓰면 안 된다(무한 스크롤이 멈춘다).
                    //
                    // entry.index 는 groupedEntries 를 만든 시점의 items.itemCount
                    // 기준으로 baked 된 값이다. Room 백엔드(Phase 2.9)에서는 이
                    // RemoteMediator 의 APPEND 쓰기 자체가 같은 테이블을 관찰하는
                    // PagingSource 를 무효화시켜, 플링 중 화면에 아직 안 들어온
                    // 셀이 나중에(리컴포지션 이후, 더 작아진 새 세대에 대해)
                    // 조합될 수 있다 — 그때 entry.index 가 새 itemCount 를
                    // 넘으면 items[entry.index] 가 IndexOutOfBoundsException 을
                    // 던진다(실기 재현됨). 최신 itemCount 로 다시 한번 범위를
                    // 확인해야 한다.
                    val photo = if (entry.index < items.itemCount) items[entry.index] else null
                    if (photo == null) {
                        Box(Modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant))
                    } else {
                        PhotoCell(
                            photo = photo,
                            thumbnails = thumbnails,
                            density = density,
                            onClick = {
                                // 전환할 칸을 먼저 정하고 나서 이동한다 —
                                // 그래야 이 칸이 sharedBounds 를 단 채로
                                // 전환이 시작된다.
                                onSharedPhotoChange(photo.uid)
                                onPhotoClick(entry.index, photo)
                            },
                            // §17.34 — **탭한 한 칸에만** 건다.
                            sharedTransitionScope =
                                sharedTransitionScope.takeIf { photo.uid == sharedPhotoUid },
                            animatedVisibilityScope =
                                animatedVisibilityScope.takeIf { photo.uid == sharedPhotoUid },
                        )
                    }
                }
            }
        }

        // 스크롤 끝에서 "더 불러오는 중" 을 보여준다.
        //
        // 이게 없으면 대량 앨범에서 사용자가 스크롤을 멈췄을 때 "계속 불러오는
        // 중이라 몇 초 걸린다" 와 "여기서 멈췄다(버그)" 를 구분할 수 없다.
        // 실제로 서버가 매 요청 처리에 시간이 걸리는 대형 앨범에서 이 신호가
        // 없으면 정상 동작 중인데도 멈춘 것처럼 보인다.
        item(key = "append-footer", span = { GridItemSpan(maxLineSpan) }) {
            AppendFooter(loadState = items.loadState.append, onRetry = items::retry)
        }
    }

    QuickScrollbar(
        gridState = gridState,
        groupedEntries = groupedEntries,
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
    )
    } // Box — 그리드 위에 빠른 스크롤바(2.6)를 겹쳐 그린다.
}

/**
 * [entryIndex] 번째 그리드 줄부터 아래로 훑어 첫 사진의 **사진 목록 기준**
 * 인덱스를 찾는다. 못 찾으면 0 — 슬라이드쇼가 처음부터 시작할 뿐이라 안전하다.
 */
internal fun firstPhotoIndexFrom(entries: List<GridEntry>, entryIndex: Int): Int {
    for (i in entryIndex.coerceAtLeast(0) until entries.size) {
        (entries[i] as? GridEntry.Item)?.let { return it.index }
    }
    return 0
}

/** 그리드에 실제로 나열되는 한 줄 — 날짜 헤더 아니면 사진. */
internal sealed interface GridEntry {
    /**
     * @param month 날짜를 못 읽었으면 null — 표기는 화면이 정한다.
     * @param occurrence 같은 달(날짜 없음 포함)이 여러 번 나올 수 있어 key 충돌을 막는다.
     */
    data class Header(val month: YearMonth?, val occurrence: Int) : GridEntry
    data class Item(val uid: String, val index: Int) : GridEntry
}

internal fun groupByMonth(items: LazyPagingItems<Photo>): List<GridEntry> = buildList {
    var lastMonth: YearMonth? = null
    var started = false
    var occurrence = 0
    for (i in 0 until items.itemCount) {
        // peek 만 쓴다 — 여기서 items[i] 를 부르면 그룹 계산만으로 아직
        // 화면에 나오지도 않은 페이지까지 Paging 이 미리 불러오게 된다.
        val photo = items.peek(i)
        // §17.18 — placeholder(아직 안 읽힌 자리)라고 건너뛰면 안 된다.
        // placeholder 를 켠 뒤로 items.itemCount 는 "로컬에 실제로 캐싱된
        // 총량"이라, 건너뛰면 그리드에 실제보다 적은 칸만 그려져 placeholder
        // 를 켠 의미가 없어진다 — 아직 날짜를 모르니 헤더만 못 붙일 뿐,
        // 칸 자체는 그대로 확보해야 한다(렌더링은 PhotoGrid 의 null 분기가
        // 이미 회색 칸으로 처리한다).
        if (photo != null) {
            val month = monthKey(photo.takenAtLocal)
            if (!started || month != lastMonth) {
                occurrence++
                add(GridEntry.Header(month, occurrence))
                lastMonth = month
                started = true
            }
        }
        add(GridEntry.Item(photo?.uid ?: "placeholder-$i", i))
    }
}

/**
 * `"2015-07-26T10:26:08Z"` -> `2015-07`. 못 읽으면 null.
 *
 * **표기는 하지 않는다.** "2015년 7월" 이냐 "July 2015" 냐는 로케일이 정하고,
 * 그 포맷은 [rememberMonthFormatter] 가 붙인다 — 파싱과 표기를 갈라 둬야
 * 파싱 규칙을 언어와 무관하게 테스트할 수 있다.
 */
internal fun monthKey(takenAtLocal: String?): YearMonth? {
    val raw = takenAtLocal?.trim()
    if (raw.isNullOrEmpty() || raw.length < 7) return null
    val year = raw.substring(0, 4).toIntOrNull()
    val month = raw.substring(5, 7).toIntOrNull()
    if (year == null || month == null || month !in 1..12) return null
    return YearMonth.of(year, month)
}

/** 연·월을 현재 로케일의 표기로 바꾸는 함수. 날짜가 없으면 대체 문구를 준다. */
@Composable
internal fun rememberMonthFormatter(): (YearMonth?) -> String {
    val noDate = stringResource(R.string.grid_no_date)
    val pattern = stringResource(R.string.pattern_month_header)
    return remember(noDate, pattern) {
        val formatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
        fun format(month: YearMonth?) = month?.format(formatter) ?: noDate
        ::format
    }
}

@Composable
private fun DateHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp, start = 4.dp),
    )
}

@Composable
private fun AppendFooter(loadState: LoadState, onRetry: () -> Unit) {
    // fillMaxSize() 를 쓰지 않는다 — 그리드 아이템은 세로로 무한 제약을 받을
    // 수 있어, fillMaxSize 로 높이를 매칭하려 하면 측정이 깨진다.
    when (loadState) {
        is LoadState.Loading -> Box(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp), strokeWidth = 2.dp)
        }

        is LoadState.Error -> Box(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            Alignment.Center,
        ) {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.grid_load_more_failed))
            }
        }

        is LoadState.NotLoading -> Unit
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PhotoCell(
    photo: Photo,
    thumbnails: ThumbnailLoader,
    density: GridDensity,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    // aspectRatio 를 고정해야 이미지 로드 전후로 셀 높이가 변하지 않는다.
    // 없으면 스크롤 중에 레이아웃이 튀면서 위치가 어긋난다.
    val base = Modifier
        .aspectRatio(1f)
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable(onClick = onClick)

    val shared = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            // sharedElement 대신 sharedBounds — ViewerScreen.kt 참고. 그리드는
            // Crop, 뷰어는 Fit 이라 서로 다른 화면비 처리를 쓰는데, sharedElement
            // 는 전환 중 한쪽 렌더링을 그대로 가져다 애니메이션되는 bounds에
            // 늘려 그려서, 뒤로가기 첫 프레임에 사진이 화면 전체에 Crop처럼
            // 꽉 차 보이는 왜곡이 있었다. sharedBounds 는 양쪽이 각자
            // contentScale로 독립 렌더링 후 크로스페이드한다.
            base.sharedBounds(
                rememberSharedContentState(key = photoSharedKey(photo.uid)),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> tween(220) },
            )
        }
    } else base

    Box(shared) {
        AsyncImage(
            // 요청 객체를 기억해 둔다. 이걸 매 재구성마다 새로 만들면
            // Coil 은 "다른 요청"으로 보고 로드를 다시 시작한다 — 화면 어딘가의
            // 무관한 상태가 바뀌어 재구성이 도는 것만으로 그리드 전체가
            // 깜빡인다(2026-09-06 실제로 발생).
            model = remember(photo.hash, density) { thumbnails.request(photo.hash, density.thumbSize) },
            contentDescription = photo.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (photo.type == MediaType.VIDEO || photo.type == MediaType.LIVE || photo.type == MediaType.ANIMATED) {
            MediaBadge(photo)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.MediaBadge(photo: Photo) {
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .padding(4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        if (photo.type == MediaType.LIVE) {
            Text("LIVE", style = MaterialTheme.typography.labelSmall, color = Color.White)
        } else if (photo.type == MediaType.ANIMATED) {
            Text("GIF", style = MaterialTheme.typography.labelSmall, color = Color.White)
        } else {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.common_video),
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** 그리드 ↔ 뷰어 Shared Element 를 잇는 키. 양쪽이 같은 값을 써야 한다. */
fun photoSharedKey(uid: String) = "photo-$uid"

/**
 * 보이는 범위보다 앞서 썸네일을 미리 받는다. §12.3
 *
 * Paging 의 prefetchDistance 는 JSON 만 당겨오므로, 이미지는 여기서 따로
 * 당겨야 한다. 이게 없으면 빠르게 스크롤할 때 회색 사각형만 지나간다.
 *
 * 날짜 헤더가 끼어들면서 `gridState` 의 인덱스는 더 이상 원본 Paging 인덱스와
 * 1:1 이 아니다 — [groupedEntries] (헤더+사진이 섞인 화면 표시 순서) 기준으로
 * 앞을 내다보고, 그중 사진 항목만 골라 원래 인덱스로 프리페치한다.
 */
@Composable
private fun ThumbnailPrefetch(
    items: LazyPagingItems<Photo>,
    groupedEntries: List<GridEntry>,
    gridState: LazyGridState,
    thumbnails: ThumbnailLoader,
    density: GridDensity,
) {
    LaunchedEffect(gridState, groupedEntries, density) {
        // 어디까지 미리 받아 뒀는지. 같은 구간을 매 줄마다 다시 큐에 넣지
        // 않으려고 기억해 둔다 — 고밀도에서는 한 번에 200개가 넘어간다.
        var prefetchedTo = 0

        // 창이 넘어가면 직전 묶음은 버린다 (§17.38).
        val window = PrefetchWindow()

        // 화면에 몇 칸이 보이는지는 밀도로 정해지므로 매 프레임 다시 잴 이유가
        // 없다. layoutInfo 를 흘려보내면 스크롤 중 매 프레임 람다가 도는데,
        // 거기서 Pair 를 만들면 그 쓰레기가 고스란히 프레임 예산을 갉아먹는다.
        val ahead = (density.columnsPerScreenEstimate() * PREFETCH_ROWS)
            .coerceIn(PREFETCH_MIN, PREFETCH_MAX)

        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last ->
                // 앞서 받아 둘 양은 **줄 수** 로 센다. 화면 한 장 전체(고밀도면
                // 200칸 넘는다)를 한 번에 큐에 넣으면 그 자체가 메인 스레드
                // 작업이 되어 스크롤을 방해한다 — 눈앞의 칸이 먼저 그려져야
                // 하는데 저 멀리 있는 것들과 같은 줄에 서게 된다(§17.34).
                val from = maxOf(last + 1, prefetchedTo).coerceAtMost(groupedEntries.size)
                val to = (last + ahead).coerceAtMost(groupedEntries.size)
                if (to <= from) return@collect

                // peek 을 쓴다 — get 은 Paging 에 "이 항목이 필요하다"고 알려
                // 로드를 앞당기지만, 프리페치는 화면에 없는 것을 미리 받는
                // 작업이라 페이징 트리거까지 당길 이유가 없다.
                val hashes = (from until to).mapNotNull { i ->
                    (groupedEntries.getOrNull(i) as? GridEntry.Item)?.let { entry ->
                        entry.index.takeIf { it < items.itemCount }?.let { items.peek(it)?.hash }
                    }
                }
                prefetchedTo = to
                if (hashes.isNotEmpty()) {
                    window.replace(thumbnails.prefetch(hashes, density.thumbSize))
                }
            }
    }
}

/** 몇 줄 앞까지 미리 받을지. */
private const val PREFETCH_ROWS = 3

private const val PREFETCH_MIN = 12
private const val PREFETCH_MAX = 48

/**
 * 빠른 스크롤바 + 날짜 말풍선 (Phase 2.6).
 *
 * 안드로이드 표준 패스트 스크롤러처럼 "손가락을 놓은 세로 위치가 곧 스크롤
 * 비율" 이다 — 델타 누적이 아니라 **절대 위치**로 계산해야, 트랙 아무 곳이나
 * 짚었을 때 그 지점까지 한 번에 뛴다(짚은 지점과 다른 곳에서 시작하는
 * 상대 드래그는 사용자 기대와 어긋난다).
 *
 * 목록이 몇 화면 안 되면 스크롤바 자체가 방해만 되므로 [SCROLLBAR_MIN_ITEMS]
 * 미만일 때는 아예 그리지 않는다.
 */
@Composable
private fun QuickScrollbar(
    gridState: LazyGridState,
    groupedEntries: List<GridEntry>,
    modifier: Modifier = Modifier,
) {
    val formatMonth = rememberMonthFormatter()
    // +1 은 PhotoGrid 가 항상 붙이는 append-footer 행. 미세한 오차라
    // 정확히 빼지 않아도 스크롤바 크기/위치 체감에 차이가 없다.
    //
    // **여기서 바로 return 하지 않는다.** totalCount 는 페이지가 도착할
    // 때마다 임계값을 넘나들 수 있는 값이다 — remember/LaunchedEffect 보다
    // 앞에서 조건부로 return 하면, 그 임계값을 넘는 순간 이 함수의 슬롯
    // 호출 순서가 recomposition 마다 달라져 슬롯 테이블이 꼬인다
    // (ZoomableImage.kt 의 동일한 교훈 참고). 그래서 아래 모든 state 훅은
    // 무조건 호출하고, 실제로 그릴지 여부만 맨 끝의 Box 호출에서 가른다.
    val totalCount = groupedEntries.size + 1
    val showScrollbar = totalCount >= SCROLLBAR_MIN_ITEMS

    val scope = rememberCoroutineScope()
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var bubbleLabel by remember { mutableStateOf<String?>(null) }

    val isScrolling = gridState.isScrollInProgress
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(isScrolling, dragging) {
        if (isScrolling || dragging) {
            visible = true
        } else {
            delay(SCROLLBAR_HIDE_DELAY_MS)
            visible = false
        }
    }

    val visibleCount = gridState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    val maxScrollIndex = (totalCount - visibleCount).coerceAtLeast(1)
    val thumbRatio = (visibleCount.toFloat() / totalCount).coerceIn(MIN_THUMB_RATIO, 1f)

    // 드래그 중에는 손가락이 진행률의 유일한 출처다. 손을 뗀 뒤에는 그리드를
    // 직접 스와이프해 스크롤해도 썸의 위치가 따라오도록 gridState 에서 다시
    // 읽는다.
    val progress = if (dragging) dragProgress else (gridState.firstVisibleItemIndex.toFloat() / maxScrollIndex).coerceIn(0f, 1f)

    // pointerInput 은 아래에서 Unit 키로 딱 한 번만 시작해 제스처 도중 재시작되지
    // 않게 한다 — maxScrollIndex 는 스크롤바를 드래그해 gridState 가 움직이는
    // 것만으로도(보이는 아이템 수가 미세하게 흔들려서) 거의 매 프레임 바뀌는
    // 값이라, 이걸 키로 쓰면 드래그 도중 제스처가 계속 재시작돼 손가락을 뗐다
    // 다시 짚어야만 이어지는 것처럼 끊긴다. rememberUpdatedState 로 "재시작 없이
    // 최신 값만" 읽는다.
    val latestMaxScrollIndex = rememberUpdatedState(maxScrollIndex)
    val latestGroupedEntries = rememberUpdatedState(groupedEntries)

    fun updateFromTouchY(y: Float) {
        val clamped = if (trackHeightPx > 0f) (y / trackHeightPx).coerceIn(0f, 1f) else 0f
        dragProgress = clamped
        val entries = latestGroupedEntries.value
        val targetIndex = (clamped * latestMaxScrollIndex.value).roundToInt().coerceIn(0, entries.lastIndex.coerceAtLeast(0))
        bubbleLabel = entries.take(targetIndex + 1).lastOrNull { it is GridEntry.Header }
            .let { (it as? GridEntry.Header)?.let { header -> formatMonth(header.month) } }
        scrollJob?.cancel()
        scrollJob = scope.launch { gridState.scrollToItem(targetIndex) }
    }

    val density = LocalDensity.current
    val thumbHeightPx = thumbRatio * trackHeightPx
    val thumbOffsetPx = progress * (trackHeightPx - thumbHeightPx)

    if (!showScrollbar) return

    Box(
        modifier
            .width(SCROLLBAR_TOUCH_WIDTH)
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    dragging = true
                    updateFromTouchY(down.position.y)
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.pressed }
                        if (change != null) {
                            updateFromTouchY(change.position.y)
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    dragging = false
                }
            },
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbOffsetPx.roundToInt()) },
        ) {
            Box(
                Modifier
                    .padding(end = 4.dp)
                    .width(4.dp)
                    .height(with(density) { thumbHeightPx.toDp() })
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }

        // 날짜 말풍선 — 드래그 중에만, 손가락이 있는 높이 옆에 붙여서 보여준다.
        // 스크롤바 자체와 별개로 페이드시키는 이유는, 스크롤(플링)만으로도
        // 스크롤바는 보이되 말풍선까지 뜨면 짚지도 않았는데 날짜가 떠 있어
        // 어색하기 때문이다 — 말풍선은 "지금 손가락이 짚은 지점" 만을 뜻한다.
        AnimatedVisibility(
            visible = dragging && bubbleLabel != null,
            enter = fadeIn(),
            exit = fadeOut(),
            // wrapContentWidth(unbounded = true) 가 핵심이다 — 이게 없으면 이
            // 노드는 여전히 부모(스크롤바 트랙, 28dp)의 measure 제약을 그대로
            // 받는다. offset 은 "그린 뒤 옮기기" 일 뿐 측정 단계에는 관여하지
            // 않아서, 말풍선 텍스트가 28dp 폭에 맞춰 한 글자씩 줄바꿈되는
            // 사고가 났었다 — unbounded 로 측정 자체를 부모 폭에서 풀어준다.
            modifier = Modifier
                .align(Alignment.TopEnd)
                .wrapContentWidth(align = Alignment.End, unbounded = true)
                .offset { IntOffset(-with(density) { SCROLLBAR_TOUCH_WIDTH.toPx() }.roundToInt(), (thumbOffsetPx + thumbHeightPx / 2 - with(density) { BUBBLE_HALF_HEIGHT.toPx() }).roundToInt()) },
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    bubbleLabel.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    softWrap = false,
                )
            }
        }
    }
}

private val SCROLLBAR_TOUCH_WIDTH = 28.dp
private val BUBBLE_HALF_HEIGHT = 18.dp
private const val SCROLLBAR_MIN_ITEMS = 60
private const val MIN_THUMB_RATIO = 0.05f
private const val SCROLLBAR_HIDE_DELAY_MS = 1000L
