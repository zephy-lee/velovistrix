package com.regnius.photoprism.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.regnius.photoprism.core.data.FeedKey
import com.regnius.photoprism.feature.albums.AlbumDetailScreen
import com.regnius.photoprism.feature.login.LoginScreen
import com.regnius.photoprism.feature.settings.SettingsScreen
import com.regnius.photoprism.feature.slideshow.SlideshowScreen
import com.regnius.photoprism.feature.viewer.ViewerScreen

/**
 * 앱 전체 네비게이션 그래프.
 *
 * 전체를 [SharedTransitionLayout] 으로 감싸는 이유는 그리드 ↔ 뷰어 전환을
 * 잇기 위해서다 (§5.5). 두 화면이 서로 다른 네비게이션 목적지라 공통 부모가
 * 없으면 Shared Element 가 성립하지 않는다.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavigation(
    isAuthenticated: Boolean,
    navController: NavHostController = rememberNavController(),
) {
    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = if (isAuthenticated) MainRoute else LoginRoute,
        ) {
            composable<LoginRoute> {
                LoginScreen(
                    onAuthenticated = {
                        navController.navigate(MainRoute) {
                            popUpTo(LoginRoute) { inclusive = true }
                        }
                    },
                )
            }

            composable<MainRoute> {
                MainShell(
                    onAlbumClick = { album ->
                        navController.navigate(
                            AlbumDetailRoute(album.uid, album.title, album.photoCount)
                        )
                    },
                    onPhotoClick = { key, index -> navController.openViewer(key, index) },
                    onStartSlideshow = { key, startIndex -> navController.openSlideshow(key, startIndex) },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                )
            }

            composable<SettingsRoute> {
                SettingsScreen(onBack = { navController.popFrom(it) })
            }

            composable<AlbumDetailRoute> { entry ->
                val route = entry.toRoute<AlbumDetailRoute>()
                AlbumDetailScreen(
                    albumUid = route.albumUid,
                    title = route.title,
                    photoCount = route.photoCount,
                    onBack = { navController.popFrom(entry) },
                    onPhotoClick = { key, index -> navController.openViewer(key, index) },
                    onStartSlideshow = { key, startIndex -> navController.openSlideshow(key, startIndex) },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                )
            }

            // 뷰어는 검정 배경 위에서 Shared Element 로 이미지 위치를 그리드와 잇는다.
            // NavHost 기본 전환(슬라이드류)을 그대로 두면, 이미지는 Shared Element
            // 애니메이션으로 부드럽게 움직이는데 그 뒤의 검정 배경은 NavHost 가 별도
            // 곡선으로 슬라이드·페이드시켜 "배경과 이미지가 따로 논다" 는 인상을 준다.
            //
            // 뒤로가기(popExitTransition)는 열 때(enterTransition)와 다른 길이를 쓴다.
            // 이미지의 Shared Element 축소 애니메이션은 SharedTransitionLayout 의
            // 오버레이에 그려져 이 배경 페이드와 별개로 자기 속도로 움직인다 — 배경을
            // 220ms 로 천천히 페이드시키면 그 동안 이미지는 이미 그리드 자리로 다
            // 줄어들어 있어, "배경과 이미지가 따로 노는" 것처럼 보인다(실사용 확인,
            // 2026-08-30). 배경·상단바를 거의 즉시 걷어내면 남는 건 이미지 하나의
            // 움직임뿐이라 훨씬 깔끔하다. 여는 방향은 이 문제가 없어 그대로 둔다.
            composable<ViewerRoute>(
                enterTransition = { fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(220)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(80)) },
            ) { entry ->
                val route = entry.toRoute<ViewerRoute>()
                ViewerScreen(
                    feedKey = route.toFeedKey(),
                    startIndex = route.startIndex,
                    onClose = { navController.popFrom(entry) },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                )
            }

            // 슬라이드쇼도 검은 화면 위에서 시작하므로 뷰어와 같은 이유로
            // NavHost 기본 슬라이드 전환 대신 페이드를 쓴다 — 다만 Shared
            // Element 로 이어지는 그림이 없어 양쪽 길이를 맞춰도 된다.
            composable<SlideshowRoute>(
                enterTransition = { fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(220)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(220)) },
            ) { entry ->
                val route = entry.toRoute<SlideshowRoute>()
                SlideshowScreen(
                    feedKey = route.toFeedKey(),
                    startIndex = route.startIndex,
                    onClose = { navController.popFrom(entry) },
                )
            }
        }
    }
}

/**
 * **이 화면에서 부른 것일 때만** 뒤로 간다.
 *
 * `popBackStack()` 은 어느 화면이 불렀는지 모른다 — 이미 팝된 뒤에 한 번 더
 * 불리면 그 다음 화면을 팝한다. 닫는 경로가 여럿인 뷰어에서는 빠른 조작으로
 * 두 번 불리기 쉽고, 그러면 그리드까지 닫혀 시작 화면으로 튕기거나 백스택이
 * 비어 빈 화면이 남는다(§17.31). 화면이 이미 팝됐으면 lifecycle 이
 * STARTED 아래로 내려가 있으므로 그걸로 걸러낸다.
 *
 * **팝했는지를 돌려준다.** 부르는 쪽이 그걸 알아야 한다 — 거부됐는데도
 * "닫는 중" 으로 넘어가면 화면이 닫히지도 살아있지도 않은 상태로 굳는다(§17.32).
 */
private fun NavHostController.popFrom(entry: NavBackStackEntry): Boolean =
    if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) popBackStack() else false

private fun NavHostController.openSlideshow(key: FeedKey, startIndex: Int = 0) {
    navigate(key.toSlideshowRoute(startIndex))
}

private fun NavHostController.openViewer(key: FeedKey, index: Int) {
    navigate(key.toViewerRoute(index))
}
