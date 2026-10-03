package com.regnius.photoprism.core.ui

import androidx.compose.ui.unit.dp

/**
 * 가로 모드에서 쓰는 낮은 타이틀 바 높이(§17.29).
 *
 * 가로 화면은 높이가 400dp 안팎이라 표준 64dp 짜리 타이틀 바가 15% 를 넘게
 * 먹는다. 아이콘 버튼의 최소 터치 타깃(48dp)이 유지되는 선까지만 낮춘다 —
 * 세로 모드는 표준 높이를 그대로 쓴다.
 *
 * `MainShell` 과 `AlbumDetailScreen` 이 같은 값을 써야 화면을 오갈 때 바
 * 높이가 튀지 않아서 여기 공용으로 둔다.
 */
val CompactTopBarHeight = 48.dp
