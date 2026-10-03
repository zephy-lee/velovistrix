package com.regnius.photoprism.baselineprofile

import android.graphics.Rect
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Rule
import org.junit.Test

/**
 * §11.7 — 첫 실행이 빨라지도록, 실제로 앱을 조작해 보고 밟은 코드 경로를 기록한다.
 *
 * 결과는 `:app/src/main/generated/baselineProfiles/` 에 들어간다.
 * 다시 만들 때는 §11.7 의 명령을 쓴다(설치를 남기는 옵션이 붙는다).
 *
 * **두 개로 나눠 둔 이유**: 산출물이 둘이다.
 * - `baseline-prof.txt` — 설치 때 미리 AOT 컴파일할 목록. 많을수록 좋다.
 * - `startup-prof.txt` — dex 안에서 **시동 코드를 한데 모으기** 위한 목록.
 *   여기에 스크롤·뷰어까지 섞어 넣으면 "시동에 쓰는 코드"라는 신호가 희석돼
 *   목적을 잃는다. 그래서 시동 경로만 담는 테스트를 따로 둔다.
 *
 * **조작은 전부 좌표로 한다**([tapCenterOf] / [swipeVertically]). 사진이 들어찬
 * 그리드에서는 썸네일이 도착할 때마다 화면이 다시 구성돼, 잡아 둔
 * [androidx.test.uiautomator.UiObject2] 가 몇 밀리초 만에 낡는다
 * ([StaleObjectException]). 찾자마자 좌표만 뽑아 쓰면 이 경주가 아예 없어진다.
 * 핸들을 들고 있다가 3·4차 실행이 연달아 여기서 죽었다(§11.7).
 */
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    /** 콜드 스타트만. Application → Hilt → 세션 복원 → 첫 프레임. */
    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }

    /**
     * 시동 이후의 주된 동선.
     *
     * 로그인돼 있지 않으면 로그인 화면에서 더 갈 데가 없어 아래가 통째로
     * 건너뛰어진다 — 실패하지는 않지만 정작 중요한 그리드·페이징·디코딩
     * 경로가 안 담긴다(§11.7 주의). 각 단계는 서로 독립이라 하나가 못 찾고
     * 지나가도 다음 단계는 그대로 진행된다.
     */
    @Test
    fun journey() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = false,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        // 시작 탭(기본 앨범)의 그리드부터.
        scrollGrid()

        // 사진 탭 — Paging 3 가 실제로 도는 유일한 경로다.
        // (앨범 목록은 페이징이 아니라 read-through 캐시다, §2.9)
        if (selectTab("사진")) {
            scrollGrid()
            if (openFirstCell()) {          // 뷰어 진입 = 원본 디코딩 경로
                browseViewer()
                device.pressBack()
                device.waitForIdle()
            }
        }

        if (selectTab("즐겨찾기")) {
            scrollGrid()
        }

        // 앨범 상세는 이 앱의 의도된 주 동선이다(§5.1).
        if (selectTab("앨범")) {
            if (openFirstCell()) {
                scrollGrid()
                device.pressBack()
                device.waitForIdle()
            }
        }
    }

    /**
     * 하단 탭을 누른다.
     *
     * **`By.text` 로 찾는다.** 코드에는 아이콘에 `contentDescription` 이 붙어
     * 있지만, Compose 가 라벨 Text 와 병합하면서 접근성 트리에는 `text` 만
     * 남는다 — 실기기 덤프로 확인했다(§11.7). `By.desc` 로 찾으면 아무것도
     * 안 잡혀서 탭 전환이 통째로 조용히 건너뛰어진다.
     */
    private fun MacrobenchmarkScope.selectTab(label: String): Boolean {
        if (!tapCenterOf(By.text(label))) return false
        device.wait(Until.hasObject(By.scrollable(true)), UI_TIMEOUT_MS)
        device.waitForIdle()
        return true
    }

    /**
     * 그리드의 첫 칸을 연다.
     *
     * 셀은 **`contentDescription` 을 가진 큰 노드**라는 점으로 고른다 — 동영상
     * 배지(약 39px)나 앱바 아이콘(약 68px)은 크기로 걸러진다. 사진 셀은 반대로
     * `text` 가 비어 있어서 탭과는 찾는 방법이 다르다(§11.7).
     */
    private fun MacrobenchmarkScope.openFirstCell(): Boolean =
        tapCenterOf(By.desc(ANY_TEXT), minHeight = device.displayHeight / 12)

    /**
     * 뷰어에 **머문다.**
     *
     * 열자마자 곧바로 닫으면 프로파일에 클래스 이름 한 줄만 남는다 — ART 는
     * 실제로 실행된 메서드를 기록하는데, 100ms 만에 닫히면 원본 디코딩도
     * 페이저도 돌 겨를이 없다. 5차 실행에서 뷰어 규칙이 1개에 그친 이유다
     * (§11.7). 그래서 뷰어가 뜬 것을 확인하고, 옆 사진으로 몇 장 넘겨 본다.
     */
    private fun MacrobenchmarkScope.browseViewer() {
        if (!device.wait(Until.hasObject(By.desc(VIEWER_CLOSE)), UI_TIMEOUT_MS)) return
        device.waitForIdle()

        val y = device.displayHeight / 2
        val right = (device.displayWidth * 0.8f).toInt()
        val left = (device.displayWidth * 0.2f).toInt()
        repeat(2) {
            device.swipe(right, y, left, y, SWIPE_STEPS)
            device.waitForIdle()
        }
    }

    /** 조건에 맞는 첫 노드의 **좌표를 뽑아** 그 지점을 누른다. */
    private fun MacrobenchmarkScope.tapCenterOf(
        selector: androidx.test.uiautomator.BySelector,
        minHeight: Int = 0,
    ): Boolean {
        val target = boundsOf(selector, minHeight) ?: return false
        device.click(target.centerX(), target.centerY())
        device.waitForIdle()
        return true
    }

    /**
     * 낡은 핸들은 넘어간다.
     *
     * 목록을 훑는 도중에도 화면이 다시 구성되면서 개별 노드가 낡을 수 있어,
     * 한 개씩 감싸고 실패한 것만 버린다. 프로파일 채집은 "코드 경로를 밟는" 게
     * 목적이라 한 동작이 미끄러졌다고 테스트 전체를 실패시킬 이유가 없다 —
     * 실패하면 그때까지 모은 것까지 통째로 버려진다.
     */
    private fun MacrobenchmarkScope.boundsOf(
        selector: androidx.test.uiautomator.BySelector,
        minHeight: Int,
    ): Rect? {
        val found = try {
            device.wait(Until.findObjects(selector), UI_TIMEOUT_MS)
        } catch (_: StaleObjectException) {
            null
        } ?: return null

        for (node in found) {
            val bounds = try {
                node.visibleBounds
            } catch (_: StaleObjectException) {
                continue
            }
            if (bounds.height() > minHeight) return bounds
        }
        return null
    }

    /**
     * 그리드를 굴린다 — 노드를 잡지 않고 화면 좌표로만 쓸어 넘긴다.
     *
     * 위아래 끝은 앱바·하단 바를 피해서 잡는다. 화면 가장자리에서 시작한
     * 제스처는 시스템 뒤로가기로 먹힌다.
     */
    private fun MacrobenchmarkScope.scrollGrid() {
        val x = device.displayWidth / 2
        val low = (device.displayHeight * 0.75f).toInt()
        val high = (device.displayHeight * 0.25f).toInt()
        repeat(2) {
            device.swipe(x, low, x, high, SWIPE_STEPS)   // 아래로 내려간다
            device.waitForIdle()
        }
        device.swipe(x, high, x, low, SWIPE_STEPS)       // 되돌아온다
        device.waitForIdle()
    }

    private companion object {
        /** play flavor 는 applicationId 에 접미사가 없다. */
        const val PACKAGE_NAME = "com.regnius.photoprism"
        const val UI_TIMEOUT_MS = 5_000L

        /** 작을수록 빠른 스와이프 = 플링에 가깝다. */
        const val SWIPE_STEPS = 8
        val ANY_TEXT: Pattern = Pattern.compile(".+")

        /** 뷰어가 떴다는 표지 — 닫기 버튼의 contentDescription. */
        const val VIEWER_CLOSE = "닫기"
    }
}
