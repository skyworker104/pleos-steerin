// Generates the contest planning document (v6) as an editable .docx.
const fs = require("fs");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, WidthType, AlignmentType,
  HeadingLevel, BorderStyle, ShadingType, LevelFormat, PageBreak,
} = require("docx");

const OUT = process.argv[2];
const FONT = "Malgun Gothic";
const CYAN = "00AAD2";
const DARK = "121418";
const GREY = "5F6B7A";

const border = { style: BorderStyle.SINGLE, size: 4, color: "C9CED6" };
const borders = { top: border, bottom: border, left: border, right: border };

function p(text, opts = {}) {
  const runs = Array.isArray(text) ? text : [new TextRun({ text, size: opts.size || 20, bold: opts.bold, color: opts.color })];
  return new Paragraph({ children: runs, spacing: { before: opts.before ?? 60, after: opts.after ?? 60 }, alignment: opts.align, heading: opts.heading, numbering: opts.numbering });
}
function h1(text) { return new Paragraph({ text, heading: HeadingLevel.HEADING_1 }); }
function h2(text) { return new Paragraph({ text, heading: HeadingLevel.HEADING_2 }); }
function bullet(text, level = 0) {
  const runs = Array.isArray(text) ? text : [new TextRun({ text, size: 20 })];
  return new Paragraph({ children: runs, numbering: { reference: "bullets", level }, spacing: { before: 40, after: 40 } });
}
function b(t) { return new TextRun({ text: t, bold: true, size: 20 }); }
function r(t) { return new TextRun({ text: t, size: 20 }); }

function cell(text, { width, header = false, shade, bold = false, align } = {}) {
  const runs = Array.isArray(text) ? text : [new TextRun({ text: String(text), bold: header || bold, size: 18, color: header ? "FFFFFF" : undefined })];
  return new TableCell({
    width: { size: width, type: WidthType.DXA },
    borders,
    shading: header ? { type: ShadingType.CLEAR, fill: DARK, color: "auto" } : shade ? { type: ShadingType.CLEAR, fill: shade, color: "auto" } : undefined,
    margins: { top: 60, bottom: 60, left: 100, right: 100 },
    children: [new Paragraph({ children: runs, alignment: align, spacing: { before: 0, after: 0 } })],
  });
}
function table(widths, rows, { headerRows = 1 } = {}) {
  return new Table({
    width: { size: widths.reduce((a, c) => a + c, 0), type: WidthType.DXA },
    columnWidths: widths,
    rows: rows.map((cells, ri) => new TableRow({
      tableHeader: ri < headerRows,
      children: cells.map((c, ci) => cell(c, { width: widths[ci], header: ri < headerRows, shade: ri >= headerRows && ri % 2 === 0 ? "F4F6F8" : undefined })),
    })),
  });
}

const W = 9638; // usable width A4 with 2.54cm margins (11906 - 2*1134)

const children = [];

// ---- Title block
children.push(p([new TextRun({ text: "현대자동차그룹 차량용 앱 개발 공모전 기획서 (v6.0)", size: 22, color: GREY })], { after: 40 }));
children.push(p([new TextRun({ text: "나는야 주차왕 (Parking Master)", size: 40, bold: true, color: DARK })], { after: 40 }));
children.push(p([new TextRun({ text: "Pleos Vehicle SDK 실시간 차량 신호 기반 후진 주차 과정 평가 및 Ghost Ring 조향 코칭 앱", size: 22, color: CYAN, bold: true })], { after: 160 }));

children.push(table([2000, 2819, 2000, 2819], [
  ["공모전명", "현대자동차그룹 차량용 앱 개발 공모전 2026", "참가 구분", "일반인 부문"],
  ["출품작명", "나는야 주차왕 (Parking Master)", "분야", "스마트 드라이빙·안전 (Pleos Connect 앱)"],
  ["팀명 / 구성", "6학년5반 · 2인 (앱·SDK 개발 1, UI/UX 1)", "핵심 연동", "Pleos Vehicle SDK 2.3.2 + AAOS Car API"],
  ["저장소", "github.com/skyworker104/pleos-steerin", "제출물", "소스 코드, release APK, 시연 영상"],
], { headerRows: 0 }));
children.push(p("※ 심사 자료 익명성 지침에 따라 본 문서에는 성명·연락처 등 개인정보를 기재하지 않았습니다.", { size: 16, color: GREY, before: 80, after: 160 }));

// ---- 1
children.push(h1("1. 한 줄 소개"));
children.push(p([b("R 기어를 넣는 순간부터 P 기어까지, "), r("차량이 이미 알고 있는 조향각·기어·차속·브레이크·도어 신호로 후진 주차 과정을 100점으로 채점하고, 다음 순간 핸들을 둘 위치를 "), b("잔상 링(Ghost Ring)"), r("으로 보여 주는 후진 주차 코치입니다.")]));

// ---- 2
children.push(h1("2. 기획 배경 및 문제 정의"));
children.push(bullet([b("운전자의 문제: "), r("후진 주차는 초보 운전자가 가장 자주 실패하고 가장 오래 걱정하는 조작입니다. 기존 주차 보조(경고음·카메라)는 \"지금 위험하다\"만 알려 줄 뿐, 무엇을 어떻게 고쳐야 하는지 말해 주지 않습니다.")]));
children.push(bullet([b("기존 앱의 한계: "), r("스마트폰 앱은 핸들이 얼마나 돌아가 있는지, 지금 기어가 R인지 알 수 없습니다. 이 문제는 차량 안에서, 차량 신호를 읽을 수 있는 앱만이 풀 수 있습니다.")]));
children.push(bullet([b("Pleos Connect가 여는 기회: "), r("Vehicle SDK는 조향각(SteeringWheel), 브레이크 페달(Brake), 도어/트렁크(Door)를, AAOS Car API는 차속(PERF_VEHICLE_SPEED)과 기어(GEAR_SELECTION)를 실시간으로 제공합니다. 이 다섯 신호만으로 주차 과정의 품질을 객관적으로 측정하고 코칭할 수 있습니다.")]));
children.push(bullet([b("설계 원칙: "), r("GPS 정밀 정렬, 초음파 거리, 후방 카메라처럼 서드파티 앱이 받을 수 없는 데이터에는 기대지 않습니다. 모든 기능은 실제로 수신 가능한 신호 위에서만 동작하도록 설계했습니다.")]));

// ---- 3
children.push(h1("3. 주요 기능 및 차량 특화 요소"));
children.push(h2("3.1 Ghost Steering Guide (잔상 링 Snap-In)"));
children.push(bullet("화면에 목표 조향각의 잔상 링(Pleos Cyan)과 현재 핸들(흰색/코랄)을 실제 조향각과 1:1 비율로 그립니다. 두 링이 허용 오차 안에 포개지면 녹색으로 바뀌고 짧은 확인음이 납니다."));
children.push(bullet("\"15도 더 돌리세요\" 같은 숫자 대신 \"회색 링을 녹색 링에 맞추세요\"라는 한 가지 행동만 요구합니다. 인지 부하를 줄이는 것이 핵심 UX입니다."));
children.push(bullet([b("목표각 산출(기술 차별점): "), r("조향각과 차속을 운동학 자전거 모델(ψ̇ = v/L·tanδ)로 적분해 차량이 주차 시작 후 얼마나 회전했는지 추정합니다. 직각 후진 주차 표준 절차(풀 록 → 65° 회전 시 풀기 시작 → 80° 또는 핸들을 바로 세운 시점에 직진)에 따라 단계별 목표 조향각을 냅니다. 절대 위치가 아닌 상대 회전량을 쓰므로 GPS나 주차 칸 정보가 필요 없습니다.")]));

children.push(h2("3.2 100점 주차 과정 평가"));
children.push(p("모든 항목이 실제 수신 가능한 신호 위에 있으며, 결과가 아닌 과정(숙련자는 한 번에, 천천히, 핸들을 한 방향으로만 조작한다)을 점수화합니다.", { after: 80 }));
children.push(table([1900, 800, 2300, 4638], [
  ["평가 항목", "배점", "사용 신호 (경로)", "측정 및 감점 기준"],
  ["조향 안정성", "35", "조향각 (Vehicle SDK)", "조향 방향 반전 횟수(1회 무료, 이후 회당 −4), 이동 중 최대 조향 각속도 220°/s 초과분"],
  ["재시도 횟수", "25", "기어 (AAOS GEAR_SELECTION)", "R↔D 전환 횟수 0/1/2/3/4+회 → 25/20/13/6/0점"],
  ["속도·제동", "20", "차속 (AAOS PERF_VEHICLE_SPEED), 브레이크 (Vehicle SDK)", "R 기어 최고 속도 5 km/h 초과분 km/h당 −2, 급제동(감속 ≥ 9 km/h/s) 회당 −4"],
  ["안전 상태", "10", "도어/트렁크 (Vehicle SDK)", "후진 중 도어·트렁크 열림 감지 시 0점"],
  ["소요 시간", "10", "기어 타임스탬프", "첫 R → P 체결 45초 초과 시 10초당 −2"],
]));
children.push(p("등급: 90점 이상 명예 주차왕 / 70–89 안전 주차 마스터 / 50–69 주차 입문자 / 50 미만 연습 단계. 결과 카드는 P 기어 체결 후에만 표시되며, 가장 많이 감점된 항목에 대한 한 줄 코칭을 함께 제공합니다.", { before: 80 }));

children.push(h2("3.3 Driving UX 및 안전 설계"));
children.push(bullet([b("상태 머신: "), r("DRIVING(최소 UI, 터치 없음) → COACHING(링 + 한 줄 지시, 읽기 전용) → RESULT(P 체결 시에만, 터치 허용). Google Design for Driving 원칙을 따릅니다.")]));
children.push(bullet([b("주행 제한 연동: "), r("CarUxRestrictionsManager를 구독해 주행 제한 상태에서는 보조 텍스트와 데모 패널을 숨깁니다.")]));
children.push(bullet([b("음성 우선: "), r("단계 전환 시 Pleos TextToSpeech SDK(ON_DEVICE)로 안내해 운전자의 시선이 거울과 후방 카메라에 머물도록 합니다. Pleos Connect에서는 R 기어 시 시스템 후방 카메라가 화면을 점유하므로 음성이 주 채널이고 링은 확인용입니다(에뮬레이터 실측).")]));
children.push(bullet([b("현대차그룹 UX 라이팅 3원칙 적용: "), r("정확성·일관성·간결성. 예) \"화면의 녹색 가이드에 맞춰 핸들을 우측으로 돌려주세요.\" / \"조향 정렬이 완료되었습니다. 이 각도를 유지하세요.\" / \"후진 전에 도어와 트렁크가 모두 닫혔는지 확인하세요.\"")]));

children.push(h2("3.4 스마트 코칭 트리거"));
children.push(bullet("R 기어 진입 시 자동 시작하되, 직전 평가가 70점 이상인 운전자에게는 음성 안내를 줄이고 링만 표시하는 \"조용한 모드\"를 기본값으로 제공합니다(설정에서 변경 가능)."));
children.push(bullet("주차 칸 방향(좌/우)은 대기 화면에서 한 번만 선택하며, 주행 중에는 변경할 수 없습니다."));

// ---- 4
children.push(h1("4. 사용 시나리오"));
children.push(table([700, 3000, 5938], [
  ["단계", "운전자 행동", "앱 동작"],
  ["1", "주차 칸 앞에 정차 후 R 기어 체결", "COACHING 전환. \"핸들을 오른쪽 끝까지 돌린 뒤 천천히 후진하세요.\" 잔상 링이 풀 록 위치에 표시"],
  ["2", "핸들을 돌려 잔상 링에 맞춤", "현재 핸들이 녹색으로 바뀌고 확인음. \"조향 정렬이 완료되었습니다. 이 각도를 유지하세요.\""],
  ["3", "3 km/h 이하로 후진", "차속·조향각 적분으로 회전량 추적. 65°에 도달하면 \"핸들을 천천히 풀어 주세요.\" 잔상 링이 중앙으로 이동"],
  ["4", "핸들을 바르게 하고 직진 후진", "\"핸들을 바르게 하고 천천히 후진하세요.\" 과속·도어 열림 시 즉시 경고 라인"],
  ["5", "정차 후 P 기어 체결", "RESULT 카드: 총점, 등급, 5개 항목 막대, 가장 약한 항목 한 줄 코칭. 음성으로 점수 안내"],
]));

// ---- 5
children.push(h1("5. 개발 계획 (SDK/API 활용 계획)"));
children.push(h2("5.1 기술 스택"));
children.push(bullet("Pleos Connect (Android Automotive OS, API 28+), Kotlin 2.0, Jetpack Compose, Clean Architecture + MVVM + StateFlow"));
children.push(bullet("Pleos Vehicle SDK 2.3.2 (ai.pleos.playground:Vehicle), AAOS Car API (CarPropertyManager, CarUxRestrictionsManager)"));
children.push(bullet("빌드 기준선은 Pleos 공식 Vehicle SDK 샘플 앱과 동일 (AGP 8.8.2, Gradle 8.10.2, JDK 17, compileSdk 35)"));
children.push(h2("5.2 SDK/API 연동 명세"));
children.push(table([1500, 2600, 3538, 2000], [
  ["신호", "경로", "API", "권한"],
  ["조향각", "Vehicle SDK SteeringWheel", "checkSteeringWheelAngleCapabilities → getCurrentSteeringWheelAngle / registerSteeringWheelAngle", "pleos.car.permission.READ_CAR_STEERING"],
  ["기어", "AAOS CarPropertyManager (Pleos Gear 도메인은 폴백)", "GEAR_SELECTION 구독 (Pleos 2.3.2에서 Gear 도메인이 Google 속성 사용 권장으로 deprecated)", "android.car.permission.CAR_POWERTRAIN"],
  ["차속", "AAOS CarPropertyManager", "PERF_VEHICLE_SPEED, SENSOR_RATE_FAST (Pleos 공식 FAQ가 안내하는 클러스터 동일 소스)", "android.car.permission.CAR_SPEED"],
  ["브레이크", "Vehicle SDK Brake", "checkBrakePedalStatusCapabilities → registerBrakePedalStatus", "pleos.car.permission.CAR_DRIVE"],
  ["도어/트렁크", "Vehicle SDK Door", "checkDoorPositionCapabilities → registerDoorPosition(DoorArea(REAR …))", "pleos.car.permission.CAR_DOORS"],
  ["주행 제한", "AAOS CarUxRestrictionsManager", "onUxRestrictionsChanged", "—"],
]));
children.push(h2("5.3 현재 구현 상태 (1차 제출 시점)"));
children.push(bullet([b("완료: "), r("데이터 계층(Vehicle SDK·AAOS·시뮬레이터), 자전거 모델, Ghost 목표각 플래너, 100점 채점 엔진, R→P 세션 상태 머신, Compose UI(대기·코칭·결과·데모 패널), Pleos TTS 음성, 단위 테스트 21건, release APK. Pleos Connect 에뮬레이터(v2.1.0)에서 조향각·기어·차속·브레이크·도어 실수신과 채점·음성 재생을 확인했습니다.")]));
children.push(bullet([b("시연 방식: "), r("Pleos Connect AVD에서는 Virtual Console의 Motion(기어·브레이크·조향각)·Convenience(트렁크) 카드로 신호를 넣습니다. Vehicle Service가 없는 기기에서는 내장 시뮬레이터(모범 주차·초보 주차 시나리오, 수동 슬라이더)로 동일한 흐름을 재현합니다.")]));
children.push(bullet([b("PT까지: "), r("Pleos AVD에서 조향각 콜백 주기·분해능 실측 후 허용 오차 재보정, 조향각 부호 확인, 세션 로그 저장·리플레이, 주차 이력 화면.")]));
children.push(h2("5.4 일정"));
children.push(table([2200, 7438], [
  ["기간", "작업"],
  ["~11/15 (1차 접수)", "기획서·소스·APK·시연 영상 제출"],
  ["11/16 ~ 11/22", "조향각 부호 정규화 완료, 콜백 주기 보강(폴링) 완료 → 시연 영상·PT 대본 리허설, UX 라이팅 다듬기"],
  ["11/23 ~ 12/2", "세션 로그·이력, PT 대본 리허설(Virtual Console 경로 + 시뮬레이터 경로 이중화)"],
  ["12/3 ~ 12/4", "오프라인 PT 실동작 시연"],
  ["12/19 ~ 2027.1", "전시용 APK 안정화, Pleos TTS SDK 전환, 실차 테스트 기회 확보 시 모델 재보정"],
]));

// ---- 6
children.push(h1("6. 기대 효과 및 사업성"));
children.push(h2("6.1 타깃"));
children.push(bullet("1차: 면허 취득 3년 이내 또는 차량 교체 직후의 Pleos Connect 탑재 차량 운전자. 2차: 가족 차량을 공유하는 동승자·초보 가족 구성원."));
children.push(h2("6.2 가정 기반 지표 (검증 전 추정치)"));
children.push(table([3200, 2200, 4238], [
  ["지표", "가정치", "근거·산식"],
  ["대상 모수", "Pleos Connect 탑재 차량의 20%", "초보·교체 직후 운전자 비중 가정. 실제 비중은 출시 후 측정"],
  ["설치 대비 월간 활성(MAU)", "45%", "주차는 매 운행마다 발생하는 행위로, 설치 후 R 기어 진입 시 자동 동작하므로 별도 실행 부담이 없음"],
  ["30일 리텐션", "35%", "점수·등급이 매 주차마다 갱신되고, 70점 이상 도달 시 조용한 모드로 전환돼 피로도를 낮춤"],
  ["세션당 사용 시간", "약 30~60초", "R→P 구간에 한정. 주행 중 사용을 전제로 하지 않음"],
]));
children.push(h2("6.3 확장 계획"));
children.push(bullet([b("데이터 가치: "), r("주차 과정 지표(반전·재시도·속도)는 운전 숙련도의 객관적 지표입니다. 운전자 동의 하에 블루멤버스 포인트·정비 쿠폰·UBI 보험 연계가 가능하지만, 이는 "), b("제휴가 전제된 로드맵"), r("이며 본 제출본은 앱 내 등급·배지까지만 구현합니다. 운전 데이터의 외부 제공은 별도의 명시적 동의 설계를 전제로 합니다.")]));
children.push(bullet([b("기능 확장: "), r("ADAS SDK 공개 시 후방 장애물 거리를 \"안전 상태\" 항목에 추가, FusedLocation SDK로 자주 가는 주차장별 이력, 평행 주차 시나리오 추가.")]));
children.push(bullet([b("차량 특화 가치: "), r("동일한 기능을 스마트폰으로는 구현할 수 없습니다. 조향각과 기어를 실시간으로 읽는 것 자체가 Pleos Connect 앱만의 차별점입니다.")]));

// ---- 7
children.push(h1("7. 참고 자료"));
children.push(bullet("Pleos Playground 개발자 문서: Vehicle SDK Introduction / Sample App Guide / Virtual Console Guide / App FAQ (document.pleos.ai)"));
children.push(bullet("Pleos-Playground/pleos-sample-vehicle-app (GitHub) — 공식 Vehicle SDK 샘플, 빌드 기준선"));
children.push(bullet("Android Automotive OS: CarPropertyManager, CarUxRestrictionsManager, Design for Driving 가이드라인"));
children.push(bullet("운동학 자전거 모델(Kinematic bicycle model) — 저속 조향 기하 근사"));

const doc = new Document({
  creator: "pleos-steerin team",
  title: "나는야 주차왕 공모전 기획서 v6",
  styles: {
    default: { document: { run: { font: FONT, size: 20 } } },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: FONT, size: 28, bold: true, color: DARK }, paragraph: { spacing: { before: 320, after: 120 }, keepNext: true, outlineLevel: 0 } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: FONT, size: 23, bold: true, color: CYAN }, paragraph: { spacing: { before: 200, after: 80 }, keepNext: true, outlineLevel: 1 } },
    ],
  },
  numbering: { config: [{ reference: "bullets", levels: [
    { level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 420, hanging: 260 } } } },
    { level: 1, format: LevelFormat.BULLET, text: "–", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 840, hanging: 260 } } } },
  ] }] },
  sections: [{
    properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 1134, bottom: 1134, left: 1134, right: 1134 } } },
    children,
  }],
});

Packer.toBuffer(doc).then((buf) => { fs.writeFileSync(OUT, buf); console.log("wrote", OUT, buf.length); });
