package com.regnius.photoprism.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.regnius.photoprism.R
import com.regnius.photoprism.core.model.MediaType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Phase 2.2 — 검색 필터 시트. 기간 / 타입 / 카메라 / 위치 유무를 고른다.
 *
 * 적용은 stringResource(R.string.filter_apply) 버튼을 눌러야만 반영된다 — 칩을 하나씩 누를 때마다
 * 검색을 다시 트리거하면 서버를 불필요하게 여러 번 두드리고, 사용자가
 * 여러 필터를 조합하는 중간 상태로 결과가 계속 깜빡인다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFilterSheet(
    filters: SearchFilters,
    onApply: (SearchFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(filters) { mutableStateOf(filters) }
    var showDateRangeDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.filter_title), style = MaterialTheme.typography.titleLarge)
                if (draft.isActive) {
                    TextButton(onClick = { draft = SearchFilters() }) { Text(stringResource(R.string.filter_reset)) }
                }
            }

            FilterSection(stringResource(R.string.filter_period)) {
                PeriodRow(
                    dateFrom = draft.dateFrom,
                    dateTo = draft.dateTo,
                    onPreset = { from, to -> draft = draft.copy(dateFrom = from, dateTo = to) },
                    onCustom = { showDateRangeDialog = true },
                )
            }

            FilterSection(stringResource(R.string.filter_type)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = draft.type == null,
                            onClick = { draft = draft.copy(type = null) },
                            label = { Text(stringResource(R.string.common_all)) },
                        )
                    }
                    items(SearchFilters.TYPE_OPTIONS) { type ->
                        FilterChip(
                            selected = draft.type == type,
                            onClick = { draft = draft.copy(type = if (draft.type == type) null else type) },
                            label = { Text(type.label()) },
                        )
                    }
                }
            }

            FilterSection(stringResource(R.string.filter_camera)) {
                OutlinedTextField(
                    value = draft.camera.orEmpty(),
                    onValueChange = { draft = draft.copy(camera = it) },
                    placeholder = { Text(stringResource(R.string.filter_camera_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            FilterSection(stringResource(R.string.filter_location)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.hasLocation == null,
                        onClick = { draft = draft.copy(hasLocation = null) },
                        label = { Text(stringResource(R.string.common_all)) },
                    )
                    FilterChip(
                        selected = draft.hasLocation == true,
                        onClick = { draft = draft.copy(hasLocation = if (draft.hasLocation == true) null else true) },
                        label = { Text(stringResource(R.string.filter_has_location)) },
                    )
                    FilterChip(
                        selected = draft.hasLocation == false,
                        onClick = { draft = draft.copy(hasLocation = if (draft.hasLocation == false) null else false) },
                        label = { Text(stringResource(R.string.filter_no_location)) },
                    )
                }
            }

            TextButton(
                onClick = { onApply(draft); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.filter_apply) + if (draft.isActive) " (${draft.activeCount})" else "")
            }
        }
    }

    if (showDateRangeDialog) {
        DateRangeDialog(
            initialFrom = draft.dateFrom,
            initialTo = draft.dateTo,
            onConfirm = { from, to ->
                draft = draft.copy(dateFrom = from, dateTo = to)
                showDateRangeDialog = false
            },
            onDismiss = { showDateRangeDialog = false },
        )
    }
}

@Composable
private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun PeriodRow(
    dateFrom: LocalDate?,
    dateTo: LocalDate?,
    onPreset: (LocalDate?, LocalDate?) -> Unit,
    onCustom: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    // 라벨은 remember 밖에서 읽는다 — stringResource 는 @Composable 이라
    // remember 의 계산 블록(평범한 람다) 안에서는 부를 수 없다.
    val labelToday = stringResource(R.string.filter_today)
    val labelLast7 = stringResource(R.string.filter_last_7_days)
    val labelLast30 = stringResource(R.string.filter_last_30_days)
    val labelThisYear = stringResource(R.string.filter_this_year)
    val presets = remember(today, labelToday, labelLast7, labelLast30, labelThisYear) {
        listOf(
            labelToday to (today to today),
            labelLast7 to (today.minusDays(6) to today),
            labelLast30 to (today.minusDays(29) to today),
            labelThisYear to (today.withDayOfYear(1) to today),
        )
    }
    val isAll = dateFrom == null && dateTo == null
    val matchedPreset = presets.firstOrNull { (_, range) -> range.first == dateFrom && range.second == dateTo }
    val isCustomRange = !isAll && matchedPreset == null

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(selected = isAll, onClick = { onPreset(null, null) }, label = { Text(stringResource(R.string.filter_all_time)) })
        }
        items(presets) { (label, range) ->
            FilterChip(
                selected = matchedPreset?.first == label,
                onClick = { onPreset(range.first, range.second) },
                label = { Text(label) },
            )
        }
        item {
            FilterChip(
                selected = isCustomRange,
                onClick = onCustom,
                label = { Text(if (isCustomRange) "${dateFrom}~${dateTo ?: ""}" else stringResource(R.string.filter_custom_range)) },
            )
        }
    }
}

/**
 * §17.23/§17.24 — M3 `DateRangePicker` 는 전체 화면 크기를 전제로 설계돼
 * 있다. 예전엔 `AlertDialog` 안에 그대로 넣었는데, `AlertDialog` 의 좁은
 * 고정 폭에 눌려 요일 칸(특히 금·토)이 서로 겹치고, 시작일/종료일을
 * 나란히 보여주는 헤더도 폭이 없어 세로로 무너져 보였다. `Dialog` 를
 * 직접 써서 폭 제약(`usePlatformDefaultWidth = false`)을 풀어 정상
 * 크기(100%)로 그린다 — 축소해서 보여주는 시도(§17.24 최초 수정, 화면에
 * 왼쪽으로 쏠려 붙어 보이는 부작용이 있었다)는 되돌렸다.
 *
 * **키보드 직접 입력 모드(연필 아이콘)는 그대로 둔다.** §17.24 에서
 * `showModeToggle = false` 로 한 번 없앴었다 — 그 모드의 stringResource(R.string.filter_start_date)/
 * stringResource(R.string.filter_end_date) 두 `OutlinedTextField` 가 좁은 `AlertDialog` 안에서 종료일
 * 쪽만 두 줄로 밀려 내려가서였다. 그런데 그 근본 원인은 입력 모드
 * 자체가 아니라 §17.24 의 `AlertDialog` 폭 제약이었다 — 지금은 폭 제약
 * 없는 `Dialog` 로 이미 고쳤으니, 입력 모드도 다시 켜도 정상 폭에서
 * 렌더링된다. §17.25 에서 "날짜 수동으로 수정 못한다"는 사용자 지적으로
 * 다시 켰다(`showModeToggle` 기본값 `true`, 파라미터 자체를 뺌).
 *
 * `title`/`headline` 슬롯을 직접 채워 크기·정렬을 뒤집었다(§17.25) —
 * 기본값은 작은 좌측 정렬 타이틀(stringResource(R.string.filter_pick_date)) + 큰 헤드라인(선택한
 * 날짜) 조합인데, 사용자 요청으로 타이틀을 크고 가운데 정렬로, 헤드라인
 * (선택한 날짜 범위)은 예전 타이틀 정도 크기로 작게 바꿨다. 헤드라인은
 * 연도까지 포함해 표시한다("2026년 2월 2일" — 연도 없이 "2월 2일"만
 * 나오던 걸 사용자가 지적해 고침).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(
    initialFrom: LocalDate?,
    initialTo: LocalDate?,
    onConfirm: (LocalDate?, LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialFrom?.toEpochMillisUtc(),
        initialSelectedEndDateMillis = initialTo?.toEpochMillisUtc(),
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // §17.25 — 확인/취소 버튼이 안 보이던 버그: DateRangePicker 안의
        // 달력(월 목록)은 스크롤 가능한 컴포넌트라 높이 제약이 없으면 다이얼로그
        // 안에서 원하는 만큼 커져서 버튼 행을 화면 밖으로 밀어냈다. Surface 를
        // 화면 높이의 90% 로 고정하고, DateRangePicker 에 weight(1f) 를 줘서
        // "남은 공간만" 차지하게 해야 버튼 행이 항상 고정 높이로 아래에 남는다.
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxHeight(0.9f),
        ) {
            Column(Modifier.fillMaxHeight()) {
                DateRangePicker(
                    state = state,
                    modifier = Modifier.weight(1f),
                    title = {
                        Text(
                            stringResource(R.string.filter_pick_date),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 12.dp, end = 12.dp),
                        )
                    },
                    headline = {
                        // 날짜 표기는 언어마다 순서가 다르다 — 패턴 자체를 번역한다.
                        val datePattern = stringResource(R.string.pattern_full_date)
                        val formatter = remember(datePattern) {
                            DateTimeFormatter.ofPattern(datePattern, Locale.getDefault())
                        }
                        val startText = state.selectedStartDateMillis?.toLocalDateUtc()?.format(formatter) ?: stringResource(R.string.filter_start_date)
                        val endText = state.selectedEndDateMillis?.toLocalDateUtc()?.format(formatter) ?: stringResource(R.string.filter_end_date)
                        Text(
                            "$startText - $endText",
                            style = MaterialTheme.typography.bodyLarge,
                            // §17.25 재조정 — 8dp 는 "2일"의 가운데가 일요일 칸과
                            // 같은 줄에 겹칠 만큼 너무 왼쪽이었다. 20dp 로 늘림 —
                            // 실기 확인 후 추가 조정 가능.
                            modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 4.dp),
                        )
                    },
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                    TextButton(onClick = {
                        onConfirm(state.selectedStartDateMillis?.toLocalDateUtc(), state.selectedEndDateMillis?.toLocalDateUtc())
                    }) { Text(stringResource(R.string.common_confirm)) }
                }
            }
        }
    }
}

private fun LocalDate.toEpochMillisUtc(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toLocalDateUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun MediaType.label(): String = when (this) {
    MediaType.IMAGE -> stringResource(R.string.filter_type_photo)
    MediaType.VIDEO -> stringResource(R.string.filter_type_video)
    MediaType.LIVE -> stringResource(R.string.filter_type_live)
    MediaType.ANIMATED -> "GIF"
    else -> apiValue
}
