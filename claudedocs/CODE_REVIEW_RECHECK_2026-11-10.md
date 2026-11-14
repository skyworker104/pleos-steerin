# 코드 리뷰 재점검 (2026-11-10)

- 근거: `docs/나는야 주차왕(pleos-steerin) 코드 리뷰 결과1.pdf` (2026-11-09, 커밋 b1acfdd 기준)
- 대상: 현재 HEAD `b1acfdd` (리뷰 이후 코드 변경 없음 → 줄 번호 그대로 유효)
- 방법: 지적된 파일·줄을 직접 열어 대조, float 반올림은 float32로 재현, 매니페스트는 **병합본**(`app/build/intermediates/merged_manifests/debug/...`)까지 확인
- 한계: 빌드·단위 시험·AVD 실행은 하지 않음. 코드는 수정하지 않음

판정: ✅ 유효(수정 권장) · 🟡 유효하나 영향 작음/의도 판단 필요 · ❌ 오탐 · ⏸ 잠재(현재 호출 경로 없음)

---

## 1. "출시 전 수정 권장" 7건

| # | 위치 | 판정 | 재점검 메모 | 개발자 답변(제안) |
|---|---|---|---|---|
| 1 | `ParkingViewModel.kt:190-213`, `CoachingScreen.kt:117-121` | ✅ | `emitVoiceFor`는 단계 전환·스냅만 음성으로 냄. 도어·과속은 `Warnings()` 화면 표시뿐이고, 헤즈업(`MainActivity.kt:60-64`)도 단계 문구만 보냄. R에서는 시스템 후방 카메라가 앞에 있으므로 경고가 운전자에게 사실상 안 감 | 수정 |
| 2 | `ParkingScoreEvaluator.kt:110`, `:138` | ✅ **(리뷰보다 심각)** | 속도(AAOS ~10 Hz)·조향 폴링(5 Hz)·브레이크 신호가 섞여 들어와 인접 샘플 간격이 50 ms 미만인 경우가 흔함. 조향 변화가 담긴 샘플 직전 50 ms 안에 속도 샘플이 있으면 그 구간이 버려지고, 다음 구간은 조향 변화가 0 → **조향 점프 자체가 사라짐**. 급제동도 같은 구조. 실차/AVD에서 각속도·급제동 감점이 체계적으로 과소 측정됨 | 수정 (기준 샘플 유지 방식) |
| 3 | `PleosVehicleSignalSource.kt:111-120` | ⏸ | 현상은 맞음. 다만 `start()`는 ViewModel의 `started` 가드로 1회만 호출되어 재시도 경로 자체가 없음. 추가로 `stop()`은 앱 어디서도 호출되지 않음(아래 E1) | 수정(1줄, 비용 없음) |
| 4 | `ReplayController.kt:45-56`, `:74-76` | ✅ | `play()`가 매번 새 트래커를 만들고 `signals[0]`부터 순회 → 일시정지 후 재생 시 처음부터 재생 | 수정 |
| 5 | `SteerinApp.kt:112-120` | ✅ | 토글 노출 조건이 `!ui.uxRestricted`뿐, COACHING 여부 미검사. 명세 7장 위반 | 수정(조건 1개 추가) |
| 6 | `SlotOutcomeEvaluator.kt:66` | ✅ 재현됨 | float32: `94*0.7f = 65.799995`, `19*(1-0.7f) = 5.7000003`, 합 `71.49999237` → `roundToInt()` = **71** (명세 72) | 수정 (정수 연산) |
| 7 | `SlotOutcomeEvaluator.kt:40` | ✅ | `minGap > -10` → 정확히 −10 cm는 0점. 같은 파일 KDoc 표(12줄 "> 10 cm 침범 시 0점")와도 모순 | 수정 (`>=`) |

## 2. "확인 필요" 7건

| 위치 | 판정 | 재점검 메모 | 개발자 답변(제안) |
|---|---|---|---|
| `ParkingSessionTracker.kt:211` UNKNOWN에서 결과 유지 | 🟡 | 코드가 `PARK \|\| UNKNOWN`. 주석(210줄)은 "only while parked". 신호 끊김 대비라면 의도로 보고 주석·README에 예외 명시 | 의도된 동작(문서 보강) |
| `PleosVehicleSignalSource.kt:278-280` `<queries>` 누락 | ❌ **오탐** | 소스 매니페스트에는 없지만, SDK AAR이 병합 매니페스트에 `<queries>`로 `VEHICLE_START`, `SPEECH_START` 둘 다 넣어 줌 (병합본 36-43줄 확인). 패키지 가시성 문제 없음 | 의도된 동작(SDK 병합으로 충족) |
| `ParkingSessionTracker.kt:150-156` 같은 ms 덮어쓰기 | 🟡 | 동시 도착 신호를 한 샘플로 합치려는 설계. 실제 기어는 D→N→R로 바뀌어 같은 ms D→R은 사실상 불가 | 의도된 동작 |
| `SessionReplayer.kt:23-27` 기어 먼저 송신 | ✅ | P 샘플에서 기어가 먼저 가면 세션이 즉시 종료되어 같은 ms의 브레이크·도어·조향 변화가 기록에서 빠짐. 기어를 마지막에 보내도 R 시작 샘플은 스냅샷이 먼저 갱신된 뒤 기록되므로 안전 | 수정(줄 순서 이동) |
| `SessionRepository.kt:46-51`, `SessionCodec.kt:55-80` | 🟡 | 직접 쓰기, 잘린 파일도 수용 확인. ID 충돌은 같은 ms 2회 저장이라 현실적으로 불가. 쓰기 중 종료 시 샘플이 잘린 기록이 정상처럼 보일 수 있음 | 보류 또는 임시파일+rename(소규모) |
| `ReplayController.kt:59` 2초 상한 | 🟡 | 확인. 데모 목적이면 유지 | 의도된 동작 |
| `PleosVehicleSignalSource.kt:252-255` 가용 표시 유지 | 🟡 | `fail()`은 detail만 갱신. `stop()` 미호출이라 후반부는 잠재 | 보류(상태 표시만 영향) |

## 3. "검토자 1인 제기·미확인" 7건 → 코드 대조 결과

| 항목 | 판정 | 메모 |
|---|---|---|
| 리플레이 로드 중 주행 제한 → 재생 화면 복귀 (`ParkingViewModel.kt:142-148`) | ✅ 낮음 | 로드 후 `uxRestricted` 재확인 없음. 로드 후 체크 1줄로 해결 |
| 헤즈업이 UI 상태 변화 시에만 갱신 (`MainActivity.kt:55-70`) | ✅ 중간 | lifecycle 변화가 트리거가 아님. R 진입 시 기어 신호가 Activity가 아직 RESUMED일 때 처리되면 배너가 안 뜨고, 정지 상태면 다음 신호까지 지연. `combine(ui, lifecycle.currentStateFlow)`로 해결 |
| 음성 SharedFlow replay=0 (`ParkingViewModel.kt:80`) | 🟡 낮음 | `configChanges`로 회전 재생성이 막혀 있고 수집은 onCreate~onDestroy. 실영향 미미 |
| Pleos TTS 초기화 실패 시 대기 문구 유실 (`PleosTtsEngine.kt` / `VoiceCoach.kt`) | ✅ 낮음 | `onFailure`에서 `pending` 폐기. 첫 안내 1건 유실 가능 |
| 대기 화면 안내문 노출·이력 버튼 P 미검사 (`StandbyScreen.kt`) | 🟡 의도 | 이력 버튼은 이미 `!uxRestricted` 블록 안. 안내문은 텍스트뿐 |
| 디버그 오버레이 / SYSTEM_ALERT_WINDOW | ❌ 문제 없음 | 권한은 `app/src/debug/AndroidManifest.xml`에만 있음. 릴리스에서는 `canDrawOverlays()=false`로 no-op |
| `stop()`과 비동기 등록 경합 | ⏸ | 콜백에 `started` 검사 없음은 맞지만 `stop()` 호출처 없음 |

## 4. 명세·문서 불일치 (4건 모두 확인)

| 항목 | 확인 내용 | 조치 |
|---|---|---|
| 조향 부호 | 명세 43줄 "SDK 부호 그대로" ↔ 코드 `STEERING_SIGN = -1f` | 명세 수정 |
| 기어 우선순위 | 명세 54줄 Pleos 우선 ↔ 코드 181줄 AAOS 우선 | 명세 수정 |
| 폐기 속도 경계 | `SCORING.md:16` "이상" ↔ 코드 `>` | SCORING.md "초과" |
| 대기 안내문 | `standby_hint`에 "이동 있음·2초 이상" 조건 없음 | 시연 대본 반영 또는 문구 보강 |

## 5. 리뷰에 없던 추가 발견

- **E1 ⏸** `PleosVehicleSignalSource.stop()`은 앱 어디서도 호출되지 않음(리스너·폴링 코루틴은 프로세스 수명 동안 유지). 또 `initialize()` 실패 후 `started=true`로 남은 상태에서 `stop()`이 불리면 244줄 `vehicle.getDoor()`가 `runCatching` 밖이라 예외 가능. 3번 수정 시 함께 정리 권장.
- **E2** 2번 항목의 실제 영향이 리뷰 서술(“40 ms 간격 예시”)보다 넓음 — 신호 혼합으로 일반 주행에서도 발생 (위 표 참고). 수정 우선순위를 1순위로 올릴 것을 권장.
- **E3** `SlotOutcomeEvaluator` KDoc 표와 코드 경계 불일치 (7번과 같이 해결).

## 6. 시험 공백 (확인)

현재 시험 33개(6파일). 조향 각속도(220°/s) 시험은 `app/src/test`에 없음 — 리뷰 지적 맞음. 수정 시 함께 추가 권장:
1. <50 ms 간격이 섞인 샘플에서 각속도·급제동 검출 (2번 회귀)
2. 종합 71.5 → 72, −10 cm → 15점 경계 (6·7번)
3. 일시정지→재생 이어가기, P 샘플 다중 변화 기록의 저장→재생 점수 동일성 (4번, SessionReplayer)
4. 220°/s·5 km/h·45초·등급 50/70/90 경계, 과속·급제동 예시 정확값 6점

## 7. 권장 수정 순서

| 순서 | 항목 | 규모 |
|---|---|---|
| 1 | #2 50 ms 구간 처리 + 시험 | 소 (두 함수) |
| 2 | #1 도어·과속 음성/헤즈업 + 헤즈업 lifecycle 트리거 | 중 |
| 3 | #6, #7 점수 경계 + 시험 | 극소 |
| 4 | #4 리플레이 이어 재생, SessionReplayer 기어 순서 | 소 |
| 5 | #5 COACHING 토글 숨김, 리플레이 로드 후 제한 체크 | 극소 |
| 6 | #3/E1 start 실패 복구·stop 안전화, TTS 대기 문구 이관 | 소 |
| 7 | 명세 4곳 + SCORING.md 정정 | 문서 |

---

## 8. 조치 결과 (브랜치 `fix/code-review-recheck`)

| 항목 | 조치 |
|---|---|
| #1 도어·과속 경고 | 상승 에지마다 음성 1회(과속은 4.5 km/h 아래로 내려가야 재무장), 헤즈업 문구도 경고 우선. 과속 한계 5 km/h를 `ParkingViewModel.OVER_SPEED_KMH`로 통일 |
| 헤즈업 갱신 | `combine(ui, lifecycle.currentStateFlow)` — 화면 전환만으로도 배너 표시/해제 |
| #2 50 ms 구간 | 기준(anchor) 샘플 방식으로 각속도·급제동 계산. 모든 간격이 50 ms 이상이면 기존과 결과 동일 |
| #3 / E1 | 초기화 실패 시 `started=false`, 늦게 온 capability 응답 무시, `stop()`의 도어 해제 `runCatching`, 가용 플래그 초기화 |
| #4 리플레이 | 트래커·신호 인덱스 보존, 일시정지 후 이어 재생 |
| SessionReplayer | 기어 신호를 샘플의 마지막에 송신 |
| #5 | COACHING 중 상단 토글 숨김, 리플레이 로드 완료 시 제한·코칭·화면 재확인 |
| #6 / #7 | 종합 점수 정수 연산(`processWeightPct=70`), −10 cm 경계 포함 |
| TTS | Pleos TTS 초기화 실패 시 대기 문구를 Android TTS로 넘김 |
| 저장 | 임시 파일에 쓴 뒤 rename |
| 문서 | 개발 명세 43·54줄, SCORING.md 폐기 경계, 트래커 UNKNOWN 주석 |
| 보류 | `standby_hint` 문구(시연 대본에서 설명), 리플레이 2초 상한(데모 의도), `fail()` 시 가용 표시(상태 표시만 영향), 같은 ms 덮어쓰기(설계) |

시험: 33 → 41개, 전부 통과 (`./gradlew testDebugUnitTest`). AVD·실차 확인은 아직 안 함.
