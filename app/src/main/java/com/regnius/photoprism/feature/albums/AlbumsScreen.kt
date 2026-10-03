package com.regnius.photoprism.feature.albums

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.regnius.photoprism.R
import com.regnius.photoprism.core.model.Album
import com.regnius.photoprism.core.model.AlbumSort
import com.regnius.photoprism.core.ui.ThumbnailLoader

/**
 * 앱의 시작 화면 (§5.1).
 *
 * 사진 그리드와 달리 정사각형 타일 + 제목/장수를 함께 보여준다. 앨범은 개수가
 * 수십~수백 수준이라 사진 그리드만큼의 밀도가 필요 없고, 어떤 앨범인지
 * 식별하는 게 더 중요하기 때문이다.
 *
 * 정렬은 서버가 아니라 클라이언트에서 한다 — [AlbumSort] 주석 참고.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    onAlbumClick: (Album) -> Unit,
    contentPadding: PaddingValues,
    viewModel: AlbumsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            is AlbumsUiState.Loading ->
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            is AlbumsUiState.Error ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Text(stringResource(R.string.albums_load_failed), style = MaterialTheme.typography.titleMedium)
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = viewModel::retry) { Text(stringResource(R.string.common_retry)) }
                    }
                }

            is AlbumsUiState.Loaded -> {
                val albums = s.visible
                // Phase 2.10 — 당겨서 새로고침. 목록이 비어 있어도 당길 수
                // 있어야 하므로(서버에 새 앨범이 생겼을 수 있다) 빈 상태
                // 메시지까지 PullToRefreshBox 안에 둔다.
                PullToRefreshBox(
                    isRefreshing = s.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (albums.isEmpty()) {
                        // 앨범이 하나도 없으면 정렬/즐겨찾기 바도 의미가 없어
                        // 같이 감춘다 — 정렬할 대상이 없다.
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(
                                stringResource(R.string.albums_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(
                                start = 4.dp,
                                end = 4.dp,
                                top = contentPadding.calculateTopPadding(),
                                bottom = contentPadding.calculateBottomPadding(),
                            ),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // §17.27 — 정렬/즐겨찾기 바를 상단에 고정하지 않고
                            // 그리드의 첫 줄(전체 폭)로 넣어 앨범들과 같이
                            // 스크롤되게 한다. 스크롤을 내리는 동안에는 어차피
                            // 안 쓰는 컨트롤이라, 고정해서 세로 공간을 상시
                            // 차지하는 것보다 같이 밀려 올라가는 편이 낫다.
                            item(span = { GridItemSpan(maxLineSpan) }, key = "sort-bar") {
                                SortBar(
                                    loaded = s,
                                    onSortChange = viewModel::setSort,
                                    onFavoritesFirstChange = viewModel::setFavoritesFirst,
                                )
                            }
                            items(albums, key = { it.uid }) { album ->
                                AlbumCard(album, viewModel.thumbnails) { onAlbumClick(album) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 정렬 선택 + 즐겨찾기 우선 토글.
 *
 * 앨범 화면 전용으로 두는 이유는, 상단 앱바(검색/더보기)가 모든 탭이 공유하는
 * 자리라 앨범에만 있는 정렬 옵션을 거기 넣으면 다른 탭에서는 빈 채로 남기
 * 때문이다.
 *
 * 화면 상단에 고정하지 않고 그리드의 첫 줄로 넣어 같이 스크롤된다(§17.27).
 */
@Composable
private fun SortBar(
    loaded: AlbumsUiState.Loaded,
    onSortChange: (AlbumSort) -> Unit,
    onFavoritesFirstChange: (Boolean) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            TextButton(onClick = { menuOpen = true }) {
                Text(stringResource(loaded.sort.labelRes()), style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                AlbumSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sort.labelRes())) },
                        onClick = { onSortChange(sort); menuOpen = false },
                    )
                }
            }
        }

        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            FilterChip(
                selected = loaded.favoritesFirst,
                onClick = { onFavoritesFirstChange(!loaded.favoritesFirst) },
                label = { Text(stringResource(R.string.albums_favorites_first)) },
                leadingIcon = if (loaded.favoritesFirst) {
                    { Icon(Icons.Filled.Favorite, null, modifier = Modifier.padding(2.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

@Composable
private fun AlbumCard(album: Album, thumbnails: ThumbnailLoader, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(bottom = 8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            // 요청 객체를 기억해 둔다 — 매 재구성마다 새로 만들면 Coil 이
            // "다른 요청"으로 보고 로드를 다시 시작해서, 무관한 상태 변화로
            // 재구성이 도는 것만으로 커버가 깜빡인다(2026-09-06 실제로 발생).
            val coverRequest = remember(album.uid, album.coverHash) {
                // 전용 커버 엔드포인트는 플레이스홀더 SVG 를 주므로 쓰지 않는다.
                // Album.coverHash 주석 참고.
                album.coverHash?.let {
                    thumbnails.request(it, com.regnius.photoprism.core.model.ThumbSize.ALBUM_COVER)
                } ?: thumbnails.albumCoverRequest(album.uid)
            }
            AsyncImage(
                model = coverRequest,
                contentDescription = album.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            album.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
        Text(
            pluralStringResource(R.plurals.common_photo_count, album.photoCount, album.photoCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

/**
 * 정렬 기준의 번역된 이름.
 *
 * [AlbumSort] 자체는 `:core:model`(순수 Kotlin/JVM)에 있어 안드로이드 리소스를
 * 못 쓴다(§3.4). 그래서 enum 에는 값만 두고, 보여줄 이름은 여기서 붙인다.
 */
@StringRes
internal fun AlbumSort.labelRes(): Int = when (this) {
    AlbumSort.NAME_ASC -> R.string.album_sort_name_asc
    AlbumSort.NAME_DESC -> R.string.album_sort_name_desc
    AlbumSort.RECENTLY_UPDATED -> R.string.album_sort_recently_updated
    AlbumSort.OLDEST -> R.string.album_sort_oldest
}
