# pleos-steerin 개발 명세서 (AI 코딩 콘텍스트용) v2

> **프로젝트**: `pleos-steerin` / 나는야 주차왕 (Parking Master)
> **패키지**: `com.steerin.parkingmaster`
> **플랫폼**: Pleos Connect (Android Automotive OS, API 28+), Pleos Vehicle SDK 2.3.2
> **목적**: Cursor / Claude / Copilot 등에 콘텍스트로 주입하는 구현 명세. v1(2026-11-08 이전)의 Vehicle Data API 기반 설계는 폐기되었다. 변경 사유는 `docs/ANALYSIS_2026-11-08.md` 참고.

## 0. v1 → v2 변경 요약 (반드시 숙지)

| v1 (폐기) | v2 (현행) | 이유 |
| :-- | :-- | :-- |
| Pleos Vehicle Data API (`api.pleos.ai/v1/vehicles/{vin}/…`, OAuth) | **Vehicle SDK**(온디바이스) + **AAOS Car API** | Vehicle Data API는 클라우드 REST, 유럽 한정, 차량 내 앱용이 아님 |
| `Vehicle.checkSupport()`, `VehicleProperty.*`, `SensorRate.*` | `Vehicle(context).initialize()` → `getSteeringWheel()` 등 도메인 객체 → `checkXxxCapabilities` / `getCurrentXxx` / `registerXxx` (비동기 `onComplete/onFailed`) | v1 API는 실존하지 않음 |
| GPS 중앙 정렬(10 cm/2점), 초음파 센서 경고 단계, 후방 카메라 궤적 | **과정 품질 채점**(조향 안정성·재시도·속도/제동·안전·시간) + **자전거 모델 상대 방위각** | GPS 정밀도 부족, PDW/카메라는 서드파티 미제공 |
| UBI 보험·정비 할인 "보상 체계" | 앱 내 등급/배지. 외부 보상은 로드맵 | 2인팀이 약속할 수 없는 제3자 혜택 |
| `Pleos_Connect_v2.1`을 SDK 버전으로 표기 | AVD 이름 `Pleos_Connect_v2.1`, SDK는 2.3.2 | 용어 정정 |

## 1. 기술 스택

* Kotlin 2.0.21, AGP 8.8.2, Gradle 8.10.2, JDK 17 (Pleos 공식 샘플 앱 기준선)
* compileSdk 35 / minSdk 28 / targetSdk 35
* Jetpack Compose (BOM 2024.12.01) + Material3, StateFlow/Coroutines, `useLibrary("android.car")`
* `ai.pleos.playground:Vehicle:2.3.2` — Maven `https://nexus-playground.pleos.ai/repository/maven-releases/` (인증 불필요)
* 테스트: JUnit4, 순수 Kotlin 도메인(안드로이드 의존 없음)

## 2. 계층 구조

```
Presentation  MainActivity · ParkingViewModel · Compose UI(GhostRing, Coaching, Result, Standby, DemoPanel)
              UxRestrictionsMonitor(CarUxRestrictionsManager) · VoiceCoach(TTS + 확인음)
      ▲ StateFlow<UiState>
Domain        ParkingSessionTracker(상태 머신 + 세션 기록) · GhostTargetPlanner · BicycleModel · ParkingScoreEvaluator
      ▲ Flow<VehicleSignal>
Data          VehicleSignalSource ← PleosVehicleSignalSource(+AaosCarSignalSource) | SimulatedVehicleSignalSource
```

`SteerinApplication`이 Vehicle Service 유무(`ai.pleos.playground.vehicle.VEHICLE_START` 서비스 조회)로 소스를 고른다.

## 3. 신호 모델과 SDK 매핑

```kotlin
sealed interface VehicleSignal { val timestampMs: Long
  data class SteeringAngle(val degrees: Float, …)   // + 우측, − 좌측 (SDK 부호 그대로; AVD에서 확인)
  data class GearChanged(val gear: GearPosition, …)  // PARK/REVERSE/NEUTRAL/DRIVE/UNKNOWN
  data class Speed(val kmh: Float, …)                // AAOS PERF_VEHICLE_SPEED(m/s) × 3.6
  data class BrakePedal(val pressed: Boolean, …)
  data class DoorState(val door: DoorSlot, val open: Boolean, …)
}
```

| 신호 | SDK 호출 | 권한 |
| :-- | :-- | :-- |
| 조향각 | `vehicle.getSteeringWheel().checkSteeringWheelAngleCapabilities({cfg,bounds->…},{e->…})` → `getCurrentSteeringWheelAngle` / `registerSteeringWheelAngle(SteeringWheelAngleListener)` | `pleos.car.permission.READ_CAR_STEERING` |
| 기어 | `vehicle.getGear().getCurrentGearSelectionState` / `registerGearSelection(GearSelectionListener{onSelected(VehicleGear)})` · 실패 시 `CarPropertyManager GEAR_SELECTION` | `pleos.car.permission.CAR_DRIVE` |
| 브레이크 | `vehicle.getBrake().checkBrakePedalStatusCapabilities` → `registerBrakePedalStatus` | `CAR_DRIVE` |
| 도어/트렁크 | `vehicle.getDoor().checkDoorPositionCapabilities({cfg, areaMap->…})` → `registerDoorPosition(DoorArea(DoorValue.REAR), …)` (REAR=트렁크) | `CAR_DOORS` |
| 차속 | `Car.createCar(ctx).getCarManager(Car.PROPERTY_SERVICE)` → `registerCallback(cb, VehiclePropertyIds.PERF_VEHICLE_SPEED, SENSOR_RATE_FAST)` | `android.car.permission.CAR_SPEED` (런타임) |

모든 리스너 인터페이스는 `VehicleListener.onFailed(Exception)`을 상속하므로 object 식으로 두 메서드를 구현한다. `release()`는 비동기 콜백 완료 후, Activity `onDestroy` 시점에 호출한다.

## 4. 세션·상태 머신 (`ParkingSessionTracker`)

```
DRIVING ──(gear=R)──▶ COACHING ──(gear=P, 이동 있음, ≥2s)──▶ RESULT ──(확인 또는 기어 이탈)──▶ DRIVING
                         │
                         └──(D & >15 km/h 4초 지속)──▶ 세션 폐기 → DRIVING
```

* 모든 신호마다 스냅샷을 샘플로 기록(`Sample(t, steer, speed, gear, brake, doorOpen)`).
* 매 신호마다 **직전 스냅샷**으로 자전거 모델을 `dt`만큼 적분한다(후진이면 속도 부호 음수).
* COACHING 상태는 `GhostTarget(phase, targetDeg, toleranceDeg, headingChangeDeg)`와 `snapped`를 들고 있다.

## 5. Ghost 목표각 (`GhostTargetPlanner`, `BicycleModel`)

* 기하: 축거 2.75 m, 조향비 14.5, 록 540°, 록 사용률 0.85(목표 459°).
* 요레이트 `ψ̇ = v / L · tan(δ)`, `δ = 조향각 / 조향비`. 회전량 = |ψ| 누적.
* 단계: `READY`(정차, 풀 록 목표, 허용 30°) → `TURN_IN`(이동 시작, 70°까지 풀 록 유지) → `STRAIGHTEN`(70→88° 선형 풀기, 허용 20°) → `HOLD_STRAIGHT`(0°, 허용 5°). 단계는 되돌아가지 않는다.
* Snap-In: `|live − target| ≤ tolerance`.

## 6. 채점 (`ParkingScoreEvaluator`)

`docs/SCORING.md`의 표와 동일. 임계값은 `Config` 데이터 클래스에 모여 있다. 결과 `ParkingScore(total, tier, categories[5], metrics)`; `weakestCategory`로 팁을 고른다.

## 7. UI 규격

* 레이아웃 기준 1920×720(12.3") 가로. 좌 42% 링, 우 58% 텍스트. 일반 폰 가로에서도 동작.
* 색: 배경 `#121418`, 표면 `#1B1F26`, Ghost `#00AAD2`(α .55), 정렬 `#00E676`, 미정렬 `#FF5252`, 주의 `#FFC107`.
* 타이포: 지시문 28 sp, 점수 96 sp, 칩 18/13 sp.
* 링은 조향각과 1:1 회전. 상단 마커 점으로 회전 방향이 보이게 한다. 허용 오차 대역은 Cyan 아크로 표시.
* COACHING에는 터치 요소를 두지 않는다. RESULT의 "확인" 버튼만 터치. `uxRestricted=true`면 데모 패널·보조 텍스트 숨김.

## 8. UX 라이팅 (strings.xml)

| 상황 | 문구 |
| :-- | :-- |
| READY(우측) | 핸들을 오른쪽 끝까지 돌린 뒤 천천히 후진하세요. |
| TURN_IN | 녹색 가이드에 맞춰 핸들을 유지하며 후진하세요. |
| STRAIGHTEN | 녹색 가이드에 맞춰 핸들을 천천히 풀어 주세요. |
| HOLD_STRAIGHT | 핸들을 바르게 하고 천천히 후진하세요. |
| Snap-In | 조향 정렬이 완료되었습니다. 이 각도를 유지하세요. |
| 도어 열림 | 도어 또는 트렁크가 열려 있습니다. 확인 후 진행하세요. |
| 과속 | 속도를 줄이세요. |
| 결과(음성) | 주차가 완료되었습니다. 평가 점수는 N점입니다. |

규칙: 한 문장 한 행동, 느낌표·감탄사·마케팅 형용사 금지, 계기판 용어(핸들·후진·기어) 사용.

## 9. 시뮬레이터 (`SimulatedVehicleSignalSource`)

* 50 ms 틱 가상 시계. 속도 하트비트를 매 틱 발행해 적분을 유지한다.
* `Scenario(id, keyframes)` 선형 보간. `MODEL_PARKING`(24 s, ≥90점), `NOVICE_PARKING`(42 s, <70점)은 단위 테스트가 보증한다.
* 수동 조작: 기어 P/R/N/D, 조향각 −540…540, 속도 0…20, 브레이크, 트렁크.

## 10. 빌드·제출

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease     # signing/signing.properties 로 서명
cp app/build/outputs/apk/release/app-release.apk apk/<팀명>_ParkingMaster_release.apk
```

## 11. 남은 작업 (우선순위)

1. Pleos AVD에서 조향각 콜백 주기·분해능 실측 → 허용 오차/히스테리시스 재보정.
2. 조향각 부호(+가 우측인지) AVD에서 확인 후 필요 시 `PleosVehicleSignalSource`에서 반전.
3. Pleos TextToSpeech SDK 공개 시 `VoiceCoach` 교체.
4. `CarInfo` 제원에서 축거 읽어 `VehicleGeometry` 자동 설정(현재 SDK 공개 API에는 치수 getter가 없어 기본값 사용).
5. 세션 로그 JSON 저장·리플레이, 주차 이력 화면.
