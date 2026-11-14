# 나는야 주차왕 (pleos-steerin)

> **후진 주차 과정을 차량 신호로 채점하고, Ghost Ring Snap-In 가이드로 코칭하는 Pleos Connect 앱**
> 현대자동차그룹 차량용 앱 개발 공모전 2026 출품작 · 일반인 부문 · Pleos Connect(Android Automotive OS) 앱

## 📌 심사 필수 항목

### ① 앱 이름 · 한 줄 소개
| 항목 | 내용 |
| :-- | :-- |
| 앱 이름 | **나는야 주차왕** (Parking Master) · 팀 6학년5반 |
| 한 줄 소개 | R 기어부터 P 기어까지 조향각·기어·차속·브레이크·도어 신호로 후진 주차를 코칭하고 100점 만점으로 채점하는 Pleos Connect 앱 |

### ② 실행 방법
```bash
adb install -r apk/6학년5반_나는야주차왕_release.apk
adb shell am start -n com.steerin.parkingmaster/.presentation.MainActivity
```
1. 처음 실행하면 뜨는 **차량 데이터 권한 창에서 허용**을 누릅니다. 상단 칩이 **"Pleos Vehicle SDK"** 로 바뀌면 연결된 것입니다.
2. Virtual Console(`http://127.0.0.1:8088/`) → Emulation Controls → **Motion** 에서 기어를 **R** 로 바꾸면 코칭이 시작됩니다.
3. 조향각·차속을 바꿔 가며 후진한 뒤 기어를 **P** 로 바꾸면 100점 결과 카드가 표시됩니다.
4. 대기 화면에서 **핸들 스킨**, **주차 이력**, **다시 보기(리플레이)** 를 확인할 수 있습니다.
* Vehicle Service가 없는 일반 Android 기기·에뮬레이터에서는 **"시뮬레이션 모드"** 로 실행되며, 우측 데모 패널의 **"모범 주차 재생"** 으로 전체 흐름을 볼 수 있습니다.
* 소스 빌드와 AVD 스크립트는 [4. 실행 방법](#4-실행-방법)에 있습니다.

### ③ 테스트 환경 · 필요한 설정
| 항목 | 내용 |
| :-- | :-- |
| 검증 환경 | Pleos Connect 에뮬레이터 `Pleos_Connect_v2.1` (Android 14, 2560×1440, Vehicle Service 2.3.22) · 일반 Android 에뮬레이터 (시뮬레이션 모드) |
| 최소 요구 | Android API 28 이상 (compileSdk/targetSdk 35) · Pleos Vehicle SDK 2.3.2 (Vehicle Service 2.3.13 이상) |
| 빌드 도구 | JDK 17 · Gradle 8.10.2 · AGP 8.8.2 · Kotlin 2.0.21 |
| 필요한 권한 | 차량 데이터 `pleos.car.permission.READ_CAR_STEERING · CAR_DRIVE · CAR_SPEED · CAR_DOORS · CAR_SAFETY · CAR_INFO`, 음성 `TTS_SERVICE`, `POST_NOTIFICATIONS` (헤즈업 알림) — 첫 실행 시 허용 |
| Virtual Console | 에뮬레이터 재부팅 후 `adb forward tcp:8088 tcp:8088` 실행 → 브라우저에서 `http://127.0.0.1:8088/` |
| 네트워크·외부 연동 | 필요 없음 (모든 처리와 기록 저장은 차량 내부에서만) |
| (선택) 카메라 위 핸들 카드 | 디버그 빌드에서만 동작합니다. `adb shell appops set --user 10 com.steerin.parkingmaster.debug SYSTEM_ALERT_WINDOW allow` |

### ④ APK 위치
* 저장소 경로: [`apk/6학년5반_나는야주차왕_release.apk`](apk/6%ED%95%99%EB%85%845%EB%B0%98_%EB%82%98%EB%8A%94%EC%95%BC%EC%A3%BC%EC%B0%A8%EC%99%95_release.apk) (release 서명본, 약 40 MB)
* 바로 받기: [다운로드](https://github.com/skyworker104/pleos-steerin/raw/main/apk/6%ED%95%99%EB%85%845%EB%B0%98_%EB%82%98%EB%8A%94%EC%95%BC%EC%A3%BC%EC%B0%A8%EC%99%95_release.apk)

| 기타 | 내용 |
| :-- | :-- |
| 플랫폼 | Pleos Connect (Android Automotive OS) · Pleos Vehicle SDK 2.3.2 · Kotlin · Jetpack Compose |
| 데이터 경로 | **온디바이스** Vehicle SDK + AAOS Car API. 클라우드 API, GPS, 카메라, 초음파 센서를 사용하지 않습니다. |

### 🎬 시연 영상
[![나는야 주차왕 시연 영상 (3분 7초)](docs/video/demo_thumbnail.jpg)](docs/video/6%ED%95%99%EB%85%845%EB%B0%98_%EB%82%98%EB%8A%94%EC%95%BC%EC%A3%BC%EC%B0%A8%EC%99%95_%EC%8B%9C%EC%97%B0%EC%98%81%EC%83%81.mp4)

**▶ [시연 영상 보기 (3분 7초, 1080p)](docs/video/6%ED%95%99%EB%85%845%EB%B0%98_%EB%82%98%EB%8A%94%EC%95%BC%EC%A3%BC%EC%B0%A8%EC%99%95_%EC%8B%9C%EC%97%B0%EC%98%81%EC%83%81.mp4)** · [mp4 다운로드](https://github.com/skyworker104/pleos-steerin/raw/main/docs/video/6%ED%95%99%EB%85%845%EB%B0%98_%EB%82%98%EB%8A%94%EC%95%BC%EC%A3%BC%EC%B0%A8%EC%99%95_%EC%8B%9C%EC%97%B0%EC%98%81%EC%83%81.mp4) · [SRT 자막](docs/video/6%ED%95%99%EB%85%845%EB%B0%98_%EB%82%98%EB%8A%94%EC%95%BC%EC%A3%BC%EC%B0%A8%EC%99%95_%EC%8B%9C%EC%97%B0%EC%98%81%EC%83%81.srt)

Pleos Connect 에뮬레이터에서 현재 빌드를 실제로 구동해 녹화했습니다. 대기 화면·핸들 스킨 → 모범 주차(후방 카메라 하단 핸들 카드, 90점) → 초보 주차(과속 경고·재시도, 37점) → 주차 이력·세션 리플레이 순서입니다.

---

## 1. 기획 배경: 후진 주차 · 게임화 · 넛지(Nudge)

후진 주차는 초보 운전자가 가장 어려워하는 상황 중 하나입니다. 핸들을 어느 쪽으로 얼마나 돌려야 하는지 헷갈리는데, 기존 주차 보조는 경고음이나 "우측 15도 회전" 같은 숫자 지시로 **위험만 알릴 뿐 지금 핸들을 어떻게 해야 하는지는 알려 주지 않습니다.** 스마트폰 앱은 핸들이 얼마나 돌아가 있는지 알 수 없으므로 이 문제를 풀 수 없습니다.

Pleos Connect 앱은 차량이 이미 알고 있는 **조향각, 기어, 차속, 브레이크, 도어 상태** 를 실시간으로 읽을 수 있습니다. 나는야 주차왕은 이를 이용해 주차 스트레스를 **게임 퀘스트를 깨는 재미** 로 바꿉니다.

1. **넛지 인터랙션**: 숫자 대신 화면 속 목표 잔상 링에 지금 핸들을 **포개기(Snap-In)** 만 하면 되는 한 가지 행동으로 다음 조작을 유도합니다.
2. **과정 채점**: 급조향, 재시도, 과속, 급제동, 안전 상태, 소요 시간을 객관적으로 100점 만점으로 채점합니다.
3. **보상 루프**: 목표를 달성할 때마다 새 **핸들 스킨** 을 얻고 캐릭터 **'휠리'** 가 응원해, 운전자가 스스로 반복 연습하도록 이끕니다.

---

## 2. 주요 기능

### 2.1 Ghost Steering Guide (잔상 링 Snap-In)
* 화면에 **목표 조향각의 잔상 링(Pleos Cyan)** 과 **현재 핸들(흰색/코랄)** 을 1:1 각도로 그립니다.
* 두 링이 허용 오차 안에서 포개지면 현재 핸들이 **녹색** 으로 바뀌고 짧은 확인음이 납니다.
* 목표 조향각은 **운동학 자전거 모델** 로 계산합니다. 조향각과 차속을 적분해 "차가 얼마나 회전했는가"를 추정하고, 직각 후진 주차 표준 절차(풀 록 → 65° 회전 시 풀기 시작 → 80° 또는 핸들을 바로 세운 시점에 직진)에 따라 단계별 목표각을 냅니다. 절대 위치가 아니라 **상대 회전량** 을 쓰므로 GPS나 주차 칸 정보가 필요 없습니다.

### 2.2 100점 주차 과정 평가
모든 항목이 실제로 수신 가능한 신호 위에 있습니다. 상세 수식은 [`docs/SCORING.md`](docs/SCORING.md).

| 항목 | 배점 | 신호 | 측정 |
| :-- | :--: | :-- | :-- |
| 조향 안정성 | 35 | 조향각 | 조향 방향 반전 횟수(1회 무료), 이동 중 최대 조향 각속도 |
| 재시도 횟수 | 25 | 기어 | R↔D 전환 횟수 |
| 속도·제동 | 20 | 차속, 브레이크 | R 기어 최고 속도(5 km/h 초과분), 급제동 횟수 |
| 안전 상태 | 10 | 도어/트렁크 | 후진 중 열림 감지 시 0점 |
| 소요 시간 | 10 | 기어 타임스탬프 | 첫 R → P 체결, 45초 초과분 |

결과 카드는 **P 기어 체결 후에만** 표시되며, 가장 많이 감점된 항목에 대한 한 줄 코칭을 함께 보여 줍니다.

### 2.3 Driving UX / 안전
* 상태 머신: `DRIVING`(최소 UI, 터치 없음) → `COACHING`(링 + 한 줄 지시, 읽기 전용) → `RESULT`(P 체결 시에만, 터치 허용).
* `CarUxRestrictionsManager` 를 구독해 주행 제한 상태에서는 데모 패널과 보조 텍스트를 숨깁니다.
* **R 기어 구간의 전달 채널**: Pleos Connect는 기어가 P를 벗어나면 서드파티 액티비티를 차단하고(실측), 시스템 후방 카메라가 화면을 점유합니다. 그래서 코칭은 **음성(Pleos TTS) + 헤즈업 알림**(카메라 위 한 줄 배너, 단계 전환·정렬 완료 시 갱신)으로 전달하고, 화면(링·결과·이력·리플레이)은 P 상태에 집중합니다. 디버그 빌드에서는 후방 카메라 화면의 **하단 가운데 빈 영역**에 Ghost Ring 핸들 카드를 띄웁니다(오버레이 권한이 필요해 release 빌드에는 넣지 않았습니다). 검토 기록: [`docs/CAMERA_POPUP_REVIEW.md`](docs/CAMERA_POPUP_REVIEW.md)

| 헤즈업 알림 (카메라 위, 준비 단계) | 하단 핸들 카드 · 정렬 완료(녹색) | R 기어 전체 화면 (클러스터 + 후방 카메라 + 핸들 카드) |
| :--: | :--: | :--: |
| ![headsup](docs/screenshots/v3_2026-11-14/09_headsup_over_camera.png) | ![card snapped](docs/screenshots/v3_2026-11-14/07_overlay_ghost_ring_snapped.png) | ![r gear](docs/screenshots/v3_2026-11-14/10_rgear_fullscreen_wheel.png) |
* **음성 우선**: 단계 전환 시 음성으로 안내해 시선은 거울과 후방 카메라에 두도록 합니다. Pleos Connect에서는 **Pleos TextToSpeech SDK(ON_DEVICE 모드)** 로, 그 외 기기에서는 Android TTS로 재생합니다. R 기어에서 시스템 후방 카메라 앱이 화면을 점유하므로 음성이 주 채널이고 링은 확인용입니다.
* UX 라이팅은 현대차그룹 3원칙(정확성·일관성·간결성)을 따릅니다. 모든 문구는 `app/src/main/res/values/strings.xml` 에 있습니다.

### 2.4 주차 이력과 세션 리플레이
* P 체결로 평가가 끝나면 세션(조향각·차속·기어·브레이크·도어 샘플)이 **차량 내부 저장소**에 자동 저장됩니다(최근 50건, 외부 전송 없음).
* 대기 화면의 **주차 이력** 버튼 → 기록 수·평균·최고 점수와 세션별 점수·등급·항목 막대·지표를 보여 줍니다.
* **다시 보기**는 저장된 신호를 별도 트래커로 다시 흘려 보내 같은 Ghost Ring 코칭 화면과 결과 카드를 재생합니다(1x/2x/4x, 일시정지). 운전자가 "그때 어디서 핸들을 늦게 풀었는지"를 눈으로 복기할 수 있고, 음성은 재생하지 않습니다.
* 이력·리플레이는 정차(P) 상태에서만 열리며, 주행 중 R 기어가 들어오면 자동으로 코칭 화면으로 돌아갑니다.

| 주차 이력 (Pleos 에뮬레이터) | 세션 리플레이 (Pleos 에뮬레이터) |
| :--: | :--: |
| ![history](docs/screenshots/v3_2026-11-14/05_history.png) | ![replay](docs/screenshots/v3_2026-11-14/06_replay_neon_snapped.png) |

### 2.4.1 목표 달성 보상: 핸들 스킨 · 캐릭터 휠리 · 연속 기록
* **핸들 스킨 5종**: 클래식(기본) / 마스터 링(70점 이상 1회) / 카본 그립(주차 10회) / 네온 스트릭(70점 이상 5회 연속) / 주차왕 골드(90점 이상 3회). 해금 시 결과 카드에 배너가 뜨고, 대기 화면의 **핸들 스킨**에서 고릅니다. 획득 기록은 세션 파일과 따로 저장되어 이력을 지워도 유지됩니다(`data/reward/RewardStore`).
* **안전 원칙**: 스킨은 테두리·중앙·마커 모양만 바꿉니다. 코칭 중에는 모든 스킨이 같은 의미 색(파랑 목표·초록 정렬·빨강 경고)으로 그려지고, 금색·네온 장식 색은 정차 화면에서만 보입니다.
* **캐릭터 휠리**: 코드로 그린 자체 타이어 캐릭터. 대기 화면에서 인사·연속 연습일·다음 핸들 목표를, 결과 화면에서 점수별 표정과 한 줄 응원을 보여 줍니다. 재촉 알림은 보내지 않습니다.
* 판정 로직은 순수 Kotlin(`domain/reward/Rewards.kt`)이며 `RewardEngineTest`로 검증합니다.
* 핸들 중앙은 현대자동차 엠블럼 형상(타원 + 기울어진 H)입니다(이슈 #4, 공모전 출품용). 공식 벡터 원본을 받으면 `WheelEmblem`만 교체하면 됩니다.

| 대기 화면 + 휠리 | 핸들 스킨 선택 | 새 스킨 획득 + 휠리 |
| :--: | :--: | :--: |
| ![standby](docs/screenshots/v3_2026-11-14/01_standby_skin_mascot.png) | ![skins](docs/screenshots/v3_2026-11-14/02_skin_picker.png) | ![unlock](docs/screenshots/v3_2026-11-14/03_result_unlock_neon_90.png) |

### 2.4.2 소셜 주차 랭킹 · 시즌 리그 (로드맵 · 미구현)
* 같은 차종 동호회, 지역(시·구), 가족·친구 단위의 주간·월간 랭킹 리그를 확장 기능으로 계획합니다.
* 점수를 공유하려면 운전자의 명시적 동의와 서버가 필요해 **이번 제출본에는 넣지 않았습니다.** 현재 모든 주차 기록은 차량 내부에만 저장됩니다.
* 구현하더라도 Google Design for Driving 기준에 따라 주행 중에는 리더보드를 숨기고, **P 기어 정차 시와 모바일 앱에서만** 보이게 합니다.
* 발표자료 9쪽의 구상:
  * **행동 경제학적 넛지**: 강제 제재 대신 게임 요소를 적용해, 포인트·보상으로 위험 운전 습관 개선을 유도합니다(TrypScore 등 연구 흐름 참고).
  * **소셜 확장**: 가족·동승자 간 로컬 리그, 지역별·차종별 랭킹.
  * **비즈니스 리워드 로드맵**: UBI(운전습관연계보험) 할인, 블루멤버스 포인트, 정비 쿠폰 연계(제휴 전제).

| 발표자료 9쪽 · 소셜 주차 랭킹 리그 (로드맵 예시 이미지, 미구현) |
| :--: |
| ![social ranking](docs/pt/slide09_social_ranking.png) |

### 2.5 주차 위치 점수 (SVM 연동 확장, 구현 완료)
* 과정 점수에 더해 "어디에 세웠는가"를 보는 **위치 점수 100점**(주차선 안 30 · 좌우 균형 30 · 앞뒤 깊이 20 · 평행 정렬 20)을 구현했습니다. 종합 점수는 과정 70% + 위치 30%입니다.
* 입력은 SVM이 이미 계산하는 **인식 결과 숫자**(좌·우·앞·뒤 간격, 기울기, 신뢰도)이며 영상은 쓰지 않습니다. 현재 Pleos는 이 데이터를 제공하지 않으므로 운영 기본값은 "위치 점수 없음"이고, SVM이 공개되면 데이터 소스 하나만 연결하면 됩니다.
* 대기 화면의 **SVM 위치 점수 시연** 스위치(기본 꺼짐)를 켜면 고정 예시값으로 결과 화면·이력을 보여 줍니다. 화면과 이력에 항상 "가상 데이터"로 표시됩니다.
* 화면 주기 캡처 방식은 개인정보·매번 동의·화면 구성 의존성 때문에 검토 후 배제했습니다. 상세: [`docs/SVM_POSITION_SCORE.md`](docs/SVM_POSITION_SCORE.md)

| 시연 스위치 (대기 화면, 기본 꺼짐) | 과정 85 + 위치 100 = 종합 90 (가상 데이터) |
| :--: | :--: |
| ![toggle](docs/screenshots/v3_2026-11-14/15_standby_classic.png) | ![model](docs/screenshots/v3_2026-11-14/03_result_unlock_neon_90.png) |

### 2.6 어디서나 시연 가능
* **Pleos Connect AVD / 실차**: Vehicle Service가 감지되면 자동으로 Vehicle SDK 경로를 사용합니다. Virtual Console의 Motion(기어·브레이크·조향각)·Convenience(트렁크) 카드로 신호를 넣을 수 있습니다.
* **그 외 모든 Android 기기/에뮬레이터**: Vehicle Service가 없으면 내장 시뮬레이터로 전환됩니다. "모범 주차", "초보 주차" 시나리오 재생과 수동 슬라이더를 제공하므로 동일한 데모 스크립트를 재현할 수 있습니다.

---

## 2.7 화면 (Pleos Connect 에뮬레이터 캡처, 2026-11-14 빌드)

코칭 화면은 P 상태의 세션 리플레이로 캡처했습니다. 실제 R 기어 주행 중에는 Pleos가 앱 화면을 가리고, 위 2.3의 후방 카메라 핸들 카드와 음성으로 안내합니다.

| 1단계 · 핸들 끝까지 (회전 준비) | 2단계 · 정렬 완료 (Snap-In) | 3단계 · 핸들 풀기 |
| :--: | :--: | :--: |
| ![turn in](docs/screenshots/v3_2026-11-14/11_coaching_turn_in.png) | ![snapped](docs/screenshots/v3_2026-11-14/12_coaching_snapped.png) | ![straighten](docs/screenshots/v3_2026-11-14/13_coaching_straighten.png) |

| 결과 (81점 · 안전 주차 마스터) | 초보 결과 (41점 · 휠리의 격려) | 대기 화면 (클래식 스킨) |
| :--: | :--: | :--: |
| ![result](docs/screenshots/v3_2026-11-14/14_result_process_81.png) | ![novice result](docs/screenshots/v3_2026-11-14/04_result_mascot_comfort_41.png) | ![standby](docs/screenshots/v3_2026-11-14/15_standby_classic.png) |

---

## 3. Pleos SDK / API 사용 명세

| 신호 | 경로 | API |
| :-- | :-- | :-- |
| 조향각 | Vehicle SDK `SteeringWheel` | `checkSteeringWheelAngleCapabilities` → `getCurrentSteeringWheelAngle` / `registerSteeringWheelAngle` |
| 기어 | Vehicle SDK `Gear` (실패 시 AAOS `GEAR_SELECTION` 폴백) | `getCurrentGearSelectionState` / `registerGearSelection` |
| 브레이크 페달 | Vehicle SDK `Brake` | `checkBrakePedalStatusCapabilities` → `registerBrakePedalStatus` |
| 도어/트렁크 | Vehicle SDK `Door` | `checkDoorPositionCapabilities` → `registerDoorPosition(DoorArea(REAR …))` |
| 차속 | AAOS `CarPropertyManager` | `PERF_VEHICLE_SPEED` (Pleos 공식 FAQ가 안내하는 클러스터 동일 소스) |
| 주행 제한 | AAOS `CarUxRestrictionsManager` | `onUxRestrictionsChanged` |
| 카메라 위 배너 | Android Notification (IMPORTANCE_HIGH, CATEGORY_NAVIGATION) | `CoachNotifier` — 액티비티가 전면이 아닐 때만 |
| 음성 안내 | Pleos TextToSpeech SDK 3.3.1 | `TextToSpeech(context, Mode.ON_DEVICE).initialize` → `setLocale(KOREA)` → `speak` |

권한: `pleos.car.permission.CAR_DRIVE`, `CAR_DOORS`, `CAR_SAFETY`, `CAR_INFO`, `TTS_SERVICE`, `android.car.permission.CAR_SPEED`(런타임), `CAR_POWERTRAIN`. (`READ_CAR_STEERING`·`CAR_SPEED`는 선언만 되어 있고 현재 이미지에는 정의되지 않은 권한으로 확인됨)

의존성: `ai.pleos.playground:Vehicle:2.3.2`, `ai.pleos.playground:TextToSpeech:3.3.1` (`https://nexus-playground.pleos.ai/repository/maven-releases/`).

---

## 4. 실행 방법

### 4.1 APK 설치 (심사용)
```bash
# Pleos Connect AVD(Pleos_Connect_v2.1) 또는 임의의 Android 기기/에뮬레이터(API 28+)
adb install -r apk/6학년5반_나는야주차왕_release.apk
adb shell am start -n com.steerin.parkingmaster/.presentation.MainActivity
```
* Pleos AVD에서 처음 실행하면 차량 데이터 권한 요청 창이 뜹니다. 허용 후 상단 칩이 **"Pleos Vehicle SDK"** 로 바뀝니다.
* Virtual Console → Motion 카드에서 기어를 **R** 로 바꾸면 코칭이 시작되고, **P** 로 바꾸면 결과 카드가 표시됩니다.
* Vehicle Service가 없는 기기에서는 상단 칩이 **"시뮬레이션 모드"** 로 표시되고 우측 데모 패널이 열립니다. **"모범 주차 재생"** 을 누르면 24초 뒤 결과 카드까지 자동 진행됩니다.

### 4.2 Pleos Connect 에뮬레이터에서 실행 (실측 완료)
```bash
scripts/pleos_avd.sh install   # 공식 가이드대로 Update Site 등록 → 시스템 이미지 설치 → AVD 생성 (RAM 4 GB, Cold boot)
scripts/pleos_avd.sh start     # Pleos_Connect_v2.1 기동 (Android Studio Device Manager에도 표시됨)
scripts/pleos_avd.sh deploy    # 디버그 빌드 설치·실행
adb forward tcp:8088 tcp:8088  # 브라우저에서 http://127.0.0.1:8088/ → Virtual Console
```
* Virtual Console → Emulation Controls → **Motion**(Gear Selection, Brake Pedal, Steering Angle)으로 R→주행→P 흐름을 만들면 코칭·채점이 동작합니다. 속도는 정수 km/h만 받습니다.
* 실측 사실: R 기어에서는 시스템 후방 카메라 앱이 앱 영역을 점유하고 P에서 자동 복귀합니다(결과 카드는 P에서 표시). 조향각은 콘솔 기준 부호가 반대라 앱에서 정규화했습니다.
* 시연 영상(에뮬레이터 화면 녹화, 해설·자막 포함): [`docs/video/6학년5반_나는야주차왕_시연영상.mp4`](docs/video/6%ED%95%99%EB%85%845%EB%B0%98_%EB%82%98%EB%8A%94%EC%95%BC%EC%A3%BC%EC%B0%A8%EC%99%95_%EC%8B%9C%EC%97%B0%EC%98%81%EC%83%81.mp4) · 초기 무음 녹화: [`pleos_connect_model_parking.mp4`](docs/video/pleos_connect_model_parking.mp4)

### 4.3 소스 빌드
```bash
git clone https://github.com/skyworker104/pleos-steerin.git
cd pleos-steerin
./gradlew :app:testDebugUnitTest     # 채점 엔진·운동학 모델·상태 머신 단위 테스트
./gradlew :app:assembleRelease       # signing/signing.properties 가 있으면 서명본, 없으면 unsigned
```

### 4.4 테스트 환경
| 항목 | 버전 |
| :-- | :-- |
| JDK | 17 |
| Gradle / AGP / Kotlin | 8.10.2 / 8.8.2 / 2.0.21 (Pleos 공식 샘플 앱 기준선과 동일) |
| compileSdk / minSdk | 35 / 28 |
| Pleos Vehicle SDK | 2.3.2 (Vehicle Service 2.3.13 이상) |
| 검증 환경 | Pleos Connect 에뮬레이터 `Pleos_Connect_v2.1`(Android 14, Vehicle Service 2.3.22, CaaS 4.2.10) · 일반 Android 에뮬레이터(API 37, 시뮬레이션 모드) |

---

## 5. 프로젝트 구조

```
app/src/main/java/com/steerin/parkingmaster/
 ├── data/outcome/ParkingOutcomeSource.kt   # 주차 위치 데이터 소스 (SVM 자리 · 미제공 · 시연용)
 ├── data/session/SessionRepository.kt      # 세션 파일 저장소 (filesDir/sessions)
 ├── data/vehicle/
 │    ├── VehicleSignal.kt                 # 정규화된 신호 모델
 │    ├── VehicleSignalSource.kt           # 데이터 소스 인터페이스
 │    ├── PleosVehicleSignalSource.kt      # Pleos Vehicle SDK 연동 (조향각·기어·브레이크·도어)
 │    ├── AaosCarSignalSource.kt           # AAOS CarPropertyManager (차속, 기어 폴백)
 │    └── SimulatedVehicleSignalSource.kt  # 시나리오 재생기 / 수동 시뮬레이터
 ├── domain/
 │    ├── kinematics/BicycleModel.kt       # 운동학 자전거 모델 (상대 방위각 추정)
 │    ├── coaching/GhostTargetPlanner.kt   # 단계별 목표 조향각 (READY→TURN_IN→STRAIGHTEN→HOLD)
 │    ├── scoring/ParkingScoreEvaluator.kt # 100점 과정 채점 엔진
 │    ├── outcome/  SlotObservation · SlotOutcomeEvaluator  # 위치 점수·종합 점수
 │    └── session/  ParkingSessionTracker(R→P 세션·상태 머신) · SessionCodec(텍스트 저장 포맷) · SessionReplayer
 └── presentation/
      ├── ParkingViewModel.kt / ReplayController.kt / MainActivity.kt
      ├── ui/  GhostRing · CoachingScreen · ResultScreen · StandbyScreen · HistoryScreen · ReplayScreen · DemoPanel
      ├── overlay/  CoachNotifier(헤즈업 알림) · GhostOverlayWindow(디버그 실험)
      ├── ux/UxRestrictionsMonitor.kt      # CarUxRestrictions 연동
      └── voice/  VoiceCoach · PleosTtsEngine   # Pleos TTS SDK(ON_DEVICE) / Android TTS 폴백 + Snap-In 확인음
app/src/test/…                             # 단위 테스트 50건 (채점·위치 채점·모델·플래너·세션·코덱/리플레이·음성 스케줄러·차량 소스)
docs/                                      # SCORING.md, DEMO_SCRIPT.md, SVM_POSITION_SCORE.md, PT_SVM_ADDENDUM.md, 분석 보고서
apk/                                       # 제출용 release APK
```

---

## 6. 로드맵

* **지금 (공모전 제출본)**: 위 기능 전체. 12.3인치 와이드 레이아웃, 시뮬레이션 모드 포함.
* **SVM 공개 시**: 위치 점수 활성화(구현 완료, 데이터 소스 연결만 필요), 실시간 위치 기반 음성 코칭.
* **PT 이후**: CarInfo 제원 기반 축거 자동 보정, 목표 점수 설정, 실차에서 조향각 부호·콜백 주기 재확인.
* **동의·서버 확보 후**: 소셜 주차 랭킹·시즌 리그([2.4.2](#242-소셜-주차-랭킹--시즌-리그-로드맵--미구현)), 주행 중 비표시.
* **상용화 검토 단계**: 블루멤버스 포인트·정비 쿠폰·UBI 보험 연계는 **제휴가 전제된 로드맵** 입니다. 본 제출본은 앱 내 핸들 스킨·휠리 보상까지만 구현하며, 운전 데이터의 외부 제공은 별도의 명시적 동의 설계가 필요합니다.

## 7. 라이선스 / 고지
* 소스: MIT. Pleos Vehicle SDK는 Pleos Playground 이용 약관을 따릅니다.
* release 서명 키는 저장소에 포함하지 않습니다. 재빌드 방법과 제출 APK의 인증서 지문은 `signing/README.md` 에 있습니다.
