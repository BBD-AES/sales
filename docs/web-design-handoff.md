# 웹 프론트 UI 개선 — Claude Design 핸드오프 (v2)

> 작성 2026-06-15 (v2 정정) · 대상 구현 레포: **BBD-AES/frontend-react** (본사·지점 웹, 단일 웹앱 Role·Tenancy 차등)
> 현재 상태 참조: `BBD-AES Console (3).html` — **100% 목업**(실 API 호출 0, in-memory). 화면의 `/api/v1/...`·토스트는 계약을 보여주는 라벨.
> 근거: 콘솔 27화면 + **5개 서비스(sales·inventory·item·user·procurement) 실제 REST 엔드포인트** + 안내북 공통 UI 패턴.
> **v2 정정**: 생산(작업지시)는 **procurement에 실구현** → 제거 아님, 연동. CustomerOrder(수주)는 인스코프(권한 추가 예정). **상태 배지는 실제 백엔드 enum을 따른다**(안내북 6종 매핑 폐기).

---

## 0. 핵심 — 콘솔은 목업, 전 화면을 실 API로 연동

콘솔은 데이터·상태전이가 전부 브라우저 메모리. frontend-react는 **모든 화면을 실 엔드포인트에 연결**하는 게 본질. 아래 표의 27화면은 **(공급사 제외 의심이 풀려) 전부 백엔드가 실재** — 제거할 화면 없음.

## 1. 화면 인벤토리 — 도메인·엔드포인트·상태 매핑

| 콘솔 화면 | 도메인(서비스) | 핵심 엔드포인트 | 상태 enum(실제 백엔드) |
|---|---|---|---|
| 대시보드 | inventory+sales+procurement 집계 | `/stocks/summary` + 각 목록 카운트 | — |
| 품목 목록/상세/등록 | item | `GET/POST /items`, `PATCH /items/{sku}/price·activate·deactivate` | active/비활성 |
| 재고 현황/상세/요약 | inventory | `GET /stocks`, `/stocks/{sku}`, `/stocks/summary`, `/stocks/{wh}/{sku}` | 정상/부족/없음/과잉 |
| 재고 보정·로케이션 | inventory | `PATCH /stocks/{wh}/{sku}`(ADJUST)·`/location` | — |
| 입출고 내역 | inventory | `GET /stock-movements` | IN/OUT/ADJUST |
| **판매주문(보충요청) 목록/상세/등록** | **sales** | `GET/POST /sales-orders`, `/{so}/submit·approve·reject·receive·cancel·withdraw`, 예약 `/{so}/reservations`·`/stock-availability` | **REQUESTED·SUBMITTED·IN_FULFILLMENT·BACKORDERED·REJECTED·CANCELED·RECEIVED** |
| **수주(고객주문) 목록/상세/등록** | **sales** | `GET/POST /customer-orders`, `/{co}/confirm·cancel·close` | **OPEN·CONFIRMED·CLOSED·CANCELED** |
| 발주(PO) 목록/상세/등록/SO연계 | procurement | `GET/POST /purchase-orders`, `/{po}/complete·cancel`, `PATCH /{po}`, `PUT /{po}/lines`, `/{po}/history` | **DRAFT·RECEIVED·CANCELED** |
| **발주 요청 알림** | procurement | `GET /purchase-requests` | PENDING·DONE |
| **작업지시(생산) 목록/상세/등록** | **procurement** | `GET/POST /work-orders`, `/{wo}/start·complete` | **PLANNED·IN_PRODUCTION·COMPLETED·CANCELED** |
| **생산 요청 알림** | **procurement** | `GET /work-order-requests` | PENDING·DONE |
| 공급사 목록/등록 | procurement | `GET/POST /vendors`, `PATCH /{code}·/{code}/active` | active/비활성 |
| 창고 목록/등록 | inventory | `GET /warehouses`, `POST /warehouses`, `PATCH /{code}/activate·deactivate` | active/비활성 |
| 사용자 권한 | user | `GET /users`(검색), `PATCH /users/{id}/authorization·/status` | ACTIVE·PENDING·INACTIVE |

> 콘솔이 쓰는 한글 상태 라벨(요청/제출/처리중/백오더/반려/취소/입고완료, 접수/확정/종료, 작성/입고/취소, 계획/생산중/완료, 미처리/처리완료)은 **백엔드 enum과 정확히 일치** → 그대로 유지.

---

## 2. 도메인 흐름 — 디자이너가 알아야 할 맥락

**2-1. 영업(sales) 두 갈래**
- **판매주문(보충요청, SO)** = 지점→본사 재고 보충. REQUESTED→SUBMITTED→(승인)IN_FULFILLMENT 또는 (부족)BACKORDERED→(지점 수령)RECEIVED. 승인 화면에 **멀티창고 예약**(전 라인 충당→IN_FULFILLMENT / 잔여분→BACKORDERED). 반려는 사유 필수(400).
- **수주(고객주문, CO)** = 지점이 고객에게 받은 주문. OPEN→CONFIRMED→CLOSED(+CANCELED). **인스코프 확정**(백엔드에 `@RequireRole` 추가 예정이므로 정식 화면으로 설계). 필드: 대리점창고·고객명·고객연락처·우선순위·라인.

**2-2. 부족분 → 조달(procurement) 자동 라우팅** ★ 두 알림 화면의 출처
- SO 승인 시 부족분이 `sales.purchase-requested` 이벤트로 procurement에 전달.
- procurement가 SKU별 **조달구분(MAKE/BUY)으로 자동 분기**(이벤트 힌트→item 마스터→기본 BUY):
  - **BUY 라인 → 발주 요청 알림(`/purchase-requests`, PENDING)** → 담당자가 보고 **발주(PO) 작성**.
  - **MAKE 라인 → 생산 요청 알림(`/work-order-requests`, PENDING)** → 담당자가 보고 **작업지시(WO) 작성**.
- PO 완료(DRAFT→RECEIVED) 또는 WO 완료(IN_PRODUCTION→COMPLETED) 시 **둘 다 `procurement.stock-in-requested` 발행** → inventory 재고 증가 + 원 SO 백오더 충족.
- 즉 **"생산 요청 알림 → 작업지시"와 "발주 요청 알림 → 발주"는 대칭 구조**. UI도 대칭으로 디자인(인박스 목록 → 항목 클릭 → 생성 폼 프리필 → 완료).

**2-3. 권한 요약(화면 노출 차등)**
- 생산(WorkOrder)·발주(PO)·공급사·요청 알림 = **HQ만**(HQ_MANAGER/HQ_STAFF). 지점·모바일 없음. 생성·완료·취소는 주로 HQ_MANAGER.
- SO 승인·거절 = ADMIN·HQ_MANAGER / SO 출고 처리 = HQ_* / SO 작성·도착확인 = BRANCH.
- 재고 조정·창고 관리 = ADMIN·HQ_MANAGER / 사용자 관리·품목 쓰기 = ADMIN·HQ_* / 사용자 관리 전체 = ADMIN.
- 지점(BRANCH)은 자기 지점 재고·SO·CO만(백엔드 Tenancy 강제, 소속 외 404).

---

## 3. 추가·보강 — 안내북 필수인데 콘솔에 약하거나 없는 UI

**3-1. 대시보드 — 할 일(To-do) 보강**
- 현재: 재고 KPI 6 + 영업/조달 KPI 4 + 최근 입출고 + 알림함.
- 추가 권고: **할 일 3탭**(승인 대기 SO·출고/처리 대기·도착 지연) 리스트(행 클릭 → 상세), 그리고 **요청 알림 합산**(발주 요청 PENDING + 생산 요청 PENDING)을 KPI/할 일에 노출. 카드 클릭 → 해당 목록 **필터 쿼리 포함 이동**. 부분 장애 시 카드만 "—".
- 지점 대시보드는 본사와 분리(지점은 자기 지점 요약).

**3-2. 권한·Tenancy별 노출** — 사이드바 메뉴·버튼을 Role·Tenancy로 차등 노출 + 진입 차단. 권한 없는 버튼은 **숨김**(disabled 아님). 데이터는 백엔드가 403/404 강제, UI는 404를 "대상 없음" 처리. (안내북 핵심 평가 항목)

**3-3. 사용자 관리 — 실 목록 연동** — 콘솔의 "목록 API 없음" disclaimer는 **stale**. user에 `GET /api/v1/users`(검색·페이징, ADMIN) 실재 → 정식 목록 화면(필터·테이블·행 액션 `/authorization`·`/status`). 사용자 *생성*은 외부 IdP(Keycloak/SCIM) 소관이라 ERP UI는 권한·상태 변경만 + "생성은 관리자 콘솔" 안내.

**3-4. 마이페이지 self** — `GET /api/v1/users/me` user팀에 요청 발행([user-me-endpoint-request.md](./user-me-endpoint-request.md)). 나오기 전엔 토큰 sub로 `internal/snapshot` 우회.

---

## 4. 전 화면 공통 UI 패턴 (안내북 — 전면 적용)
- **목록**: 통합검색 **디바운스 300ms** · 필터/정렬 변경 시 즉시 재조회+1페이지 · **URL 쿼리 동기화**(북마크/딥링크) · **서버사이드 정렬** · 페이지네이션 10·20·50(기본 20, 재고이력 기본 50) · 0건 시 빈 상태+"필터 초기화".
- **상태 4종**(콘솔 데모 토글을 실제 상태로): 로딩(스켈레톤)·데이터·**빈 상태**(전용 메시지)·**에러**(영역별 재시도).
- **기간 필터**: 기본 30~90일 · **최대 365일**(초과 시 인라인 경고+자동 보정).
- **폼/모달**: API 대기 중 버튼 비활성+중복 제출 차단 · 상태 변경(승인/거절/출고/완료/취소/비활성)은 **확인 모달**+취소·거절·반려는 **사유 필수**(예: SO 반려 400 SO006) · 모달 이탈 시 "작성 중 내용 사라짐" 확인 · 실시간 계산(조정 후 예상 재고, 라인 합계, 예약 잔여) · 조건부 필수(사유=기타→메모).
- **HTTP 분기 토스트**: 201/200 성공+목록 갱신 · 400 인라인 · 403 "권한 없음"+모달 닫힘 · 404 "대상 없음" · 409 "다른 사용자가 먼저 처리"+재조회(낙관적 락) · 5xx "일시적 오류"+재시도.
- **표기**: 0은 "0"(숨김 금지) · 누락 "—" · 상태/유형 **색 배지** · 비활성 행 회색+배지(이력엔 정상 표시).

---

## 5. 상태 배지 정의 (실제 백엔드 enum — 표기만 한글)

| 도메인 | enum(코드) · 라벨 |
|---|---|
| 판매주문 SO (sales) | REQUESTED 요청 · SUBMITTED 제출 · IN_FULFILLMENT 처리중(충당) · BACKORDERED 백오더 · REJECTED 반려 · CANCELED 취소 · RECEIVED 입고완료 |
| 수주 CO (sales) | OPEN 접수 · CONFIRMED 확정 · CLOSED 종료 · CANCELED 취소 |
| 발주 PO (procurement) | DRAFT 작성 · RECEIVED 입고완료 · CANCELED 취소 |
| 작업지시 WO (procurement) | PLANNED 계획 · IN_PRODUCTION 생산중 · COMPLETED 완료 · CANCELED 취소 |
| 요청 알림 (발주/생산) | PENDING 미처리 · DONE 처리완료 |
| 충당원천 FS (SO 라인) | STOCK 재고 · BACKORDERED 백오더(부족) |
| 재고 상태 | 정상 · 부족 · 없음 · 과잉 |
| 이동 유형 | IN 입고 · OUT 출고 · ADJUST 조정 |
| 사용자 상태 | ACTIVE 활성 · PENDING 대기 · INACTIVE 정지 |

> 안내북의 SHIPPED/DELIVERED 6종은 쓰지 않는다. **콘솔/백엔드의 실제 enum이 권위.**

---

## 6. 디자인 시스템 노트
- **해상도** 1440px 기준(이하 가로 스크롤). 지점 사용자 태블릿 병행 가능 → 조회·승인 화면 반응형 권장.
- **단일 웹앱, Role·Tenancy 차등** — 본사/지점은 한 앱에서 메뉴·데이터가 갈린다.
- **배지 색**: 재고 정상(녹)/부족(주황)/없음(적)/과잉(회), 이동 IN(↑녹)/OUT(↓적)/ADJUST(⟳회), 주문·발주·작업지시 상태별 색 구분.
- **밀도 토글(컴팩트/편안)** 유지. "상태 데모 토글"은 실 상태 전환으로 대체.
- **대칭 인박스 패턴**: 발주 요청 알림 ↔ 생산 요청 알림을 같은 레이아웃으로(목록→프리필 생성 폼).

---

## 7. 인증·진입
- **Keycloak OIDC 세션**(게이트웨이 TokenRelay, 브라우저는 토큰 직접 안 듦). 로그인=`/oauth2/authorization/keycloak`, 로그아웃 `POST /logout`.
- 로그인 성공 → 역할별 홈(ADMIN→사용자, HQ_*→대시보드, BRANCH_*→재고 조회).
- PENDING 첫 로그인 → 비밀번호 변경 강제(Keycloak). 토큰 만료 → 로그인 복귀+작성 폐기.

---

## 8. 남은 확인 (소수)
1. **PO 상태 3종 확정** — procurement 실제는 DRAFT·RECEIVED·CANCELED(콘솔과 일치). CONFIRMED/ORDERED 단계 없음 — 안내북 표기와 다르나 **백엔드 따름**(확정).
2. **CustomerOrder 권한** — 백엔드 `@RequireRole` 추가 예정(sales팀). UI는 SalesOrder와 동일 권한 모델로 설계.
3. **공급사 등록 필드** — Vendor: code(자동 V000001)·name·contact·terms(결제조건)·active. 등록은 HQ_MANAGER.
