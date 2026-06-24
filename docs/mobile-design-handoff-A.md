# BBD 모바일 디자인 핸드오프 — A안 (현장 실행 앱)

> 대상: 디자인(프로토타입 산출). 작성: sales팀. 기준일 2026-06-24.
> 실제 구현(BBD-mobile, Kotlin/Compose) + sales/inventory/user 백엔드 API 계약을 반영한 **권위 핸드오프**.
> JSX/목업은 디자인 참고용이며, 최종 반영은 Compose 재구현임.

---

## 0. 제품 포지셔닝 — "모바일 = 현장 실행"

| 클라이언트 | 역할 | 핵심 행위 |
|---|---|---|
| **모바일(이 문서)** | 지점 **현장 실행** | 입고 수령 · 출고 차감 · **현장 수주(CO) 등록** · 재고 조회 · 작업 이력 |
| 웹 콘솔 | 계획·결정 | STR(재고이동요청) **작성·제출·승인·예약**, 발주/작업지시 |

**A안 핵심 결정 (우리 선택):**
- ✅ **CO(현장 수주) 작성 = 모바일에 둔다** — 지점이 고객 주문을 현장에서 직접 등록. **이 앱의 차별점.**
- ❌ **SO(STR, 재고이동요청) 작성 = 모바일에 두지 않는다** — STR 작성·제출은 웹. 모바일에서 SO는 **수령(receive)+조회**만.
- 사유: ① STR 작성은 '계획' 작업(라인 다수 입력)이라 웹이 적합 ② 모바일 차별점은 CO에 집중 ③ "웹=계획/결정, 모바일=현장 실행" 역할 분담이 선명.

> **출고 스캔(재고 차감)과 현장 수주(CO)는 서로 다른 흐름이다.** 절대 한 화면에 합치지 말 것.
> - **출고 스캔** = 정비 작업에 부품을 **소비** → 즉시 재고 차감(`POST /stocks/outbound`). CustomerOrder 레코드 아님.
> - **현장 수주(CO)** = 고객 주문/판매 건을 **기록**(`POST /customer-orders`, 상태 OPEN). 즉시 차감 없음(차감은 CO '종료'=웹 시점).

---

## 1. 사용자 / 역할

| 역할 | 코드 | 모바일 대상 | 비고 |
|---|---|---|---|
| 정비사 | `BRANCH_STAFF` | ✅ | 입고/출고/현장수주/재고/이력. **STR 본사 제출 불가** |
| 점장 | `BRANCH_MANAGER` | ✅ | 정비사와 동일(+ STR 제출은 웹) |
| 본사·관리자 | `HQ_*` / `ADMIN` | ❌ | 모바일 비대상(웹 전용) |

권위 역할값 출처: `GET /user/api/v1/users/me` 의 `role`. (게이트웨이 `/api/auth/me`에는 role 없음 — §4.1)

---

## 2. 정보구조 / 네비게이션

- **하단 탭바 4개**: `홈` · `재고` · `작업이력` · `마이`
- **홈 작업카드 3개**(현장 동선 진입점): `입고 스캔` · `출고 스캔` · `현장 수주`
- **푸시 화면**(스택): `입고 스캔` / `출고 스캔` / `현장 수주 등록`
- **오버레이**: 도착 대기 큐 바텀시트(모든 화면 헤더 우측 트럭 아이콘 + 홈 히어로에서 진입), 부품 검색 시트, 확인 모달
- 뒤로가기: 시트 열림→닫기 / 푸시 깊이>1→pop / 비-홈 탭→홈 탭 / 홈·로그인→더블백(2초) 종료

```
로그인 ─▶ [탭루트] 홈 ── 재고 ── 작업이력 ── 마이
              │
              ├─(작업카드)▶ 입고 스캔  → 수령 확정(SO receive)
              ├─(작업카드)▶ 출고 스캔  → 재고 차감(stocks/outbound)
              └─(작업카드)▶ 현장 수주  → CO 등록(customer-orders)
              └─(헤더 트럭)▶ 도착 대기 큐 시트 → 입고 스캔(프리셋)
```

---

## 3. 화면별 스펙

> 공통 상태 규칙(§5)을 모든 화면에 적용: **로딩 / 빈 / 에러+재시도 / 오프라인 / 401 재로그인 / (쓰기)성공 모달**. **성공 표시는 2xx 응답 이후에만.**

### 3.0 로그인
- **목적**: Keycloak OIDC 로그인(AppAuth + PKCE) → 신원 수신.
- **구성**: 브랜드 + "통합 계정(Keycloak)으로 로그인" 버튼. (사번/비번 입력은 데모 전용 — 운영은 OIDC 리다이렉트.)
- **API**: 로그인 성공 후 `GET /api/auth/me`(신원) → `GET /user/api/v1/users/me`(role·지점). §4.1
- **상태**: 인증 실패 → 재시도. 세션 만료(401) → 로그인 복귀.

### 3.1 홈 (M-HOME)
- **목적**: 현장 한눈 요약 + 작업 진입.
- **구성**:
  - 헤더: 이름·지점·직무 배지, 새로고침, **트럭 아이콘(도착 N건 → 큐 시트)**
  - 히어로: "도착 대기 **N건**"(SO `IN_FULFILLMENT`, 내 지점)
  - **작업카드 3개**: 입고 스캔 / 출고 스캔(오늘 출고 N건) / 현장 수주(오늘 수주 N건)
  - 재고 주의 위젯(안전재고 미달 N), 최근 입고 5건
- **API**: 도착=`GET /sales/.../sales-orders?status=IN_FULFILLMENT&to_warehouse_code={내창고}`; 재고주의=`GET /inventory/.../stocks?warehouseCode={내창고}&belowSafety=true`
- **주의**: "오늘 출고/수주 N건"은 **세션 표시값**(앱 재시작 시 리셋) — 서버 집계 아님. 디자인은 "오늘" 라벨로 한정.

### 3.2 입고 스캔 (M-SCAN-IN) — SO 수령
- **목적**: 본사가 보낸 보충(STR)을 현장에서 **전량 수령**.
- **흐름**: ① QR/발주번호 스캔 또는 수동 입력 → ② 발주 요약·라인 확인(ReceiveOrderForm) → ③ "전량 입고 확정" 모달 → ④ 성공.
- **API**: 조회 `GET /sales/.../sales-orders?status=IN_FULFILLMENT&to_warehouse_code` · 확정 `PATCH /sales/.../sales-orders/{soNumber}/receive` (IN_FULFILLMENT→RECEIVED)
- **상태**: 성공 모달(입고 완료 SO·품목수·남은 도착 N건) → "홈" / "작업 이력". 에러 `SO005`(IN_FULFILLMENT 아님) 등.
- **제약**: **라인별 부분 수령 없음 — 발주 전량만.** 디자인에 부분 입력 UI 두지 말 것.

### 3.3 출고 스캔 (M-SCAN-OUT) — 재고 차감 ★차별점
- **목적**: 정비 작업 **소비분**을 부품 스캔으로 즉시 차감.
- **흐름**: ① 부품 바코드 스캔(0.9s 인식) 또는 수동 입력 → ② 출고 폼 → ③ "출고 확정" 모달 → ④ 성공/부족.
- **출고 폼 구성**:
  - 부품 확인 카드(SKU · 품목명 · 분류)
  - **가용재고 배너**(현재고 · 안전재고 · 창고명)
  - **수량 스텝퍼**(1 ~ 가용재고; 초과 시 적색 경고 + 차감 거부)
  - **출고 사유 필수 선택**(3종 고정: 정비사용 / 판매 / 폐기·손망)
  - 출고 창고(읽기전용: 내 지점)
- **API**: `POST /inventory/api/v1/stocks/outbound` (§4.4) — referenceNumber(클라 생성 `CO-2026-XXXX`) = Idempotency-Key.
- **상태 분기**(설계 필수):
  - 200/2xx: "출고 완료 · 부품 1 {단위} 차감 · 사유 · 출고번호 · **남은 가용재고 X**" → "홈" / "계속 출고"
  - **409 `INSUFFICIENT_STOCK`**: "재고 부족 — 서버 가용재고 X" → "수량 조정" / "다시 시도" (**부분 차감 불가**)
  - 401: "다시 로그인", 오프라인: "다시 시도"(동일 referenceNumber로 멱등 재시도)
- **진입**: 홈 작업카드 + 재고 상세 시트 "출고" 버튼(부품 프리셋).

### 3.4 현장 수주 등록 (M-ORDER) — CO 작성 ★차별점
- **목적**: 현장 고객 작업/판매 건을 수주(CustomerOrder)로 **기록**.
- **흐름**: ① 고객/작업명 입력(선택) → ② 부품 라인 추가(스캔 또는 "검색으로 추가" 시트) → ③ 메모(선택) → ④ "등록" 모달 → ⑤ 성공.
- **구성**: 고객명 입력(공백 시 "현장 즉시판매") · 연락처(선택) · 부품 라인 리스트(SKU·수량 스텝퍼·삭제) · 메모.
- **API**: `POST /sales/api/v1/customer-orders` (§4.3) — Idempotency-Key = UUID(폼 진입당 1개, 더블탭 방지).
- **상태**: 성공 모달("수주 등록 · CO-xxxx · 오늘 N건") → "홈" / "계속 입력". 검증: 부품 라인 1개 이상 필수.
- **제약**: 모바일은 **작성(OPEN)까지만.** 확정(confirm)·종료(close=재고차감)는 웹. 디자인에 확정/종료 버튼 두지 말 것.

### 3.5 재고 조회 (M-INVENTORY)
- **목적**: 내 지점 재고 조회·검색.
- **구성**: 카테고리 칩 + 안전재고 미달 토글 + 검색(SKU·이름) / 부품 행(썸네일·이름·SKU·현재고·상태 배지) / 행 탭 → 부품 상세 시트(최근 입고 라인 + "출고" 버튼→출고 스캔 프리셋).
- **API**: `GET /inventory/api/v1/stocks?warehouseCode={내창고}&category=&belowSafety=&sku=` (페이지 순회).

### 3.6 작업 이력 (M-WORKLOG)
- **목적**: 내가 입고 확인한 발주 이력.
- **구성**: 검색(SO·창고·부품) + 기간 칩(30/90/365일) + 날짜 그룹(오늘/어제/N일전) / SO 행(상태 배지·합계) → 상세 시트.
- **API**: `GET /sales/.../sales-orders?received_by={내 사번}`.

### 3.7 마이 (M-MY)
- **목적**: 프로필·권한·로그아웃.
- **구성**: 프로필 카드(이니셜·이름·직무 배지·사번·이메일·**지점**) / 이용 권한(역할별: 정비사 — "발주(STR) 작성은 웹 전용" 안내) / 앱 정보 / 로그아웃.
- **API**: `GET /user/api/v1/users/me`(권위 role·지점명).
- **상태(중요)**: 지점(tenancy) 미매핑 시 "지점 매핑 대기" 안내 — §6 갭 참조.

### 3.8 도착 대기 큐 (바텀시트)
- **목적**: 도착 대기(IN_FULFILLMENT) 발주 빠른 확인.
- **구성**: "도착 대기 N건" + SO 행 목록 → 행 탭 → 입고 스캔(SO 프리셋, 스캔 스킵).

---

## 4. API 계약 (정확)

> 베이스 = `{게이트웨이}/{서비스프리픽스}/api/v1/...`. 서비스 프리픽스: `sales`, `inventory`, `user`. `/api/auth/me`는 게이트웨이 직속.
> 인증: **Bearer JWT**(Keycloak OIDC). 모든 호출에 `Authorization: Bearer {token}`.

### 4.1 인증 / 신원
```
GET /api/auth/me                  → 게이트웨이 OIDC 세션 신원 (role·지점 없음)
  200 { authenticated, keycloakSub, username, employeeNumber, displayName, email, position }

GET /user/api/v1/users/me         → 권위 ERP 프로필 (role·지점)
  200 { userId, keycloakSub, employeeNumber, displayName, email, position, status,
        role(BRANCH_STAFF|BRANCH_MANAGER|...), tenancyType(HQ|BRANCH), tenancyName(지점명), version }
```
- 로그인 상태/기본 프로필 = `/api/auth/me`. **role·지점명 = `/users/me`**.
- ⚠️ **창고코드(warehouseCode)는 어느 응답에도 없음** → 재고/출고가 쓰는 `warehouseCode` 출처가 미정(§6 갭).

### 4.2 SO (재고이동요청) — 조회 + 수령만 (작성 없음)
```
GET   /sales/api/v1/sales-orders ?status=IN_FULFILLMENT &to_warehouse_code={wh} &size=100   (도착 대기)
GET   /sales/api/v1/sales-orders ?received_by={employeeNumber} &size=100                     (내 입고 이력)
  200 { items:[ { soNumber, toWarehouseCode, toWarehouseName, status, priority,
                  requestedBy, approvedBy, receivedBy, requestedAt, approvedAt, receivedAt,
                  totalAmount, note } ], pagination:{ page,size,totalElements,totalPages } }

PATCH /sales/api/v1/sales-orders/{soNumber}/receive    (IN_FULFILLMENT → RECEIVED, body 없음)
  200 SalesOrderStatusChangeResponse
  409 SO005  IN_FULFILLMENT 상태만 수령 가능
```
- 상태 enum: `REQUESTED · SUBMITTED · IN_FULFILLMENT · BACKORDERED · RECEIVED · REJECTED · CANCELED`.
- 모바일 관여: `IN_FULFILLMENT`(수령 대상), `RECEIVED`(완료). 나머지는 조회 표시만.

### 4.3 CO (현장 수주) — 작성 (모바일 핵심)
```
POST /sales/api/v1/customer-orders        헤더: Idempotency-Key: {UUID}
  body {
    dealerWarehouseCode: string!,           // 내 지점 창고
    customerName: string!,                   // NotBlank, 공백이면 클라가 "현장 즉시판매"로
    customerContact: string?,                // 선택
    note: string?,                           // 선택
    lines: [ { sku: string!, quantity: int(>=1) } ]   // 최소 1건
  }
  201 { coNumber, status:"OPEN", dealerWarehouseCode, customerName, requestedBy, ... }
  409 IDEM001/002/003   멱등 키 충돌/중복(이미 처리됨 → 성공 취급, 목록으로)
```

### 4.4 출고 (재고 차감) — 출고 스캔
```
POST /inventory/api/v1/stocks/outbound    헤더: Idempotency-Key: {referenceNumber}
  body {
    referenceNumber: string!,               // 클라 생성 "CO-2026-XXXX" (멱등 기준)
    lines: [ { sku: string!, quantity: int(>0), warehouseCode: string!, unitPrice: 0 } ]
  }
  2xx (body 없음)  → 차감 성공. 남은 가용재고는 GET /stocks 재조회로 표시.
  409 INSUFFICIENT_STOCK  "재고가 부족합니다." (부분 차감 없음 — 한 라인만 부족해도 전체 실패)
```
- ⚠️ 이 엔드포인트는 원래 **sales가 CO 종료 시 호출하는 내부 출고**다. 모바일은 동일 엔드포인트를 **현장 직접 출고(정비 소비)**로 재사용 — referenceNumber는 CO 레코드가 아니라 출고 식별 라벨.

### 4.5 재고 조회
```
GET /inventory/api/v1/stocks ?warehouseCode={wh} &sku= &category= &belowSafety= &size=100
  200 { content:[ { sku, name, currentStock, availableStock, safetyStock, category, unit, warehouseCode } ],
        page, size, totalElements, totalPages }
```

---

## 5. 상태 · 에러 · 멱등 규칙 (전 화면 공통)

- **성공은 2xx 응답 후에만 표시** (낙관적 완료 금지). — 데이터 신뢰성 P0.
- **로딩 / 빈(내 지점 데이터 없음) / 에러+다시시도 / 오프라인+다시시도** 4상태 모든 목록·폼에 디자인.
- **401**: "세션 만료 — 다시 로그인" → 로그인.
- **409 분기 디자인 필수**:
  - 출고 `INSUFFICIENT_STOCK` → "재고 부족, 가용 X" + 수량 조정/재시도 (부분차감 불가 명시).
  - 수령 `SO005`, 멱등 `IDEM*`(중복=성공 취급).
- **멱등**: CO 작성=폼 진입당 UUID 1개; 출고=referenceNumber 자체가 키. 재시도 시 **동일 키** 재전송.

---

## 6. 알려진 갭 / 디자인 시 가정 (반드시 반영)

1. **창고코드(warehouseCode) — 클라 해석으로 해소(2026-06-24)** — `/api/auth/me`·`/users/me` 에 warehouseCode 없음(지점명 `tenancyName`만). → **로그인 후 모바일이 `GET /inventory/api/v1/warehouses` 에서 지점명(tenancyName) 일치 창고를 찾아 warehouseCode 를 보강**(Login → `InventoryRepository.resolveWarehouseByName`). 일치하면 재고/출고/수주가 정상 동작.
   → 디자인: "내 지점"은 **읽기전용 컨텍스트**. **이름 매칭 실패 시에만** "지점 매핑 대기" 상태(마이/재고) — 이 폴백 상태도 디자인 필요. (근본 해결은 백엔드 창고 클레임/매핑이지만, 현재는 이름축 매칭으로 동작.)
2. **부품 마스터 조회 API 없음** — 스캔/검색 후 부품 lookup이 현재 클라 시드. → 디자인은 **"부품 검색 시트"**(이름/SKU 검색→선택) 포함. (백엔드: 부품 검색 엔드포인트 필요.)
3. **CO 라이프사이클 = 모바일은 OPEN까지** — 확정/종료(차감) UI 두지 말 것(웹).
4. **부분 수령/부분 차감 없음** — 입고=전량, 출고=라인 전량(부족 시 전체 409).
5. **"오늘 N건" = 세션 표시값**(서버 집계 아님). "오늘" 한정 라벨.

---

## 7. 디자인 토큰 (웹 콘솔과 일관 — 동일 제품군)

| 토큰 | 값 | 용도 |
|---|---|---|
| Navy | `#002c5f` | 주요 버튼·헤더·중립 상태(SUBMITTED/OPEN) |
| Clay | `#a36b4f` | 강조·BACKORDERED·활성 |
| Success | `#1f7a4d` | 확정·IN_FULFILLMENT·RECEIVED·완료 |
| Danger | `#b3261e` | 취소·반려·재고부족 경고 |
| Tertiary | `#8a8178` | REQUESTED·휴면·보조 |
| Background | `#f6f3f2` | 앱 배경 |
| Radius | 4px(카드/버튼) · 6px(모달/시트) | |
| Font | Noto Sans KR + system, 숫자 `tabular-nums` | |

**표시 규칙**: 사람이 읽는 라인은 **품목명(nameSnapshot) 우선**, SKU는 보조(mono·회색). 수량/금액은 tabular-nums.

상태 배지 색: OPEN/SUBMITTED=navy · CONFIRMED/IN_FULFILLMENT/RECEIVED/CLOSED=success · BACKORDERED=clay · REJECTED/CANCELED=danger · REQUESTED=tertiary.

---

## 8. 시연 동선 (참고)

1. 정비사 로그인 → 홈(도착 N건)
2. **입고 스캔** → SO 전량 수령 → 도착 N−1
3. **출고 스캔** → 부품 차감 → 가용재고 감소 (+ 재고부족 SKU로 **409 차단** 1회 시연)
4. **재고** → 방금 차감 반영 확인
5. **현장 수주** → 고객+부품 → CO 등록 (★차별점)
6. 마이 → 권한("STR 작성은 웹")
7. (말로) 점장·본사 웹 = STR 작성·승인 → "웹=계획, 모바일=현장"

---

## 부록: A안에서 **만들지 않는** 화면 (혼동 방지)
- ❌ SO(재고이동요청) **작성/제출** 화면 — 웹 전용
- ❌ CO **확정/종료** 화면 — 웹(모바일은 작성만)
- ❌ 발주(PO)·작업지시(WO)·승인·예약·알림함 — 웹/HQ 전용
