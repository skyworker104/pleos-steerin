# 후방 카메라 위 팝업 검토 (2026-11-08, Pleos_Connect_v2.1 AVD 실측)

## 질문
R 기어에서 시스템 후방 카메라(`ai.umos.drivingview`)가 앱 영역을 점유한다. 그 위에 Ghost Ring을 잠깐 팝업처럼 띄울 수 있는가?

## 실측 결과 요약

| 방법 | 에뮬레이터 동작 | 실차/앱 마켓 배포 가능성 | 판단 |
| :-- | :-- | :-- | :-- |
| **A. 헤즈업 알림** (`NotificationChannel` IMPORTANCE_HIGH, CATEGORY_NAVIGATION) | **동작.** 카메라 화면 상단에 "나는야 주차왕 / 핸들을 오른쪽 끝까지 돌린 뒤 천천히 후진하세요." 배너가 뜨고, 정렬 완료 시 "조향 정렬 완료"로 갱신됨. 수 초 후 자동 소멸, 문구가 바뀔 때마다 다시 뜸 | 표준 Android 알림. 별도 권한 없음(API 33+ POST_NOTIFICATIONS 런타임만). 심사 위험 낮음 | **채택.** 음성(Pleos TTS)과 함께 R 구간의 기본 채널 |
| **B. 오버레이 창** (`TYPE_APPLICATION_OVERLAY` + SYSTEM_ALERT_WINDOW) | **동작.** 카메라 우상단에 Ghost Ring 팝업(라이브 핸들·목표 링·정렬 시 녹색)이 그려짐. 단 `appops set --user 10 … SYSTEM_ALERT_WINDOW allow`를 adb로 준 뒤에만 가능 | **불가.** 이미지에 "다른 앱 위에 표시" 설정 화면이 없어 운전자가 권한을 줄 방법이 없음(`MANAGE_OVERLAY_PERMISSION` 액티비티 없음). 앱 마켓 심사에서도 허용될 가능성 낮음 | **디버그 빌드 전용 실험으로 보관.** release 매니페스트에는 권한 미선언 → 자동 비활성 |
| C. Picture-in-Picture | 불가 (`android.software.picture_in_picture` 기능 없음) | — | 제외 |
| D. 클러스터(좌측) 영역 | 별도 디스플레이가 아니라 같은 디스플레이 위의 시스템 오버레이(홈 앱, 853px). 서드파티 접근 경로 없음 | — | 제외 |

## 추가로 드러난 플랫폼 제약 (중요)
- 카메라가 뜨지 않은 상태에서 R을 넣었을 때 우리 앱 위에 **"주행 중에는 사용할 수 없습니다"** 차단 화면(`ActivityBlockingActivity`)이 표시되었다. CarPackageManagerService 허용 목록이 시스템 앱만 포함하고, `distractionOptimized` 메타데이터를 선언한 서드파티 액티비티는 허용되지 않는다(`mEnableActivityBlocking: true`).
- 즉 Pleos Connect에서는 **기어가 P를 벗어나면 서드파티 액티비티 화면 자체가 보이지 않는다.** 카메라 점유 여부와 무관하게, R 구간 코칭은 음성 + 헤즈업 알림으로만 전달할 수 있다. Ghost Ring 화면은 P(대기·결과·리플레이)와 "정차 상태에서 핸들을 미리 돌리는 준비 단계"가 실제 가치 구간이다.
- 이 제약은 공모전 PT에서 먼저 언급하는 편이 유리하다. "플랫폼 정책을 실측으로 확인했고, 그래서 음성 우선 + 알림 + P 복귀 결과 카드로 설계했다"는 근거가 된다.

## 구현 상태
- `presentation/overlay/CoachNotifier.kt`: 단계 전환·정렬 완료 시 헤즈업 알림 갱신, P 복귀 시 제거. 액티비티가 전면이 아닐 때만 동작.
- `presentation/overlay/GhostOverlayWindow.kt`: 실험용 오버레이(일반 View로 그린 Ghost Ring, 터치 비소비, 4초 후 자동 숨김). `app/src/debug/AndroidManifest.xml`에서만 권한 선언.
- 에뮬레이터에서 실험하려면: `adb shell appops set --user 10 com.steerin.parkingmaster.debug SYSTEM_ALERT_WINDOW allow`

## 증빙
- `docs/screenshots/09_hun_over_camera.png` — 헤즈업 알림 + (디버그) 오버레이 링, 준비 단계
- `docs/screenshots/10_hun_snapped_over_camera.png` — 정렬 완료 상태 갱신
- `docs/screenshots/11_blocked_while_driving.png` — 서드파티 액티비티 차단 화면

## 권고
1. 제출본(release)은 A만 사용한다. 헤즈업 문구는 음성과 동일한 한 줄 지시문으로 유지한다.
2. B는 Pleos 측에 "주차 보조 카테고리 앱의 R 기어 중 오버레이 허용" 요청 근거 자료로 활용한다(PT Q&A 대비).
3. 결과 카드·리플레이·이력 등 시각 요소는 P 상태에 집중한다(현재 구현과 일치).
