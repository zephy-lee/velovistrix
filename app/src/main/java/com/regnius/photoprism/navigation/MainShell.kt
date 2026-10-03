package com.regnius.photoprism.navigation

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.regnius.photoprism.R
import com.regnius.photoprism.core.data.FeedKey
import com.regnius.photoprism.core.model.Album
import com.regnius.photoprism.core.ui.CompactTopBarHeight
import com.regnius.photoprism.feature.albums.AlbumsScreen
import com.regnius.photoprism.feature.photos.PhotoFeedScreen
import com.regnius.photoprism.feature.placeholder.UpcomingFeaturesMenu
import com.regnius.photoprism.feature.search.SearchBody
import com.regnius.photoprism.feature.search.SearchFilterSheet
import com.regnius.photoprism.feature.search.SearchFilters
import com.regnius.photoprism.feature.search.SearchTopBarContent
import com.regnius.photoprism.feature.search.SearchViewModel
import com.regnius.photoprism.feature.settings.SettingsViewModel

/** 하단 바 좌우 여백 — 세로 모드 기준(§17.28). 이 값으로 바의 고정 폭을 정한다. */
private val BOTTOM_BAR_SIDE_MARGIN = 64.dp

/** 창이 아주 좁아도 탭 4개가 뭉개지지 않도록 하는 하한(§17.28). */
private val BOTTOM_BAR_MIN_WIDTH = 220.dp

/** 메뉴 패널 좌우 여백 — 하단 알약보다 넓어야 아이콘 4개가 한 줄에 들어간다. */
private val MENU_PANEL_SIDE_MARGIN = 12.dp

/** 태블릿처럼 짧은 변마저 넓은 기기에서 패널이 지나치게 벌어지지 않게 하는 상한. */
private val MENU_PANEL_MAX_WIDTH = 480.dp

/**
 * 하단 4탭 셸 (§5.1).
 *
 * 상단 앱바에는 검색과 오버플로(⋮ — 슬라이드쇼·설정)가 있다. 설정은 원래
 * 하단 "메뉴" 탭의 본문이었는데, 하단 탭은 "라이브러리를 어떤 축으로 훑을
 * 것인가"를 고르는 자리라 앱 환경설정이 그 넷 중 하나로 앉아 있는 게
 * 어색해서 이리로 옮겼다. 빈 자리가 된 메뉴 탭은 앞으로 만들 탐색 기능들을
 * 비활성으로 보여준다([UpcomingFeaturesScreen]).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MainShell(
    onAlbumClick: (Album) -> Unit,
    onPhotoClick: (FeedKey, Int) -> Unit,
    onStartSlideshow: (FeedKey, startIndex: Int) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.START) }

    // Phase 2.11 설정의 "시작 화면" 을 반영한다. 앱을 새로 열 때(=이 MainShell
    // 인스턴스가 처음 생겼을 때) 딱 한 번만 적용하고, 그 뒤로 사용자가 탭을
    // 직접 눌러 옮겨 다니는 것까지 설정값이 계속 따라와 덮어쓰면 안 된다 —
    // tabInitialized 로 "이미 한 번 적용했다" 를 기억해 둔다.
    var tabInitialized by rememberSaveable { mutableStateOf(false) }
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    // §17.43 — **실제 설정값이 온 뒤에** 적용한다. null 은 "아직 안 읽었다" 다.
    // 예전에는 `startTab` 을 썼는데 그건 DataStore 를 읽기 전에 기본값을 한 번
    // 내보내므로, 그 자리표시자를 첫 프레임에 적용하고 잠가 버려 설정이 영영
    // 반영되지 않았다.
    val configuredStartTab by settingsViewModel.initialStartTab.collectAsStateWithLifecycle()
    LaunchedEffect(configuredStartTab) {
        val tab = configuredStartTab ?: return@LaunchedEffect
        if (!tabInitialized) {
            selectedTab = MainTab.from(tab)
            tabInitialized = true
        }
    }

    // 검색은 별도 라우트가 아니라 지금 보던 탭 위에 얹히는 오버레이다 (Phase 2.1).
    // 탭을 오가며 열고 닫을 때 화면 전환 애니메이션이 끼어들 이유가 없어서
    // 그냥 앱바/본문을 검색 모드로 바꿔치기한다.
    //
    // §17.47 — 이 셋은 여기 지역 상태가 아니라 ViewModel 에 있다. 뷰어는 별도
    // 라우트라 사진을 열면 이 컴포지션이 떠나는데, 예전에는 필터만 `remember`
    // 여서 돌아왔을 때 초기화됐다. ViewModel 은 `MainRoute` 의 백스택 엔트리에
    // 묶여 있어 뷰어를 다녀와도 살아 있다.
    val searchViewModel: SearchViewModel = hiltViewModel()
    val searchActive by searchViewModel.active.collectAsStateWithLifecycle()
    val searchQuery by searchViewModel.query.collectAsStateWithLifecycle()
    val searchFilters by searchViewModel.filters.collectAsStateWithLifecycle()
    var filterSheetOpen by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    // 하단 "메뉴"는 탭이 아니라 하단 바 자리에 펼쳐지는 패널이다 — 누르면
    // 보고 있던 화면은 그대로 두고 그 위로 열린다(사용자 지정, 2026-09-06).
    var menuOpen by remember { mutableStateOf(false) }

    // 메뉴가 탭 본문이던 시절의 저장값([rememberSaveable])이 남아 있을 수 있다.
    LaunchedEffect(Unit) {
        if (selectedTab == MainTab.MENU) selectedTab = MainTab.START
    }

    BackHandler(enabled = searchActive) { searchViewModel.close() }
    BackHandler(enabled = menuOpen) { menuOpen = false }

    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // §17.29 — 가로 모드에서 세로 공간이 부족하다는 지적. 타이틀 바를
    // 스크롤에 따라 접는다: 아래로 내리면 위로 사라지고, 조금이라도 위로
    // 올리면 바로 다시 내려온다(`enterAlwaysScrollBehavior`). 가로 411dp
    // 짜리 화면에서 64dp 는 15% 가 넘는 면적이라, 목록을 보는 동안만이라도
    // 돌려주는 효과가 크다. 세로에서도 같은 이득이라 방향을 안 가린다.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // 탭을 바꾸거나 검색을 열고 닫을 때는 접혀 있던 바를 다시 펴 준다 —
    // 안 그러면 새 화면의 내용이 짧아 스크롤할 수 없을 때 바가 숨은 채로
    // 남아 되살릴 방법이 없다.
    LaunchedEffect(selectedTab, searchActive) {
        scrollBehavior.state.heightOffset = 0f
        scrollBehavior.state.contentOffset = 0f
    }

    // 오버플로 메뉴(⋮). Phase 3.1 슬라이드쇼가 첫 항목이다.
    var overflowOpen by remember { mutableStateOf(false) }

    // 슬라이드쇼를 목록 맨 앞이 아니라 **지금 스크롤해 둔 자리**부터 시작한다.
    // 탭을 옮기면 그리드가 통째로 새로 만들어져 스크롤도 처음으로 돌아가므로
    // 이 값도 같이 초기화한다.
    var visibleStartIndex by remember(selectedTab) { mutableIntStateOf(0) }


    // 슬라이드쇼가 재생할 목록. **지금 보고 있는 화면의 목록**을 그대로 쓴다 —
    // 어디서 열었든 화면에 있던 사진들이 그대로 흘러가는 게 예상에 맞다.
    // 앨범 목록 탭과 메뉴 탭에는 사진 목록 자체가 없어 null 이고, 그때는
    // 메뉴 버튼이 비활성으로 남는다.
    val slideshowKey: FeedKey? = remember(selectedTab) {
        when (selectedTab) {
            MainTab.PHOTOS -> FeedKey.timeline()
            MainTab.FAVORITES -> FeedKey.favorites()
            MainTab.ALBUMS, MainTab.MENU -> null
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        SearchTopBarContent(
                            query = searchQuery,
                            onQueryChange = searchViewModel::setQuery,
                            onSubmit = { searchViewModel.commitSearch(searchQuery) },
                            onClose = searchViewModel::close,
                            focusRequester = searchFocusRequester,
                            // 뷰어에서 돌아온 경우에는 키보드를 다시 올리지
                            // 않는다 — 보러 온 결과를 절반이나 가린다. 아직
                            // 아무것도 안 친 새 검색일 때만 자동으로 잡는다.
                            autoFocus = searchQuery.isBlank() && !searchFilters.isActive,
                            activeFilterCount = searchFilters.activeCount,
                            onOpenFilters = { filterSheetOpen = true },
                        )
                    } else {
                        Text(stringResource(R.string.app_name))
                    }
                },
                actions = {
                    if (!searchActive) {
                        IconButton(onClick = { searchViewModel.open(); menuOpen = false }) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.common_search))
                        }
                        Box {
                            IconButton(onClick = { overflowOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.common_more))
                            }
                            DropdownMenu(
                                expanded = overflowOpen,
                                onDismissRequest = { overflowOpen = false },
                            ) {
                                // 사진 목록이 없는 탭(앨범 목록·메뉴)에서는
                                // 재생할 게 없다. 항목을 숨기지 않고 비활성으로
                                // 두는 편이 낫다 — 메뉴를 열 때마다 항목 수가
                                // 달라지면 어디에 뭐가 있는지 외울 수 없다.
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_slideshow)) },
                                    leadingIcon = { Icon(Icons.Filled.Slideshow, contentDescription = null) },
                                    enabled = slideshowKey != null,
                                    onClick = {
                                        overflowOpen = false
                                        slideshowKey?.let { onStartSlideshow(it, visibleStartIndex) }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_settings)) },
                                    leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                    onClick = {
                                        overflowOpen = false
                                        onOpenSettings()
                                    },
                                )
                            }
                        }
                    }
                },
                // 가로 모드에서는 바 자체도 낮춘다(§17.29) — 세로는 표준
                // 높이(64dp)를 그대로 둔다. 세로는 아직 답답하다는 얘기가
                // 없었고, 표준 높이가 터치 타깃/여백 면에서 가장 안전하다.
                expandedHeight = if (isLandscape) CompactTopBarHeight else TopAppBarDefaults.TopAppBarExpandedHeight,
                scrollBehavior = scrollBehavior,
            )
        },
        // bottomBar 를 빈 칸으로 두어 본문(Scaffold body)이 화면 전체를 쓰게 함.
        // 대신 본문 위에 투명한 네비게이션 바를 직접 얹는다 (Google Photos 스타일).
        bottomBar = {},
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            if (searchActive) {
                SearchBody(
                    query = searchQuery,
                    filters = searchFilters,
                    onSubmitQuery = { term ->
                        searchViewModel.setQuery(term)
                        searchViewModel.commitSearch(term)
                    },
                    onPhotoClick = { index -> onPhotoClick(searchFilters.toFeedKey(searchQuery), index) },
                    contentPadding = padding,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
                if (filterSheetOpen) {
                    SearchFilterSheet(
                        filters = searchFilters,
                        onApply = searchViewModel::setFilters,
                        onDismiss = { filterSheetOpen = false },
                    )
                }
            } else {
                // 하단 바가 본문 위에 떠 있으므로(overlay), 마지막 아이템이
                // 바에 가려지지 않도록 아래쪽에 충분한 여백을 준다.
                val contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = 80.dp
                )

                when (selectedTab) {
                    MainTab.ALBUMS -> AlbumsScreen(
                        onAlbumClick = onAlbumClick,
                        contentPadding = contentPadding,
                    )

                    MainTab.PHOTOS -> {
                        val key = remember { FeedKey.timeline() }
                        PhotoFeedScreen(
                            key = key,
                            onPhotoClick = { index -> onPhotoClick(key, index) },
                            contentPadding = contentPadding,
                            modifier = Modifier.fillMaxSize(),
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onVisibleStartIndexChange = { visibleStartIndex = it },
                        )
                    }

                    MainTab.FAVORITES -> {
                        val key = remember { FeedKey.favorites() }
                        PhotoFeedScreen(
                            key = key,
                            onPhotoClick = { index -> onPhotoClick(key, index) },
                            contentPadding = contentPadding,
                            modifier = Modifier.fillMaxSize(),
                            emptyMessage = stringResource(R.string.favorites_empty),
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onVisibleStartIndexChange = { visibleStartIndex = it },
                        )
                    }

                    // 메뉴는 탭 본문이 아니라 아래쪽 패널이라 여기서 그릴 게 없다.
                    MainTab.MENU -> Unit
                }

                // 하단 메뉴를 더 콤팩트하게 수정 (§UX 수정).
                //
                // §17.28 — 폭을 "남는 만큼"(좌우 64dp 여백)이 아니라 **화면의
                // 짧은 변 기준 고정값**으로 잡는다. 예전엔 좌우 여백만 주다 보니
                // 가로 모드에서 바가 화면 폭만큼 늘어나 탭 4개가 휑하게 벌어졌다.
                // 짧은 변은 회전해도 그대로라(세로의 폭 = 가로의 높이), 이 값을
                // 쓰면 가로/세로 어느 쪽이든 세로 모드와 똑같은 크기로 그려지고
                // BottomCenter 정렬 덕에 가로에서는 가운데에 뜬다.
                val configuration = LocalConfiguration.current
                val barWidth = remember(configuration.screenWidthDp, configuration.screenHeightDp) {
                    val shortestEdge = minOf(configuration.screenWidthDp, configuration.screenHeightDp).dp
                    (shortestEdge - BOTTOM_BAR_SIDE_MARGIN * 2)
                        .coerceAtLeast(BOTTOM_BAR_MIN_WIDTH)
                        // 분할 화면처럼 창이 아주 좁을 때 화면 밖으로 나가지 않게.
                        .coerceAtMost((configuration.screenWidthDp.dp - 16.dp).coerceAtLeast(0.dp))
                }
                // 메뉴가 열려 있으면 화면 전체를 살짝 덮어 패널을 읽기 쉽게
                // 하고, 아무 데나 눌러 닫을 수 있게 한다. 배경 화면은 그대로
                // 비쳐 보여야 하므로(사용자 지정) 스크림은 옅게만 깐다.
                if (menuOpen) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { menuOpen = false },
                    )
                }

                // 메뉴 패널은 탭 4개짜리 알약보다 넓어야 아이콘 8개가 두 줄로
                // 들어간다. 폭을 잡는 방식은 알약과 똑같이 **화면의 짧은 변
                // 기준**이다(§17.28) — "화면 폭 - 여백" 으로 두면 가로 모드에서
                // 패널이 늘어나 세로일 때와 크기가 달라진다. 짧은 변은 회전해도
                // 그대로라, 이 값을 쓰면 가로/세로에서 폭이 같고 (폭이 같으니)
                // 타일 크기와 패널 높이까지 같아진다.
                //
                // 폭을 애니메이션하지도 않는다. 애니메이션을 걸었더니 프레임마다
                // 패널 폭이 달라지면서 그 안의 타일이 매번 다시 배치돼, 메뉴가
                // 열리는 동안 화면이 여러 번 깜빡이는 것처럼 보였다. 한 번에
                // 최종 폭으로 그리는 편이 낫다.
                val menuWidth = remember(configuration.screenWidthDp, configuration.screenHeightDp) {
                    val shortestEdge = minOf(configuration.screenWidthDp, configuration.screenHeightDp).dp
                    (shortestEdge - MENU_PANEL_SIDE_MARGIN * 2)
                        .coerceAtMost(MENU_PANEL_MAX_WIDTH)
                        .coerceAtLeast(BOTTOM_BAR_MIN_WIDTH)
                        // 분할 화면처럼 창이 아주 좁을 때 화면 밖으로 나가지 않게.
                        // 반드시 마지막에 걸어야 위의 하한을 이긴다.
                        .coerceAtMost((configuration.screenWidthDp.dp - 16.dp).coerceAtLeast(0.dp))
                }
                val panelWidth = if (menuOpen) menuWidth else barWidth

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .width(panelWidth)
                        // 가로 모드에서 내비게이션 바가 좌/우 측면에 붙는 기기가
                        // 있는데, 그 인셋까지 반영하면 고정 폭 안쪽이 한쪽만
                        // 깎여 바가 가운데에서 밀려 보인다. 바는 어차피 가운데
                        // 좁게 떠 있어 측면 막대와 겹칠 일이 없으므로 아래쪽
                        // 인셋만 반영한다.
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(24.dp),
                    // 메뉴일 때는 뒤 화면이 글자에 비쳐 읽기 어려워지지 않도록
                    // 조금 더 불투명하게.
                    color = MaterialTheme.colorScheme.surface.copy(alpha = if (menuOpen) 0.97f else 0.85f),
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                ) {
                    // 메뉴를 열면 하단 바가 사라지고 **그 자리에** 메뉴가
                    // 들어선다(사용자 지정) — 별도 화면으로 넘어가는 게 아니라
                    // 지금 화면 위에서 잠깐 고르는 것이라는 게 더 정확하다.
                    if (menuOpen) {
                        UpcomingFeaturesMenu()
                    } else {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            windowInsets = WindowInsets(0.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            MainTab.entries.forEach { tab ->
                                val isMenuTab = tab == MainTab.MENU
                                NavigationBarItem(
                                    selected = !isMenuTab && selectedTab == tab,
                                    onClick = {
                                        if (isMenuTab) menuOpen = true else selectedTab = tab
                                    },
                                    icon = {
                                        Icon(
                                            tab.icon(),
                                            contentDescription = stringResource(tab.labelRes),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            stringResource(tab.labelRes),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun MainTab.icon() = when (this) {
    MainTab.PHOTOS -> Icons.Filled.Photo
    MainTab.ALBUMS -> Icons.Filled.PhotoLibrary
    MainTab.FAVORITES -> Icons.Filled.Favorite
    MainTab.MENU -> Icons.Filled.Menu
}
