package com.regnius.photoprism.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * §17.22 — 동영상 재생처럼 "시도하기 전에 네트워크가 아예 없다는 걸 미리
 * 알아야" 하는 곳 전용이다. 일반적인 API 호출은 그냥 시도하고 실패하면
 * 그때 에러를 보여주는 걸로 충분하다(Retrofit/OkHttp 가 이미 그렇게
 * 처리하고, 오프라인일 땐 어차피 Room 캐시로 폴백한다). 동영상은 오프라인
 * 캐싱 대상이 아니라서(§13 결정 기록) 재생 시도 자체가 항상 네트워크를
 * 요구하는데, 그 시도(스트리밍 실패 → WebDAV 폴백 시도 → 에러)를 오프라인
 * 상태에서도 매번 몇 초씩 기다렸다가 알아보기 힘든 에러 코드로 끝나던
 * 문제를 막으려고, 시도 전에 미리 스냅샷으로 확인한다.
 *
 * 실시간 감시(`ConnectivityManager.NetworkCallback`)는 아니다 — 호출 시점의
 * 상태만 본다. 이걸로 충분한 이유: 재생을 다시 시도하는 "다시 시도" 버튼이
 * 이미 있어서, 오프라인→온라인 전환은 사용자가 그 버튼을 눌러 확인하면 된다.
 */
fun Context.hasActiveNetwork(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return true // 못 물어보면 낙관적으로 "있다"고 가정한다 — 실제 재생 시도가 최종 판단자다.
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
