package com.regnius.photoprism.feature.albums

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.regnius.photoprism.R
import com.regnius.photoprism.core.data.FeedKey
import com.regnius.photoprism.core.ui.CompactTopBarHeight
import com.regnius.photoprism.core.ui.LoadedCount
import com.regnius.photoprism.feature.photos.PhotoFeedScreen
import kotlinx.coroutines.launch

/**
 * 앨범 상세 — 이미지 / 동영상 탭 분리 (§5.3).
 *
 * 두 탭은 **서버 쿼리로** 나눈다. 클라이언트에서 걸러내면 페이징이 망가진다 —
 * 한 페이지 60개 중 영상이 2개뿐이면 화면이 거의 빈 채로 다음 페이지를 계속
 * 불러오게 된다.
 *
 * 각 탭이 자기 스크롤 위치를 유지하도록 [HorizontalPager] 안에 독립된
 * [PhotoFeedScreen] 을 둔다.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AlbumDetailScreen(
    albumUid: String,
    title: String,
    photoCount: Int,
    onBack: () -> Unit,
    onPhotoClick: (feedKey: FeedKey, index: Int) -> Unit,
    onStartSlideshow: (feedKey: FeedKey, startIndex: Int) -> Unit = { _, _ -> },
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    // SecondaryTabRow 를 쓴다 — PrimaryTabRow 는 M3 스펙상 "화면의 최상위
    // 내비게이션" 용으로 설계되어 여백/인디케이터가 더 크다. 여기서는 상세
    // 화면 안의 부차적인 필터(이미지/동영상)라 SecondaryTabRow 가 맞고,
    // 제목 표시줄과 탭 사이 여백도 그만큼 줄어든다.
    val tabs = listOf(stringResource(R.string.album_tab_images), stringResource(R.string.album_tab_videos))
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // §17.29 — 그리드를 스크롤하면 제목 줄을 접어 세로 공간을 돌려준다.
    // 가로 모드에서는 제목 줄(64dp) + 탭 줄(48dp)이 화면 높이의 1/4 을
    // 넘게 먹어서 앨범 카드가 두 줄도 안 보였다.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // 탭을 바꾸면 접혀 있던 제목 줄을 다시 펴 준다 — 새 탭의 목록이 짧아
    // 스크롤이 안 되면 숨은 채로 남아버린다.
    LaunchedEffect(pagerState.currentPage) {
        scrollBehavior.state.heightOffset = 0f
        scrollBehavior.state.contentOffset = 0f
    }

    // 탭에 표시할 로드된 개수. PhotoPrism 검색 API 는 전체 개수를 미리 주지
    // 않아 "총 N장" 을 표시할 수 없다 — 대신 지금까지 실제로 불러온 개수를
    // 보여준다. 스크롤해도 이 숫자가 늘지 않으면 로딩이 멈춘 걸 바로 알 수
    // 있다는 부수 효과도 있다.
    var imageCount by remember { mutableStateOf(LoadedCount.ZERO) }
    var videoCount by remember { mutableStateOf(LoadedCount.ZERO) }

    // 오버플로 메뉴(⋮) — Phase 3.1 슬라이드쇼. **지금 보고 있는 탭**의 목록을
    // 재생한다: 이미지 탭에서 열면 사진만, 동영상 탭에서 열면 동영상만.
    var overflowOpen by remember { mutableStateOf(false) }

    // 탭마다 스크롤 위치가 따로라 시작 인덱스도 탭마다 따로 기억한다 —
    // 옆 탭도 미리 구성되므로(beyondViewportPageCount) 하나로 두면 지금 안
    // 보고 있는 탭의 위치가 덮어쓴다.
    val visibleStartIndices = remember { mutableStateListOf(0, 0) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    // 앨범의 전체 장수는 앨범 목록 응답에 들어 있어 **미리
                    // 알 수 있는 유일한 총량**이다. 탭의 숫자는 "지금까지
                    // 불러온 만큼" 이라 스크롤에 따라 늘어나므로, 기준이 되는
                    // 전체 수치를 여기 고정해 둔다.
                    //
                    // 세로에서는 제목 아래에 쌓고, 가로에서는 낮아진 바
                    // (48dp)에 두 줄이 들어가지 않으므로 한 줄로 붙인다(§17.29).
                    if (isLandscape) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (photoCount > 0) {
                                Text(
                                    "  " + pluralStringResource(R.plurals.common_photo_count, photoCount, photoCount),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    } else {
                        Column {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (photoCount > 0) {
                                Text(
                                    pluralStringResource(R.plurals.common_photo_count_total, photoCount, photoCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.common_more))
                        }
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_slideshow)) },
                                leadingIcon = { Icon(Icons.Filled.Slideshow, contentDescription = null) },
                                onClick = {
                                    overflowOpen = false
                                    val page = pagerState.currentPage
                                    onStartSlideshow(
                                        if (page == 0) {
                                            FeedKey.albumImages(albumUid)
                                        } else {
                                            FeedKey.albumVideos(albumUid)
                                        },
                                        visibleStartIndices.getOrElse(page) { 0 },
                                    )
                                },
                            )
                        }
                    }
                },
                expandedHeight = if (isLandscape) {
                    CompactTopBarHeight
                } else {
                    TopAppBarDefaults.TopAppBarExpandedHeight
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize()) {
            SecondaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.padding(top = padding.calculateTopPadding()),
            ) {
                tabs.forEachIndexed { index, label ->
                    val count = if (index == 0) imageCount else videoCount
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(if (count.count > 0) "$label ($count)" else label) },
                    )
                }
            }

            val direction = LocalLayoutDirection.current
            val gridPadding = PaddingValues(
                start = padding.calculateStartPadding(direction),
                end = padding.calculateEndPadding(direction),
                bottom = padding.calculateBottomPadding(),
                top = 2.dp,
            )

            // 옆 탭도 미리 구성한다.
            //
            // 두 가지를 동시에 해결한다: (1) 탭 라벨의 개수가 그 탭을 방문해야만
            // 나타나던 문제, (2) 탭을 처음 누를 때의 로딩 깜빡임. 대가는 앨범을
            // 열 때 요청이 한 번 더 나가는 것인데, 동영상은 보통 몇 개뿐이라
            // 응답이 작고 탭 전환이 즉시 되는 이득이 더 크다.
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val key = if (page == 0) FeedKey.albumImages(albumUid) else FeedKey.albumVideos(albumUid)
                PhotoFeedScreen(
                    key = key,
                    onPhotoClick = { index -> onPhotoClick(key, index) },
                    contentPadding = gridPadding,
                    emptyMessage = if (page == 0) stringResource(R.string.album_no_images) else stringResource(R.string.album_no_videos),
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onLoadedCountChanged = { if (page == 0) imageCount = it else videoCount = it },
                    onVisibleStartIndexChange = { visibleStartIndices[page] = it },
                )
            }
        }
    }
}
