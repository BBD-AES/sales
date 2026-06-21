# 웹 콘솔(BBD-AES Console) 정밀 검사 — 품질·계약 정확성·포팅 준비도

> 작성 2026-06-21 · 대상: `BBD-web.zip` (design_handoff_bbd_console, plain JSX 소스 — 디코드 불필요)
> 방법: 6영역(core/store, 영업, 구매, 재고, 관리/셸, UX·컴포넌트) 병렬 감사 + **실제 5개 서비스 백엔드 계약과 한 줄씩 대조**. raw 86건.
> 목적: 품질 판정 + frontend-react(Vite+React+TS) 포팅 준비도 평가.

## 종합 판정 — 78/100, "핸드오프 가능하나 정정 전 라이브 금지"
표면(상태 enum·핵심 권한·지점 스코프·데이터 정직성)은 **우수**하다. 그러나 ① 도메인 커버리지(생산 워크플로 전면 부재)와 ② 일부 계약 충실도(백엔드에 없는 transfer·issuer를 동작/표시, 작성 권한 과대)에서 P0가 남아, 정정 전 실연동은 403/404/필터0을 부른다.

---

## 강점 (정직하게 — 잘 만든 부분)
1. **상태 enum 충실**: SO 7종(REQUESTED·SUBMITTED·IN_FULFILLMENT·BACKORDERED·REJECTED·CANCELED·RECEIVED)·CO 4종·PO 3종이 백엔드 도메인 enum과 완전 일치. **안내북의 오염 enum(SHIPPED/DELIVERED) 거부**, IN_FULFILLMENT/BACKORDERED 정확 사용.
2. **핵심 권한 게이팅 정확**: `so.decide=[ADMIN,HQ_MANAGER]`(HQ_STAFF 제외), `so.submit=[ADMIN,BRANCH_MANAGER]`, `nav.admin=[ADMIN]`가 실제 컨트롤러 `@RequireRole`과 1:1. (BRANCH_STAFF 제출 차단은 결함 아니라 정답)
3. **지점 데이터축 스코프 일관**: `isBranchRole+currentWarehouse(=tenancyName)`로 SO/CO/stock/movements 리스트·뱃지·대시보드·뮤테이션(scopeViolation)까지 제한. 코드축 클레임 없이 이름축 인가 — 실제 테넌시 결정과 부합.
4. **승인=동기 재고예약 모델 정합**: approveSO가 HQ availableStock에서 라인별 즉시 예약 → 전량 IN_FULFILLMENT/부족 BACKORDERED, sourcingType MAKE→PRODUCTION/BUY→PURCHASE 산출이 백엔드 SourcingResolver와 개념 일치. 수령 시 예약해제→HQ OUT/지점 IN.
5. **데이터 정직성(부분) 모범**: 알림 읽음처리 API 미제공·지점 알림 미구현·사용자목록 외부 IdP(SCIM/mTLS)를 UI에 명시 고지. (단 transfer/issuer는 예외 — P0로 분리)
6. **시드가 게이트웨이 camelCase 계약 미러**(soNumber·toWarehouseCode·reservedQuantity·fulfillmentSource·unitPriceSnapshot·version, PO envelope·history) → TS 인터페이스 source-of-truth로 재사용 가능.
7. **공용 컴포넌트·레이아웃 일관**: SlideOver·FacetGroup·LineEditor(CO/SO/PO 공유)·Stepper(SO_OFFPATH로 7상태를 4스텝+오프패스색)·패싯레일+KPI+dense표 골격 통일.
8. **역할별 대시보드/뱃지 분기**: HQ=SUBMITTED·지점=IN_FULFILLMENT(스코프), KPI 딥링크가 status·focus·belowOnly 정확 프리셋.

---

## P0 — 라이브 전 필수 (5건)

**P0-1. 생산(WorkOrder) 워크플로 전면 부재** `scope`
생산(WorkOrder: PLANNED·IN_PRODUCTION·COMPLETED·CANCELED) 화면·enum·시드·라벨·전이가 0건. 그런데 approveSO는 MAKE 품목에 `fulfillmentSource='PRODUCTION'`을 실제 생성 → 그 생산오더를 조회/완료(COMPLETED→stock-in)할 종착지가 없어 **백오더→생산→입고 루프가 끊김**. (procurement에 WorkOrder가 PO와 형제 애그리거트로 실재, COMPLETED 시 StockInRequested 발행)
→ **fix**: util에 WO_PILL/WO_LABEL/WO_STEPS, core에 WORK_ORDERS 시드, screens/work-orders.jsx(구매 섹션) 추가. PRODUCTION 소스 라인→작업지시 추적 링크.

**P0-2. 발주요청/생산요청 인박스 부재** `scope`
부족분 라우팅 인박스(발주요청 `GET /purchase-requests` PENDING·DONE, 생산요청 `GET /work-order-requests`)가 없음 — 이게 HQ 구매담당의 의도된 진입점. 콘솔은 백오더 해소를 sales 화면 '백오더 이행' 버튼으로만 모델링해 실제 비동기 PR→PO/WO→stock-in→replenished 파이프라인과 불일치.
→ **fix**: procurement에 두 인박스(PENDING/DONE 배지) + 행에서 '발주 생성'/'작업지시 생성' 액션. PO 작성 1차 진입점으로, 'SO 직접 프리필'은 보조로 강등.

**P0-3. 창고 간 이동(transfer)이 백엔드 없이 동작** `data-honesty`
TransferPanel + TRF- 참조로 동작하는 척하나 inventory에 transfer 엔드포인트 없음(availability/reservations/release/issue뿐). 다른 폼은 SlideOver subtitle에 실 라우트를 적는데 transfer만 ROUTES에도 없고 고지도 없음 → 정직성 일관성 붕괴.
→ **fix**: 이동 API 없으면 제거하거나 '백엔드 미제공(데모)' 명시. 실구현 의도면 inventory 팀에 transfer 신설 협의를 핸드오프에 기록.

**P0-4. 입출고 '담당(issuer)' 컬럼이 없는 필드 표시** `data-honesty`
실제 StockMovement DTO에 employee/담당자 필드 없음(sku·warehouseCode·type·quantity·unitPrice·referenceNumber·occurredAt). 콘솔은 ISSUERS 인물명 시드 + '담당' 컬럼 노출 → 미제공 필드를 응답인 척.
→ **fix**: issuer 필드·'담당' 컬럼 제거(또는 '—'+미제공 툴팁). referenceNumber/reason 등 실 필드만.

**P0-5. 재고조정 권한 과대 노출** `correctness`
`stock.adjust` 권한키가 PERMS에 없고 조정 버튼이 `stock.location`(BRANCH_MANAGER·BRANCH_STAFF·HQ_STAFF 포함)으로 게이트 → 재고를 실제 움직이는 ADJUST가 위치변경과 동급으로 광범위 노출. adjustStock 뮤테이션에 역할/스코프 검사 전무. (안내북 재고조정=ADMIN·HQ_MANAGER 전용)
→ **fix**: `PERMS['stock.adjust']=['ADMIN','HQ_MANAGER']` 신설 + 조정 버튼 분리 게이트, adjustStock에 역할 가드.

---

## P1 — 라이브 시 403/404/필터0 (확정)

- **알림 targetRole 'HQ' 리터럴** `correctness` — 백엔드는 'HQ_MANAGER'(역할 enum에 'HQ' 없음) → 라이브 필터 **0건**. core.js·store.submitSO·shell 필터 `n.targetRole==='HQ'` 전부 'HQ_MANAGER'로 통일.
- **so.write/co.write가 HQ에도 작성 권한** `correctness` — 백엔드 POST /sales-orders·/customer-orders=[BRANCH_STAFF,BRANCH_MANAGER,ADMIN]로 HQ 제외 → HQ는 라이브 403. BRANCH(+ADMIN)로 좁히고 HQ 작성 버튼 제거.
- **withdraw(제출 철회) 미구현** `scope` — 백엔드 `PATCH /{so}/withdraw`(BRANCH_MANAGER,ADMIN) 대응 없음, cancel로만 표현. api.withdrawSO + SUBMITTED 조건 철회 버튼 추가.
- **품목 활성/비활성 토글 부재** `scope` — `PATCH /items/{sku}/activate|deactivate` 실재·updateItem 정의됐으나 화면에 토글 없음(dead code). 토글+ROUTES 등록.
- **CO 뮤테이션 스코프 가드 누락** `correctness` — updateCO/transitionCO/createCO가 scopeViolation 미호출(SO는 호출). 가드 추가.
- **PRODUCTION 소스 라벨 vs 생산화면 부재** `data-honesty` — P0-1과 동일 뿌리. 생산 화면 도입으로 추적 링크 연결.
- **stock.adjust 경로 불일치** `correctness` — ROUTES/패널이 `POST /stocks/adjust`이나 실제는 `PATCH /stocks/{wh}/{sku}`(계약 자체도 미확정 — inventory와 확정 필요).
- **relaySOs 백오더→발주 시기 부정확** `correctness` — SUBMITTED(승인 전) SO를 발주 소스로 노출. BACKORDERED+BUY 라인으로 한정, 정식 진입점은 P0-2 인박스.
- **window 전역 / __intent 딥링크** `port-readiness` — Object.assign(window,…) 강결합 + 단일 가변 전역 `__intent` 1회 소비 → React Router/StrictMode 이중 mount·뒤로가기·북마크에서 깨짐.

---

## P2 — 다듬기 (대표)
- ROUTES 전이 동사 불일치(SO 수정 'PUT' 라벨 vs 실제 PATCH), withdraw/reserveLine/stockAvailability·검색필터(received_by·기간) 라우트 맵 누락.
- movements에 **ADJUST 타입 미표현**(IN/OUT 2종만, 실제 3종) — 조정 이동이 원장에서 구분 불가.
- `item.filter='GET /items/filter'`(실제 없음, `/items?sku=`)·`warehouse.update=PUT`(존재 불명) 경로 over-claim → 404 위험.
- procurement 뮤테이션(cancelPO·updatePOHeader·replacePOLines) ROUTES 누락 + README는 'ENDPOINTS' 언급하나 코드엔 ROUTES만(심볼명 불일치).
- admin 사용자 저장이 단일 PATCH /authorization이나 백엔드는 /authorization·/status 2개(부분실패 경로 없음).
- 로딩/에러 상태 UI 전무(빈 상태는 일관), 접근성 부분적(표 행 키보드 미지원·SlideOver 포커스트랩 없음·kbd-focus 정의만).
- 대시보드 헤더 날짜 '2026-05-29' 하드코딩, CO 딥링크가 focus 무시(SO는 처리).
- PO 라인(lineOrder/partName/unitPrice) vs SO 라인(lineNo/nameSnapshot/unitPriceSnapshot) 필드명 이질 → LineEditor 수동변환. api가 동기 {ok} 즉시 반환 → live fetch 전환 시 일괄 수정.
- sales-orders 검색 패싯에 received_by·기간(date-range) 누락.

---

## 포팅 준비도 (frontend-react) — 보통-상
구조는 친화적: ROUTES 단일맵→src/api 1:1 교체 가능, 시드 camelCase→TS 인터페이스 재사용, PERMS/상태맵/공용컴포넌트가 모듈 경계 주석 보유. **단 선행 작업 순서**:
1. **window 전역 해체** — core→src/data(+TS 타입), util→src/lib/format, store→Context/Zustand, 화면의 window.can/SO_LABEL/ITEM_BY_SKU 수십 참조를 import로.
2. **타입화** — SO/CO/PO/WO 상태를 `as const` 리터럴 유니온으로(LABEL/PILL/STEPS 키 누락을 컴파일 타임 강제), 라인 필드 이질을 interface 분리 + LineEditor 제네릭화.
3. **라우터** — useState(page)+__intent → react-router, go(target,intent)→navigate(path?query), 화면은 useSearchParams로 status/focus 수신(StrictMode 안전).
4. **MOCK 해제** — api 동기 {ok}→async, 호출부 await/then, ROUTES→실 fetch 어댑터(envelope/비-envelope unwrap, 토스트+ERRORS 재사용), 화면별 AsyncBoundary(로딩 스켈레톤+에러 재시도).
5. **계약 정정 선반영(필수)** — stock.adjust 경로/메서드, item.filter→/items?sku=, notification 'HQ'→'HQ_MANAGER', so.write/co.write 축소, auth.me(미확정 /users/me 대기). **안 하면 첫 라이브에서 403/404·필터0.**

권장 순서: 타입·모듈화 → 라우터 → 계약 정정 → async/MOCK 해제 → **P0 도메인(WorkOrder/PR·WO 인박스) 신규 화면**. P0 도메인은 포팅과 병행 가능하나, 포팅 전 핸드오프에 '생산·이동·issuer 미구현/미정' 한계를 명시해 정직성을 먼저 회복할 것.
