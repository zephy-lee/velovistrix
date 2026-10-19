package com.regnius.photoprism.feature.catalog

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.regnius.photoprism.R
import com.regnius.photoprism.core.model.CatalogItem
import com.regnius.photoprism.core.model.CatalogKind
import com.regnius.photoprism.core.model.ThumbSize
import com.regnius.photoprism.core.ui.CompactTopBarHeight
import com.regnius.photoprism.core.ui.ThumbnailLoader

/**
 * 라벨 목록 · 사람 목록 (Phase 3.5~3.6).
 *
 * 화면 하나가 둘을 다 그린다 — 받아오는 것도, 그리는 것도 같고
 * ([CatalogItem]) 다른 점은 썸네일 모양뿐이다. **사람은 동그랗게, 라벨은
 * 네모로** 그린다: 얼굴은 가운데 맞춰 자른 원이 표준이고(연락처·메신저가
 * 전부 그렇다), 사물·풍경을 원으로 자르면 무엇인지 알아보기 어려워진다.
 *
 * 탭하면 사진 목록으로 넘어간다. 앨범 상세와 달리 이미지·동영상 탭으로
 * 나누지 않는다 — 라벨·인물은 **검색 쿼리**라서([CatalogItem.query]) 그리드
 * 하나로 충분하고, 뷰어·슬라이드쇼는 `FeedKey` 를 타고 그대로 따라온다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    onBack: () -> Unit,
    onItemClick: (CatalogItem) -> Unit,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val kind = viewModel.kind
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // §17.29 — 목록을 스크롤하면 제목 줄을 접어 세로 공간을 돌려준다.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(kind.titleRes())) },
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
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            when (val s = state) {
                is CatalogUiState.Loading ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

                is CatalogUiState.Error ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(32.dp),
                        ) {
                            Text(
                                stringResource(kind.loadFailedRes()),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                s.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Button(onClick = viewModel::retry) { Text(stringResource(R.string.common_retry)) }
                        }
                    }

                is CatalogUiState.Loaded -> PullToRefreshBox(
                    isRefreshing = s.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (s.items.isEmpty()) {
                        // 비어 있어도 당길 수 있어야 한다 — 서버가 방금 색인을
                        // 끝냈을 수 있으므로 메시지까지 PullToRefreshBox 안에 둔다.
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(
                                stringResource(kind.emptyRes()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(32.dp),
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            // 사람은 이름이 짧고 얼굴이 작아도 알아볼 수 있어
                            // 더 촘촘하게, 라벨은 사물 사진이라 조금 크게.
                            columns = GridCells.Adaptive(
                                minSize = if (kind == CatalogKind.PERSON) 108.dp else 140.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                end = 8.dp,
                                top = 4.dp,
                                bottom = padding.calculateBottomPadding() + 16.dp,
                            ),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(s.items, key = { it.uid }) { item ->
                                CatalogCard(item, viewModel.thumbnails) { onItemClick(item) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogCard(item: CatalogItem, thumbnails: ThumbnailLoader, onClick: () -> Unit) {
    val circular = item.kind == CatalogKind.PERSON

    Column(
        Modifier.clickable(onClick = onClick).padding(bottom = 8.dp),
        horizontalAlignment = if (circular) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(if (circular) CircleShape else RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            // 요청 객체를 기억해 둔다 — 매 재구성마다 새로 만들면 Coil 이 다른
            // 요청으로 보고 로드를 다시 시작해, 무관한 상태 변화만으로 썸네일이
            // 깜빡인다(앨범 커버에서 실제로 겪은 것, 2026-09-06).
            val request = remember(item.uid, item.coverHash) {
                item.coverHash?.let { thumbnails.request(it, ThumbSize.ALBUM_COVER) }
            }
            if (request != null) {
                AsyncImage(
                    model = request,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // 대표 사진이 없으면 빈 칸으로 남긴다. 사람·라벨은 앨범과 달리
            // 대표 사진이 없는 경우가 흔해서(이름만 붙여 둔 얼굴 묶음), 실패한
            // 이미지 아이콘을 띄우면 목록이 깨진 것처럼 보인다.
        }
        Text(
            item.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (circular) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
        if (item.photoCount > 0) {
            Text(
                pluralStringResource(R.plurals.common_photo_count, item.photoCount, item.photoCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}
