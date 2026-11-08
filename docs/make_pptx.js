// Editable contest deck for 나는야 주차왕 (replaces the image-only Gemini export).
const path = require("path");
const pptxgen = require("pptxgenjs");
const { applyTheme } = require(process.env.PPTX_SKILL + "/scripts/apply_theme.js");

const OUT = process.argv[2];

const THEME = {
  name: "Steerin Dark",
  headFontFace: "Malgun Gothic",
  bodyFontFace: "Malgun Gothic",
  colors: {
    dk1: "F2F4F7", // text on dark
    lt1: "121418", // background
    dk2: "9AA3AF", // muted text
    lt2: "1B1F26", // surface
    accent1: "00AAD2", // cyan
    accent2: "00E676", // green
    accent3: "FF5252", // coral
    accent4: "FFC107", // amber
    accent5: "262B34", // surface high
    accent6: "343A45", // outline
    hlink: "00AAD2",
    folHlink: "00AAD2",
  },
};
const H = THEME.colors;

const pres = new pptxgen();
pres.layout = "LAYOUT_WIDE"; // 13.33 x 7.5
pres.theme = { headFontFace: THEME.headFontFace, bodyFontFace: THEME.bodyFontFace };
pres.title = "나는야 주차왕 - 현대자동차그룹 차량용 앱 개발 공모전";
pres.author = "pleos-steerin team";
const C = pres.SchemeColor;

const W = 13.333, Hh = 7.5, M = 0.6;

// ---------- Layouts
pres.defineSlideMaster({
  title: "TITLE",
  background: { color: H.lt1 },
  objects: [
    { placeholder: { options: { name: "title", type: "title", x: M, y: 3.0, w: W - 2 * M, h: 1.2, fontSize: 48, bold: true, color: C.text1, align: "center", margin: 0 } } },
    { placeholder: { options: { name: "body", type: "body", x: M, y: 4.25, w: W - 2 * M, h: 0.9, fontSize: 20, color: C.accent1, align: "center", margin: 0 } } },
  ],
});
pres.defineSlideMaster({
  title: "CONTENT",
  background: { color: H.lt1 },
  objects: [
    { placeholder: { options: { name: "title", type: "title", x: M, y: 0.45, w: W - 2 * M, h: 0.8, fontSize: 28, bold: true, color: C.text1, align: "left", margin: 0 } } },
    { text: { text: "나는야 주차왕 · 현대자동차그룹 차량용 앱 개발 공모전", options: { x: M, y: Hh - 0.45, w: 8, h: 0.3, fontSize: 10, color: C.text2, margin: 0 } } },
  ],
  slideNumber: { x: W - M - 0.6, y: Hh - 0.45, w: 0.6, h: 0.3, fontSize: 10, color: H.dk2, align: "right" },
});

// ---------- helpers
function card(slide, x, y, w, h, { fill = C.background2, line } = {}) {
  slide.addShape(pres.ShapeType.roundRect, { x, y, w, h, fill: { color: fill }, line: line ? { color: line, width: 1 } : { color: fill, width: 0 }, rectRadius: 0.12, objectName: "card" });
}
function text(slide, t, x, y, w, h, o = {}) {
  slide.addText(t, { x, y, w, h, isTextBox: true, margin: 0, fontSize: o.size || 14, color: o.color || C.text1, bold: !!o.bold, align: o.align || "left", valign: o.valign || "top", lineSpacingMultiple: o.ls || 1.15, objectName: o.name || "text", paraSpaceAfter: o.psa, bullet: o.bullet });
}
function bullets(slide, items, x, y, w, h, o = {}) {
  slide.addText(items.map((s, i) => ({ text: s, options: { bullet: { indent: 14 }, breakLine: i < items.length - 1 } })),
    { x, y, w, h, isTextBox: true, margin: 0, fontSize: o.size || 14, color: o.color || C.text1, valign: "top", paraSpaceAfter: o.psa ?? 6, lineSpacingMultiple: 1.12, objectName: o.name || "bullets" });
}
function tag(slide, t, x, y, w, color) {
  slide.addShape(pres.ShapeType.roundRect, { x, y, w, h: 0.34, fill: { color: H.lt2 }, line: { color, width: 1 }, rectRadius: 0.17, objectName: "tag" });
  text(slide, t, x, y, w, 0.34, { size: 11, color, bold: true, align: "center", valign: "middle" });
}
// Steering wheel drawn from shapes
function wheel(slide, cx, cy, r, color, { lineW = 4, transparency = 0 } = {}) {
  slide.addShape(pres.ShapeType.ellipse, { x: cx - r, y: cy - r, w: 2 * r, h: 2 * r, fill: { color: H.lt1, transparency: 100 }, line: { color, width: lineW, transparency }, objectName: "wheel-rim" });
  const hub = r * 0.18;
  slide.addShape(pres.ShapeType.ellipse, { x: cx - hub, y: cy - hub, w: 2 * hub, h: 2 * hub, fill: { color: H.lt1, transparency: 100 }, line: { color, width: lineW * 0.8, transparency }, objectName: "wheel-hub" });
  const spokes = [90, 210, 330];
  spokes.forEach((deg) => {
    const rad = (deg * Math.PI) / 180;
    const x1 = cx + Math.cos(rad) * hub, y1 = cy + Math.sin(rad) * hub;
    const x2 = cx + Math.cos(rad) * r * 0.97, y2 = cy + Math.sin(rad) * r * 0.97;
    slide.addShape(pres.ShapeType.line, { x: Math.min(x1, x2), y: Math.min(y1, y2), w: Math.abs(x2 - x1) || 0.01, h: Math.abs(y2 - y1) || 0.01, line: { color, width: lineW * 0.8, transparency }, flipH: x2 < x1, flipV: y2 < y1, objectName: "wheel-spoke" });
  });
  const dot = r * 0.07;
  slide.addShape(pres.ShapeType.ellipse, { x: cx - dot, y: cy - r - dot, w: 2 * dot, h: 2 * dot, fill: { color, transparency }, line: { color, width: 0, transparency }, objectName: "wheel-marker" });
}

// ================= Slide 1: Title
pres.addSection({ title: "소개" });
{
  const s = pres.addSlide({ masterName: "TITLE", sectionTitle: "소개" });
  wheel(s, W / 2 + 0.25, 1.75, 0.95, H.accent1, { lineW: 5, transparency: 45 });
  wheel(s, W / 2 - 0.25, 1.75, 0.85, H.accent2, { lineW: 5 });
  s.addText("나는야 주차왕", { placeholder: "title" });
  s.addText("Pleos Vehicle SDK 실시간 차량 신호로 후진 주차 과정을 채점하고, Ghost Ring으로 조향을 코칭하는 Pleos Connect 앱", { placeholder: "body" });
  text(s, "현대자동차그룹 차량용 앱 개발 공모전 2026 · 일반인 부문 · 스마트 드라이빙·안전", M, 5.4, W - 2 * M, 0.4, { size: 14, color: C.text2, align: "center" });
  s.addNotes("타이틀. 핵심 메시지: 스마트폰이 못 하는 것(조향각·기어 실시간 읽기)을 Pleos Connect 앱이 한다.");
}

// ================= Slide 2: Problem
pres.addSection({ title: "문제와 기회" });
{
  const s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "문제와 기회" });
  s.addText("경고음은 위험만 알리고, 고치는 법은 알려 주지 않습니다", { placeholder: "title" });
  const cw = (W - 2 * M - 0.8) / 3, cy = 1.7, ch = 4.2;
  const cols = [
    ["운전자의 문제", H.accent3, ["후진 주차는 초보 운전자가 가장 자주 실패하고 가장 오래 걱정하는 조작", "경고음·카메라는 현재 위험만 알림", "핸들을 얼마나, 언제 풀어야 하는지는 아무도 알려 주지 않음"]],
    ["스마트폰 앱의 한계", H.accent4, ["핸들이 얼마나 돌아갔는지 알 수 없음", "지금 기어가 R인지 알 수 없음", "차량 밖 데이터로는 주차 과정을 평가할 수 없음"]],
    ["Pleos Connect 기회", H.accent2, ["Vehicle SDK: 조향각 · 브레이크 · 도어/트렁크", "AAOS Car API: 차속 · 기어", "다섯 신호만으로 과정 평가와 코칭이 가능"]],
  ];
  cols.forEach(([t, color, items], i) => {
    const x = M + i * (cw + 0.4);
    card(s, x, cy, cw, ch);
    s.addShape(pres.ShapeType.ellipse, { x: x + 0.3, y: cy + 0.3, w: 0.5, h: 0.5, fill: { color }, line: { color, width: 0 }, objectName: "dot" });
    text(s, String(i + 1), x + 0.3, cy + 0.3, 0.5, 0.5, { size: 16, bold: true, color: H.lt1, align: "center", valign: "middle" });
    text(s, t, x + 0.95, cy + 0.33, cw - 1.2, 0.45, { size: 18, bold: true, color });
    bullets(s, items, x + 0.3, cy + 1.1, cw - 0.6, ch - 1.4, { size: 14, psa: 10 });
  });
  text(s, "설계 원칙: 서드파티 앱이 받을 수 없는 데이터(GPS 정밀 정렬, 초음파 거리, 후방 카메라)에는 기대지 않습니다.", M, 6.25, W - 2 * M, 0.4, { size: 13, color: C.text2 });
  s.addNotes("문제 → 왜 폰 앱은 못 푸는가 → 왜 Pleos Connect는 풀 수 있는가. 마지막 줄의 설계 원칙은 기술 심사 대비 포인트.");
}

// ================= Slide 3: Ghost Ring idea
pres.addSection({ title: "핵심 기능" });
{
  const s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "핵심 기능" });
  s.addText("Ghost Ring: 숫자 대신 링을 포개는 한 가지 행동", { placeholder: "title" });
  // Left: before/after
  card(s, M, 1.65, 3.9, 2.1, { line: H.accent3 });
  tag(s, "기존", M + 0.25, 1.85, 0.9, H.accent3);
  text(s, "\"차량이 좌측으로 12cm 치우쳤습니다. 핸들을 우측으로 15도 더 회전하세요.\"", M + 0.25, 2.3, 3.4, 1.3, { size: 14, color: C.text2 });
  card(s, M, 3.95, 3.9, 2.1, { line: H.accent2 });
  tag(s, "나는야 주차왕", M + 0.25, 4.15, 1.5, H.accent2);
  text(s, "\"화면의 녹색 가이드에 맞춰 핸들을 우측으로 돌려주세요.\"", M + 0.25, 4.6, 3.4, 1.3, { size: 15, bold: true });
  // Center: rings
  const cx = 7.3, cy = 3.95;
  wheel(s, cx + 0.55, cy, 1.75, H.accent1, { lineW: 6, transparency: 40 });
  wheel(s, cx - 0.2, cy, 1.55, H.dk1, { lineW: 6 });
  text(s, "목표 조향각 (Ghost, Cyan)", cx + 1.0, 1.65, 2.6, 0.4, { size: 12, color: H.accent1, align: "center" });
  text(s, "현재 핸들 (SDK 실시간)", cx - 2.6, 1.65, 2.4, 0.4, { size: 12, color: C.text2, align: "center" });
  // Right: rules
  const rx = 10.2, rw = W - M - rx;
  card(s, rx, 1.65, rw, 4.4);
  text(s, "동작 규칙", rx + 0.3, 1.85, rw - 0.6, 0.4, { size: 16, bold: true, color: H.accent1 });
  bullets(s, [
    "화면 회전 = 실제 조향각 (1:1)",
    "허용 오차 안에 포개지면 녹색 전환 + 확인음",
    "단계별 허용 오차: 풀 록 30° → 풀기 20° → 직진 5°",
    "지시문은 한 문장, 한 행동 (현대차 UX 라이팅 3원칙)",
    "음성이 주 채널, 링은 확인용",
  ], rx + 0.3, 2.4, rw - 0.6, 3.5, { size: 13, psa: 8 });
  s.addNotes("핵심 UX. 인지 부하를 줄이는 것이 목적. 숫자 지시 금지.");
}

// ================= Slide 4: Five signals
{
  const s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "핵심 기능" });
  s.addText("차량이 이미 알고 있는 다섯 가지 신호만 사용합니다", { placeholder: "title" });
  const rows = [
    ["신호", "경로", "API", "용도"],
    ["조향각", "Pleos Vehicle SDK", "SteeringWheel.registerSteeringWheelAngle", "Ghost Ring, 조향 안정성"],
    ["기어 P/R/N/D", "AAOS Car API (Pleos Gear 폴백)", "CarPropertyManager GEAR_SELECTION", "세션 시작/종료, 재시도"],
    ["차속", "AAOS Car API", "PERF_VEHICLE_SPEED (클러스터 동일 소스)", "회전량 적분, 속도 감점"],
    ["브레이크 페달", "Pleos Vehicle SDK", "Brake.registerBrakePedalStatus", "급제동 판정"],
    ["도어 / 트렁크", "Pleos Vehicle SDK", "Door.registerDoorPosition(REAR …)", "안전 상태"],
  ];
  s.addTable(rows.map((r, i) => r.map((c) => ({ text: c, options: { bold: i === 0, color: i === 0 ? H.lt1 : (c === rows[i][0] && i > 0 ? H.accent1 : H.dk1), fill: { color: i === 0 ? H.accent1 : (i % 2 ? H.lt2 : H.accent5) }, fontSize: 13, margin: [4, 8, 4, 8] } }))),
    { x: M, y: 1.6, w: W - 2 * M, colW: [1.9, 2.9, 4.3, 3.03], border: { type: "solid", color: H.lt1, pt: 1 }, objectName: "signals-table" });
  const by = 5.25;
  card(s, M, by, (W - 2 * M - 0.4) / 2, 1.35, { line: H.accent2 });
  text(s, "쓰지 않는 것", M + 0.3, by + 0.15, 2, 0.35, { size: 13, bold: true, color: H.accent2 });
  text(s, "클라우드 Vehicle Data API(유럽 한정·외부 서비스용), GPS 정밀 정렬, 초음파 거리(PDW), 후방 카메라, 가속 페달 — 서드파티 앱에 제공되지 않거나 정밀도가 부족한 데이터는 설계에서 제외했습니다.", M + 0.3, by + 0.5, (W - 2 * M - 0.4) / 2 - 0.6, 0.8, { size: 11, color: C.text2 });
  const bx = M + (W - 2 * M - 0.4) / 2 + 0.4;
  card(s, bx, by, (W - 2 * M - 0.4) / 2, 1.35, { line: H.accent1 });
  text(s, "검증 방법", bx + 0.3, by + 0.15, 2, 0.35, { size: 13, bold: true, color: H.accent1 });
  text(s, "Vehicle SDK 2.3.2 AAR의 공개 API를 직접 확인했고, 모든 리스너는 checkXxxCapabilities로 지원 여부를 먼저 검사합니다. 미지원 신호는 앱을 멈추지 않고 해당 항목만 비활성화됩니다.", bx + 0.3, by + 0.5, (W - 2 * M - 0.4) / 2 - 0.6, 0.8, { size: 11, color: C.text2 });
  s.addNotes("기술 활용성 심사 포인트. 'Pleos Gear 도메인은 2.3.2에서 deprecated, Google GEAR_SELECTION 권장'까지 알고 있다는 점을 말로 보완.");
}

// ================= Slide 5: Ghost target math
{
  const s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "핵심 기능" });
  s.addText("목표 조향각은 GPS가 아니라 운동학 모델로 계산합니다", { placeholder: "title" });
  const lw = 5.6;
  card(s, M, 1.6, lw, 4.7);
  text(s, "운동학 자전거 모델", M + 0.3, 1.8, lw - 0.6, 0.4, { size: 17, bold: true, color: H.accent1 });
  text(s, "ψ̇ = (v / L) · tan(δ),   δ = 조향각 / 조향비", M + 0.3, 2.3, lw - 0.6, 0.5, { size: 14, bold: true });
  bullets(s, [
    "입력: 조향각(SDK), 차속(AAOS), 기어 부호(R이면 −v)",
    "출력: 주차 시작 후 누적 회전량 |ψ| (상대 방위각)",
    "축거 2.75 m, 조향비 14.5, 록 540° (CarInfo 제원으로 보정 예정)",
    "절대 위치·주차 칸 정보·카메라가 필요 없음",
    "오차는 회전량 임계값(65°/80°)과 운전자 페이스 종료 규칙에 흡수되어 코칭 품질에 둔감",
  ], M + 0.3, 2.95, lw - 0.6, 3.2, { size: 13, psa: 8 });
  // Right: phase flow
  const rx = M + lw + 0.4, rw = W - M - rx;
  const phases = [
    ["1 준비", "정차 상태에서 풀 록으로 미리 돌리기", "목표 ±459° · 허용 30°", H.accent4],
    ["2 회전", "풀 록 유지하며 3 km/h 이하 후진", "회전량 65°까지", H.accent1],
    ["3 정렬", "65°→80° 구간에서 선형으로 풀기", "허용 20° · 핸들 중앙 복귀 시 종료", H.accent1],
    ["4 직진", "핸들 중앙, 천천히 후진 → P", "목표 0° · 허용 5°", H.accent2],
  ];
  phases.forEach(([t, d, m, color], i) => {
    const y = 1.6 + i * 1.2;
    card(s, rx, y, rw, 1.05);
    s.addShape(pres.ShapeType.rect, { x: rx, y, w: 0.12, h: 1.05, fill: { color }, line: { color, width: 0 }, objectName: "phase-mark" });
    text(s, t, rx + 0.35, y + 0.15, 1.6, 0.35, { size: 15, bold: true, color });
    text(s, d, rx + 1.95, y + 0.12, rw - 2.2, 0.45, { size: 13 });
    text(s, m, rx + 1.95, y + 0.58, rw - 2.2, 0.35, { size: 11, color: C.text2 });
  });
  s.addNotes("v5의 '카메라 궤적선·GPS 10cm' 정의를 폐기한 이유와 대안. 단계는 되돌아가지 않는다(혼란 방지).");
}

// ================= Slide 6: Scoring
{
  const s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "핵심 기능" });
  s.addText("100점 평가: 결과가 아니라 과정을 봅니다", { placeholder: "title" });
  const rows = [
    ["항목", "배점", "측정", "감점 기준"],
    ["조향 안정성", "35", "조향 반전 횟수, 이동 중 최대 조향 각속도", "반전 1회 무료, 이후 회당 −4 · 220°/s 초과분"],
    ["재시도 횟수", "25", "R↔D 전환 횟수", "0/1/2/3/4+회 → 25/20/13/6/0점"],
    ["속도·제동", "20", "R 기어 최고 속도, 급제동 횟수", "5 km/h 초과 km/h당 −2 · 급제동 회당 −4"],
    ["안전 상태", "10", "후진 중 도어/트렁크 열림", "발생 시 0점"],
    ["소요 시간", "10", "첫 R → P 체결", "45초 초과 10초당 −2"],
  ];
  s.addTable(rows.map((r, i) => r.map((c, j) => ({ text: c, options: { bold: i === 0 || j === 1, color: i === 0 ? H.lt1 : (j === 0 ? H.accent1 : j === 1 ? H.accent2 : H.dk1), fill: { color: i === 0 ? H.accent1 : (i % 2 ? H.lt2 : H.accent5) }, fontSize: 13, align: j === 1 ? "center" : "left", margin: [4, 8, 4, 8] } }))),
    { x: M, y: 1.6, w: 8.3, colW: [1.7, 0.8, 2.9, 2.9], border: { type: "solid", color: H.lt1, pt: 1 }, objectName: "score-table" });
  const rx = M + 8.3 + 0.4, rw = W - M - rx;
  card(s, rx, 1.6, rw, 2.2);
  text(s, "등급", rx + 0.3, 1.78, rw - 0.6, 0.35, { size: 15, bold: true, color: H.accent1 });
  bullets(s, ["90–100  명예 주차왕", "70–89  안전 주차 마스터", "50–69  주차 입문자", "0–49  연습 단계"], rx + 0.3, 2.2, rw - 0.6, 1.5, { size: 13, psa: 4 });
  card(s, rx, 4.0, rw, 2.3);
  text(s, "왜 과정인가", rx + 0.3, 4.18, rw - 0.6, 0.35, { size: 15, bold: true, color: H.accent2 });
  text(s, "주차 칸 대비 센티미터 정렬은 측정할 수 없습니다. 대신 \"숙련자는 한 번에, 천천히, 핸들을 한 방향으로만 조작한다\"는 관찰 가능한 행동을 점수화합니다. 임계값은 Config 한 곳에 모여 있어 실차 데이터로 재보정하기 쉽습니다.", rx + 0.3, 4.6, rw - 0.6, 1.6, { size: 12, color: C.text2 });
  s.addNotes("배점과 감점 기준. 단위 테스트가 모범 주차 ≥90, 초보 주차 <70을 보증.");
}

// ================= Slide 7: Scenario
pres.addSection({ title: "경험" });
{
  const s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "경험" });
  s.addText("사용 시나리오: R 기어에서 P 기어까지 다섯 장면", { placeholder: "title" });
  const steps = [
    ["R 체결", "코칭 시작. \"핸들을 오른쪽 끝까지 돌린 뒤 천천히 후진하세요.\"", H.accent4],
    ["링 맞추기", "핸들이 Ghost에 포개지면 녹색 + 확인음", H.accent2],
    ["후진·회전", "회전량 추적. 65°에서 \"핸들을 천천히 풀어 주세요.\"", H.accent1],
    ["직진", "\"핸들을 바르게 하고 천천히 후진하세요.\" 과속·도어 경고", H.accent1],
    ["P 체결", "결과 카드: 점수, 등급, 5개 항목, 한 줄 코칭. 음성 안내", H.accent2],
  ];
  const cw = (W - 2 * M - 4 * 0.25) / 5, cy = 1.7, ch = 3.9;
  steps.forEach(([t, d, color], i) => {
    const x = M + i * (cw + 0.25);
    card(s, x, cy, cw, ch);
    s.addShape(pres.ShapeType.ellipse, { x: x + cw / 2 - 0.35, y: cy + 0.35, w: 0.7, h: 0.7, fill: { color }, line: { color, width: 0 }, objectName: "step" });
    text(s, String(i + 1), x + cw / 2 - 0.35, cy + 0.35, 0.7, 0.7, { size: 20, bold: true, color: H.lt1, align: "center", valign: "middle" });
    text(s, t, x + 0.2, cy + 1.25, cw - 0.4, 0.45, { size: 17, bold: true, align: "center", color });
    text(s, d, x + 0.2, cy + 1.8, cw - 0.4, 2.0, { size: 12.5, color: C.text2, align: "center" });
  });
  card(s, M, 5.85, W - 2 * M, 0.85, { line: H.accent6 });
  text(s, "주행 중(DRIVING)에는 터치 요소가 없고, 코칭 중(COACHING)은 읽기 전용, 결과(RESULT)는 P 기어 체결 후에만 표시됩니다. CarUxRestrictions 제한 시 보조 텍스트와 데모 패널을 숨깁니다.", M + 0.3, 5.95, W - 2 * M - 0.6, 0.7, { size: 12, color: C.text2, valign: "middle" });
  s.addNotes("데모 영상의 장면 순서와 동일.");
}

// ================= Slide 8: Architecture
pres.addSection({ title: "구현" });
{
  const s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "구현" });
  s.addText("시스템 구성: 온디바이스, 세 계층", { placeholder: "title" });
  const layers = [
    ["Presentation", "MainActivity · ParkingViewModel · Compose UI (GhostRing, Coaching, Result, Standby, DemoPanel) · UxRestrictionsMonitor · VoiceCoach(TTS)", H.accent2],
    ["Domain (순수 Kotlin, 단위 테스트 20건)", "ParkingSessionTracker(상태 머신·세션 기록) · GhostTargetPlanner · BicycleModel · ParkingScoreEvaluator", H.accent1],
    ["Data", "PleosVehicleSignalSource (Vehicle SDK 2.3.2: 조향각·브레이크·도어, Gear 폴백) · AaosCarSignalSource (차속·기어) · SimulatedVehicleSignalSource (시나리오·수동)", H.accent4],
  ];
  layers.forEach(([t, d, color], i) => {
    const y = 1.65 + i * 1.5;
    card(s, M, y, 8.6, 1.3);
    s.addShape(pres.ShapeType.rect, { x: M, y, w: 0.12, h: 1.3, fill: { color }, line: { color, width: 0 }, objectName: "layer-mark" });
    text(s, t, M + 0.4, y + 0.15, 8, 0.4, { size: 16, bold: true, color });
    text(s, d, M + 0.4, y + 0.6, 8, 0.65, { size: 12, color: C.text2 });
    if (i < 2) text(s, "▲  StateFlow / Flow<VehicleSignal>", M + 0.4, y + 1.3, 5, 0.2, { size: 9, color: C.text2 });
  });
  const rx = M + 8.6 + 0.4, rw = W - M - rx;
  card(s, rx, 1.65, rw, 4.5);
  text(s, "기술 스택", rx + 0.3, 1.83, rw - 0.6, 0.35, { size: 15, bold: true, color: H.accent1 });
  bullets(s, [
    "Pleos Connect (AAOS, API 28+)",
    "Kotlin 2.0 · Jetpack Compose · StateFlow",
    "ai.pleos.playground:Vehicle:2.3.2",
    "AGP 8.8.2 · Gradle 8.10.2 · JDK 17 (공식 샘플 앱 기준선)",
    "Vehicle Service 감지 시 SDK, 없으면 시뮬레이터로 자동 전환",
    "12.3\" 1920×720 와이드 레이아웃, 다크 테마",
  ], rx + 0.3, 2.3, rw - 0.6, 3.7, { size: 12, psa: 7 });
  s.addNotes("클라우드 서버 없음. 모든 처리가 차량 안에서 끝난다 → 지연 없음, 개인정보 외부 전송 없음.");
}

// ================= Slide 9: Demo everywhere + verification
{
  const s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "구현" });
  s.addText("어디서나 같은 시연: Pleos AVD와 내장 시뮬레이터", { placeholder: "title" });
  const cw = (W - 2 * M - 0.4) / 2;
  card(s, M, 1.65, cw, 3.3, { line: H.accent1 });
  tag(s, "경로 A · Pleos Connect AVD", M + 0.3, 1.85, 2.6, H.accent1);
  bullets(s, [
    "Vehicle Service 감지 → 상단 칩 \"Pleos Vehicle SDK\"",
    "Virtual Console Motion 카드: 기어 R/P, 브레이크, 조향각",
    "Convenience 카드: 트렁크 열림 → 안전 항목 0점 시연",
    "AAOS PERF_VEHICLE_SPEED로 차속 수신",
  ], M + 0.3, 2.4, cw - 0.6, 2.4, { size: 13, psa: 8 });
  const bx = M + cw + 0.4;
  card(s, bx, 1.65, cw, 3.3, { line: H.accent4 });
  tag(s, "경로 B · 모든 Android 기기", bx + 0.3, 1.85, 2.6, H.accent4);
  bullets(s, [
    "Vehicle Service 없음 → 상단 칩 \"시뮬레이션 모드\"",
    "\"모범 주차 재생\" 24초 → 자동으로 결과 카드까지",
    "\"초보 주차 재생\" 42초 → 과속·재시도·반전 감점 시연",
    "수동 슬라이더: 기어, 조향각, 속도, 브레이크, 트렁크",
  ], bx + 0.3, 2.4, cw - 0.6, 2.4, { size: 13, psa: 8 });
  card(s, M, 5.2, W - 2 * M, 1.4);
  text(s, "검증", M + 0.3, 5.35, 2, 0.35, { size: 15, bold: true, color: H.accent2 });
  bullets(s, [
    "단위 테스트 20건: 채점 수식 7, 자전거 모델 4, Ghost 플래너 4, 세션 상태 머신 5 (모범 ≥ 90점·초보 < 70점 보증)",
    "GitHub 저장소에 소스 · release APK · 빌드 재현용 서명 키 포함",
  ], M + 0.3, 5.75, W - 2 * M - 0.6, 0.8, { size: 12.5, psa: 4 });
  s.addNotes("PT 리스크 대비: AVD 문제 시 경로 B로 즉시 전환. 두 경로의 화면 흐름은 동일.");
}

// ================= Slide 10: Business
pres.addSection({ title: "사업성" });
{
  const s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "사업성" });
  s.addText("기대 효과와 사업성", { placeholder: "title" });
  const cw = (W - 2 * M - 0.8) / 3;
  const cols = [
    ["타깃", H.accent1, ["1차: 면허 3년 이내 · 차량 교체 직후의 Pleos Connect 운전자", "2차: 가족 차량을 공유하는 동승자·초보 가족", "차량특화: 폰으로는 구현 불가한 기능"]],
    ["가정 지표 (출시 후 검증)", H.accent4, ["대상 모수: 탑재 차량의 20%", "MAU/설치: 45% — R 기어 진입 시 자동 동작, 실행 부담 없음", "30일 리텐션: 35% — 매 주차마다 점수 갱신, 70점↑ 조용한 모드로 피로도 억제", "세션 30~60초, 주행 중 사용 없음"]],
    ["확장 로드맵", H.accent2, ["ADAS SDK 공개 시 후방 거리 → 안전 항목", "Pleos TTS SDK 전환, 평행 주차 시나리오", "운전자 동의 기반 블루멤버스·정비·UBI 연계는 제휴 전제 로드맵 (본 제출본은 앱 내 등급·배지까지)"]],
  ];
  cols.forEach(([t, color, items], i) => {
    const x = M + i * (cw + 0.4);
    card(s, x, 1.65, cw, 4.6);
    text(s, t, x + 0.3, 1.85, cw - 0.6, 0.4, { size: 17, bold: true, color });
    bullets(s, items, x + 0.3, 2.4, cw - 0.6, 3.7, { size: 13, psa: 10 });
  });
  s.addNotes("수치는 가정치임을 명시. 보상 체계는 약속이 아니라 로드맵.");
}

// ================= Slide 11: Schedule & close
pres.addSection({ title: "마무리" });
{
  const s = pres.addSlide({ masterName: "CONTENT", sectionTitle: "마무리" });
  s.addText("일정과 다음 단계", { placeholder: "title" });
  const rows = [
    ["기간", "작업"],
    ["~11/15", "1차 접수: 기획서 · 소스 · release APK · 시연 영상"],
    ["11/16 ~ 11/22", "Pleos AVD 실측: 조향각 콜백 주기·분해능·부호 확인 → 허용 오차 재보정"],
    ["11/23 ~ 12/2", "세션 로그·주차 이력, PT 리허설 (AVD 경로 + 시뮬레이터 경로 이중화)"],
    ["12/3 ~ 12/4", "오프라인 PT 실동작 시연"],
    ["12/19 ~ 2027.1", "전시용 APK 안정화, TTS SDK 전환, 실차 테스트 시 모델 재보정"],
  ];
  s.addTable(rows.map((r, i) => r.map((c, j) => ({ text: c, options: { bold: i === 0 || j === 0, color: i === 0 ? H.lt1 : (j === 0 ? H.accent1 : H.dk1), fill: { color: i === 0 ? H.accent1 : (i % 2 ? H.lt2 : H.accent5) }, fontSize: 13, margin: [4, 8, 4, 8] } }))),
    { x: M, y: 1.6, w: 7.6, colW: [1.9, 5.7], border: { type: "solid", color: H.lt1, pt: 1 }, objectName: "schedule-table" });
  const rx = M + 7.6 + 0.4, rw = W - M - rx;
  card(s, rx, 1.6, rw, 4.6, { line: H.accent2 });
  wheel(s, rx + rw / 2 + 0.2, 2.9, 0.8, H.accent1, { lineW: 4, transparency: 45 });
  wheel(s, rx + rw / 2 - 0.15, 2.9, 0.72, H.accent2, { lineW: 4 });
  text(s, "주차의 스트레스를 측정 가능한 연습으로", rx + 0.3, 3.95, rw - 0.6, 0.65, { size: 16, bold: true, align: "center" });
  text(s, "차량이 이미 아는 다섯 신호로, 차량 안에서, 지금 바로 동작합니다.", rx + 0.3, 4.6, rw - 0.6, 0.8, { size: 13, color: C.text2, align: "center" });
  text(s, "github.com/skyworker104/pleos-steerin", rx + 0.3, 5.5, rw - 0.6, 0.4, { size: 12, color: H.accent1, align: "center" });
  s.addNotes("마무리. 질문 대비: 조향각 부호/주기, R 기어 시 후방 카메라와의 화면 점유 관계, 보상 체계의 실현 가능성.");
}

(async () => {
  await pres.writeFile({ fileName: OUT });
  await applyTheme(OUT, THEME);
  console.log("wrote", OUT);
})();
