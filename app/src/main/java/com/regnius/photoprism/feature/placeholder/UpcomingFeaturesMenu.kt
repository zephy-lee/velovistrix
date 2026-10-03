package com.regnius.photoprism.feature.placeholder

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.regnius.photoprism.R

/** 한 줄에 놓을 개수 (사용자 지정, 2026-09-06). 항목이 8개라 정확히 두 줄이다. */
private const val COLUMNS = 4

/**
 * 하단 "메뉴" 탭을 누르면 **하단 바 자리에 펼쳐지는** 패널.
 *
 * 전체 화면이 아니다(사용자 지정, 2026-09-06). 보고 있던 화면(앨범 그리드 등)이
 * 그대로 뒤에 남고, 하단 탭 바가 있던 자리에서 이 패널이 그 바를 대신한다 —
 * 메뉴를 여는 것이 "다른 화면으로 이동"이 아니라 "지금 화면 위에서 잠깐 고르는
 * 것"이라는 게 더 정확하기 때문이다. 닫으면 원래 보던 화면이 그대로 있다.
 *
 * 원래 이 탭이 설정 화면이었는데, 설정은 상단 앱바의 오버플로(⋮)로 옮겼다.
 * 하단 탭은 "라이브러리를 어떤 축으로 훑어볼 것인가"를 고르는 자리라, 설정
 * 같은 앱 환경설정이 그 넷 중 하나로 앉아 있는 게 어색했다.
 *
 * 전부 **비활성**이다. 없는 척 숨기는 것보다 "있을 예정인데 아직 안 된다"를
 * 보여주는 편이 낫다 — 이 앱이 어디로 갈지 알 수 있고, 나중에 항목이 하나씩
 * 살아날 때 자리가 움직이지 않는다.
 *
 * [UpcomingFeature.STORIES] 를 뺀 나머지는 전부 PhotoPrism 서버가 이미 제공하는
 * 것이라 읽기만 하면 된다(§6 Phase 4). 스토리만 서버에 없어 앱이 직접 만들어야
 * 한다(§7 백로그) — 그래서 목록의 맨 끝이다.
 */
@Composable
fun UpcomingFeaturesMenu(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)) {
        Text(
            stringResource(R.string.upcoming_notice),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
        )
        // FlowRow + `fillMaxWidth(1f/COLUMNS)` 였다가 고정 [Row] + weight 로
        // 바꿨다. 비율로 폭을 주면 픽셀로 환산할 때 각자 올림돼서, 넷을 더한
        // 값이 부모보다 1~3px 넓어지는 폭이 존재한다 — 그 폭에서는 FlowRow 가
        // 한 칸을 다음 줄로 넘겨 3·3·2 로 세 줄이 됐다. weight 는 남은 공간을
        // 나눠 갖는 방식이라 넘칠 수가 없어서 **항상 [COLUMNS] 개씩**이다.
        UpcomingFeature.entries.chunked(COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { feature -> UpcomingTile(feature, Modifier.weight(1f)) }
                // 항목 수가 [COLUMNS] 의 배수가 아닐 때 남는 칸. 이게 없으면
                // 마지막 줄의 타일만 홀로 넓어진다.
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private enum class UpcomingFeature(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    /** PhotoPrism 의 People — 얼굴 인식으로 묶인 인물(`/subjects`, `/faces`). */
    PEOPLE(R.string.upcoming_people, Icons.Filled.People),

    /** PhotoPrism 의 Calendar — 월 단위로 자동 생성되는 앨범(`type=month`). */
    CALENDAR(R.string.upcoming_calendar, Icons.Filled.CalendarMonth),

    /**
     * PhotoPrism 의 States — 시·도 단위로 자동 생성되는 앨범(`type=state`,
     * 예: "경기도, 대한민국"). 지도와 **다른 기능**이다: 이건 앨범 목록이라
     * 지금 그리드를 그대로 재사용하면 되고, 지도([PLACES])는 MapLibre 가 필요하다.
     */
    STATES(R.string.upcoming_states, Icons.Filled.Map),

    /**
     * PhotoPrism 의 Places — GPS 가 있는 사진을 세계 지도에 찍어 보여주는 화면.
     *
     * [STATES] 와 나란히 놓이므로 **면적(지도) 대 지점(핀)** 으로 가른다.
     * 둘 다 지구본·지도 계열이면 아이콘만 봐서는 구분이 안 된다.
     */
    PLACES(R.string.upcoming_places, Icons.Filled.Place),

    /** PhotoPrism 의 Folders — 원본 디렉터리 구조 그대로(`type=folder` 앨범). */
    FOLDERS(R.string.upcoming_folders, Icons.Filled.Folder),

    /** PhotoPrism 의 Labels — 서버가 자동 인식한 태그(`/labels`). */
    LABELS(R.string.upcoming_labels, Icons.AutoMirrored.Filled.Label),

    /** PhotoPrism 의 Moments — 장소·시기·라벨로 서버가 자동으로 묶어 주는 모음(`type=moment`). */
    MOMENTS(R.string.upcoming_moments, Icons.Filled.AutoAwesome),

    /** 서버에 없는 기능 — 앱이 직접 만들어야 한다(§7 백로그). */
    STORIES(R.string.upcoming_stories, Icons.Filled.AutoStories),
}

@Composable
private fun UpcomingTile(feature: UpcomingFeature, modifier: Modifier = Modifier) {
    // 색으로만 비활성을 표현하고, clickable 을 아예 안 붙인다 — 눌러도 물결
    // 하나 안 뜨는 것으로 "지금은 안 되는 것"이 바로 전달된다.
    val content = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Column(
        modifier = modifier.padding(horizontal = 1.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(feature.icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
        }
        Text(
            stringResource(feature.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
