package com.regnius.photoprism.feature.albums

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.regnius.photoprism.R
import com.regnius.photoprism.core.model.Album
import com.regnius.photoprism.core.ui.CompactTopBarHeight

/**
 * 달력 · 나의 순간 · 지역 · 폴더 (Phase 3.1~3.4).
 *
 * **새 화면이 아니라 [AlbumsScreen] 에 제목 줄을 붙인 것**이다. 넷 다 서버가
 * `albums?type=` 으로 돌려주는 앨범 목록이라, 그릴 것이 앨범 탭과 완전히
 * 같다. 다른 점은 둘뿐이다 — 뒤로 갈 수 있는 제목 줄이 있고, 정렬 바가 없다
 * ([com.regnius.photoprism.core.model.AlbumKind.userSortable]).
 *
 * 어느 타입을 받아올지는 [AlbumsViewModel] 이 라우트 인자에서 직접 읽는다.
 * 이 화면은 그걸 모르고도 된다 — 제목만 받아서 얹는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumBrowseScreen(
    title: String,
    emptyMessage: String,
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit,
) {
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // §17.29 — 그리드를 스크롤하면 제목 줄을 접어 세로 공간을 돌려준다.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
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
        AlbumsScreen(
            onAlbumClick = onAlbumClick,
            // 하단 탭 바가 없는 화면이라(여기는 라우트다) 아래 여백은
            // 탭 바 자리만큼 비울 필요가 없다 — 앨범 탭의 80dp 와 달리
            // 시스템 내비게이션 인셋만 있으면 된다.
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            emptyMessage = emptyMessage,
        )
    }
}
