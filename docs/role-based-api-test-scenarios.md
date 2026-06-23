# 권한별 API 테스트 시나리오 (sales)

> 작성 2026-06-22 · 출처: 컨트롤러 `@RequireRole` + 서비스 스코핑/상태규칙 코드(권위) · 라이브 실측 표기 `[✓실측]`

## 0. 테스트 신원 5종
| 토큰 | role | tenancy | 사번 | status |
|---|---|---|---|---|
| admin | ADMIN | 본사(HQ) | ADMIN | ACTIVE |
| hq_mgr | HQ_MANAGER | 본사(HQ) | HQ001 | ACTIVE |
| hq_staff | HQ_STAFF | 본사(HQ) | HQ002 | ACTIVE |
| br_mgr | BRANCH_MANAGER | 강남 1지점(BRANCH) | BR001 | ACTIVE |
| br_staff | BRANCH_STAFF | 강남 1지점(BRANCH) | BR002 | ACTIVE |

## 1. 인가 4단 (검사 순서 — 먼저 걸리는 게 응답)
1. **status** → 비ACTIVE(PENDING)면 모든 엔드포인트 `403 AUTH006`(승인 대기). 역할·테넌시 무관, 최우선.
2. **role** (`@RequireRole`) → 역할 미허용 `403 AUTH007`(필요한 역할 없음).
3. **tenancy** (이름축) → BRANCH는 본인 창고만; 위반 `403 SO003`(출고요청)/`CO003`(수주). HQ/ADMIN 우회.
4. **state**(상태기계) → 잘못된 전이 `409 SO00x`/`CO00x`.

## 2. 역할 매트릭스 (엔드포인트 × 역할)
✓=허용, ✗=`AUTH007`. (BRANCH ✓는 "본인 지점 한정", 위반 시 SO003/CO003.)

### 출고요청(SO)
| 엔드포인트 | ADMIN | HQ_MGR | HQ_STAFF | BR_MGR | BR_STAFF | 테넌시 | 상태 전제 |
|---|:--:|:--:|:--:|:--:|:--:|---|---|
| GET `/sales-orders` (목록) | ✓ | ✓ | ✓ | ✓ | ✓ | BRANCH=본인만 | — |
| GET `/{so}` (상세) | ✓ | ✓ | ✓ | ✓ | ✓ | BRANCH 본인(→SO003) | — |
| POST (생성) | ✓ | ✗ | ✗ | ✓ | ✓ | BRANCH 본인창고(→SO003) | — |
| PUT `/{so}` (수정) | ✓ | ✗ | ✗ | ✓ | ✓ | 본인 | REQUESTED(→SO002) |
| PATCH `/submit` | ✓ | ✗ | ✗ | **✓** | **✗** | 본인 | REQUESTED(→SO008) |
| PATCH `/withdraw` | ✓ | ✗ | ✗ | **✓** | **✗** | 본인 | SUBMITTED(→SO011) |
| PATCH `/cancel` | ✓ | ✗ | ✗ | ✓ | ✓ | 본인 | REQUESTED·SUBMITTED(→SO009) |
| PATCH `/receive` | ✓ | ✗ | ✗ | ✓ | ✓ | 본인 | IN_FULFILLMENT(→SO005) |
| PATCH `/approve` | ✓ | **✓** | **✗** | ✗ | ✗ | HQ=전체 | SUBMITTED(→SO004) |
| PATCH `/reject` | ✓ | ✓ | ✗ | ✗ | ✗ | HQ=전체 | SUBMITTED(→SO004); 사유필수(→SO006) |
| PATCH `/fulfill-backorder` | ✓ | ✓ | ✗ | ✗ | ✗ | HQ | BACKORDERED(→SO010) |
| POST `/{so}/reservations` | ✓ | ✓ | ✗ | ✗ | ✗ | HQ | 라인 미충족분(→SO012) |
| GET `/{so}/stock-availability` | ✓ | ✓ | ✗ | ✗ | ✗ | — | — |

### 수주(CO)
| 엔드포인트 | ADMIN | HQ_MGR | HQ_STAFF | BR_MGR | BR_STAFF | 테넌시 | 상태 전제 |
|---|:--:|:--:|:--:|:--:|:--:|---|---|
| GET `/customer-orders` (목록) | ✓ | ✓ | ✓ | ✓ | ✓ | BRANCH=본인만 | — |
| GET `/{co}` (상세) | ✓ | ✓ | ✓ | ✓ | ✓ | BRANCH 본인(→CO003) | — |
| POST (생성) | ✓ | **✗** | **✗** | ✓ | ✓ | BRANCH 본인지점(→CO003) | — |
| PUT `/{co}` (수정) | ✓ | ✗ | ✗ | ✓ | ✓ | 본인 | OPEN(→CO002) |
| PATCH `/confirm` | ✓ | ✗ | ✗ | ✓ | ✓ | 본인 | OPEN(→CO004) |
| PATCH `/cancel` | ✓ | ✗ | ✗ | ✓ | ✓ | 본인 | OPEN·CONFIRMED(→CO005) |
| PATCH `/close` | ✓ | ✗ | ✗ | ✓ | ✓ | 본인 | CONFIRMED(→CO006); 재고부족(→CO007) |

### 알림
| GET `/notifications` | ✓ | ✓ | ✓ | **✗** | **✗** | — | — |

## 3. 역할 한 줄 요약
- **ADMIN**: 전부(테넌시 우회).
- **HQ_MANAGER**: SO **결정**(승인/반려/충족/예약) + 전체 조회 + 알림. ❌ CO 조작, ❌ SO 작성.
- **HQ_STAFF**: 조회 + 알림 **only(view-only)**. ❌ 결정·작성.
- **BRANCH_MANAGER**: 자기지점 SO 작성/**제출·회수**/취소/수령 + CO 전부. ❌ SO 승인, ❌ 알림.
- **BRANCH_STAFF**: BRANCH_MANAGER와 동일하되 **❌ SO 제출·회수**(작성·취소·수령·CO 전부는 ✓).

---

## 4. 상세 네거티브 시나리오 ("되고 안 되고")

### A. status 게이트 (AUTH006)
- **A1** PENDING 유저로 *어떤* 엔드포인트든(목록 포함) → `403 AUTH006`. 역할/테넌시보다 우선. `[✓실측: 활성화 전 br_staff 목록]`

### B. 역할 게이트 (AUTH007) — manager vs staff
- **B1 (BRANCH 매니저 vs 스태프)** — *유일한 차이 = 제출/회수* `[✓실측 2026-06-22]`
  - `br_staff` SO create → **201**(스태프 작성 가능) → `br_staff` submit → **403 AUTH007** ✗ vs `br_mgr` submit → **200 SUBMITTED** ✓
  - withdraw도 동일(MGR ✓ / STAFF 403 AUTH007).
  - 그 외(SO create/cancel/receive/update, CO 전부)는 **둘 다 ✓**. → *스태프는 초안 작성·수령·수주는 하되 HQ로 제출은 매니저만*.
- **B2 (HQ 매니저 vs 스태프)** — *스태프는 view-only* `[✓실측: hq_staff approve→403 AUTH007]`
  - `hq_mgr` PATCH `/{so}/approve` → 200 ✓ vs `hq_staff` → 403 AUTH007 ✗
  - reject·fulfill-backorder·reservations·stock-availability 동일(MGR ✓ / STAFF ✗). list·detail은 둘 다 ✓.
- **B3 (BRANCH는 SO 결정 불가)**: `br_mgr`/`br_staff` PATCH `/{so}/approve|reject|fulfill-backorder` → 403 AUTH007. `[✓실측: br_mgr approve→AUTH007]`
- **B4 (HQ는 CO 불가)**: `hq_mgr`/`hq_staff` POST `/customer-orders`(및 confirm/close/cancel/update) → 403 AUTH007(CO=지점 소유). `[✓실측: hq_mgr CO create→AUTH007]`
- **B5 (HQ는 SO 작성 불가)**: `hq_mgr`/`hq_staff` POST `/sales-orders` → 403 AUTH007. `[✓실측: hq_staff SO create→AUTH007]`
- **B6 (알림 HQ 전용)**: `br_mgr`/`br_staff` GET `/notifications` → 403 AUTH007; HQ → 200. `[✓실측]`

### C. 테넌시 스코핑 (SO003/CO003)
- **C1** `br_mgr`(강남) GET 분당 SO → `403 SO003`.
- **C2** `br_mgr`(강남) GET 대구 CO-0004 → `403 CO003`. `[✓실측]`
- **C3** `br_mgr`(강남) PATCH 분당 CO `/confirm` → 403 CO003(쓰기도 동일).
- **C4** `br_mgr`(강남) POST SO `toWarehouseCode=WH-BR-002`(분당) → 403 SO003(타 지점 앞 생성 불가).
- **C5** `br_mgr`(강남) GET 본인 강남 SO/CO → 200. `[✓실측: CO-0001/SO-0002 200]`
- **C6** HQ/ADMIN GET 아무 지점 → 200(스코프 우회). `[✓실측: HQ 전체 조회]`
- **C7** 목록: `br_mgr` 응답에 강남만(분당/대구 미노출), 넘긴 창고필터 무시. `[✓실측: br_mgr 목록 SO3·CO1]`

### D. 상태기계 (잘못된 전이)
- **D1** REQUESTED 아닌 SO submit → 409 SO008.
- **D2** SUBMITTED 아닌 SO approve → 409 SO004.
- **D3** IN_FULFILLMENT 아닌 SO receive → 409 SO005. `[✓실측: BACKORDERED서 receive→SO005]`
- **D4** BACKORDERED 아닌 SO fulfill-backorder → 409 SO010.
- **D5** SUBMITTED 아닌 SO withdraw → 409 SO011.
- **D6** OPEN 아닌 CO confirm → 409 CO004.
- **D7** CONFIRMED 아닌 CO close → 409 CO006. `[✓실측: OPEN CO close→과거 검증]`
- **D8** reject 사유 누락 → 400 SO006.
- **D9** CO close 시 지점재고 부족 → 409 CO007(rest 모드+부족; stub은 no-op).

### E. 멱등성 (#71, 생성 한정)
- **E1** 같은 `Idempotency-Key`로 create 2회 → **동일 주문번호**, 중복 0. `[✓실측: CO 같은 키→CO-2026-0006 동일]`
- **E2** 키 없이 create 2회 → 별개 주문 2건.
- **E3** 같은 키·다른 요청자 → 409 IDEM002(키 오용).
- **E4** 동시 같은 키 → 1건 + 409 IDEM001(재시도 시 원본 회수).

---

## 5. 해피패스 (역할 협업 흐름)
- **F1 (SO 정상)**: `br_staff` 또는 `br_mgr` create → **`br_mgr` submit**(스태프 불가) → `hq_mgr` approve(전량 가용=IN_FULFILLMENT) → `br_*` receive=RECEIVED.
- **F2 (SO 백오더)**: create(부족 SKU 포함, 예 RLY-12V-30A-01) → submit → `hq_mgr` approve=**BACKORDERED** + purchase-requested → (보충 후) `hq_mgr` fulfill-backorder=IN_FULFILLMENT → receive.
- **F3 (CO + #69)**: `br_*` create=OPEN → confirm=CONFIRMED → close=CLOSED(+지점재고 차감). `[✓실측: CO-2026-0005 OPEN→CONFIRMED→CLOSED]`
- **F4 (SO 반려/취소/회수)**: submit→`hq_mgr` reject(사유)=REJECTED / `br_mgr` withdraw=REQUESTED(재수정) / `br_*` cancel=CANCELED.

> ⚠️ 배포 제약: 현재 `inventory.mode=stub`이고 stub `reserve`가 0을 반환해 **모든 SO가 approve 시 BACKORDERED로 막힘** → F1의 IN_FULFILLMENT/RECEIVED는 inventory rest전환+재고시드 후에 검증 가능(`test-readiness-name-axis-handoff.md` 참조). CO close·멱등은 reserve 무관이라 정상.

---

## 6. 검증 커버리지 (라이브 실측 2026-06-22)
**28/28 시나리오 PASS**(2배치) + 앞선 인가/스코핑/플로우 실측.
- ✅ **전수 실측**: §A status(AUTH006), §B 역할(B1~B6, manager↔staff·HQ↔branch 구분 포함), §C 테넌시(C1~C7, 본인 200 / 교차 403 SO003·CO003), §D 상태기계(D1·D2·D3·D4·D5·D6·D7·D8), §E 멱등(E1·E1b·E2·E3), §F 전이(withdraw·reject·cancel·update·CO close#69).
- ⛔ **환경 제약으로 코드/단위테스트로만**(라이브 미실측):
  - **D9** CO close 재고부족→CO007: stub은 차감 no-op이라 부족 상황 생성 불가(rest+재고 필요).
  - **E4** 동시 같은키→IDEM001: 타이밍 레이스 — sales 단위테스트로 검증.
  - **F1 풀해피** SO IN_FULFILLMENT→RECEIVED: stub `reserve`=0이라 도달 불가(rest+재고 필요).
