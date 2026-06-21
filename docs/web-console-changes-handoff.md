# 웹 콘솔 변경 — Claude Design 핸드오프 (현재본 기준)

> 작성 2026-06-21 (재작성) · 대상: **`BBD-AES Console.dc.html`(현재 구현, 2,742줄 단일 DC 파일)** · 확정 후 frontend-react 반영
> ⚠️ 이전 버전 이 문서는 구버전(`app/*.jsx` 분리본) 기반이라 폐기. **현재본은 이미 잘 만들어져 바꿀 게 적습니다.**

## 현재본 상태 — 양호
생산(WO)·발주요청·생산요청 화면, 재고조정 별도 권한(CAPS `adjustStock`), 로딩/빈/에러 상태, 상태 enum 충실(SO 7·CO 4종), 지점 스코프, 데이터 정직 고지(알림 읽음·사용자 생성=외부 IdP)가 **이미 반영**됨. README 자체가 충실한 핸드오프. → 아래는 **남은 소수 변경**만.

## 남은 변경

### 1. 제출 철회(withdraw) 버튼 추가 — 보충요청 상세
sales 백엔드에 `withdraw`(SUBMITTED→되돌림, 예약 반납) 있으나 콘솔 SO 액션은 제출/승인/출고/입고/반려/취소뿐. cancel이 SUBMITTED까지 CANCELED로 처리해 **철회와 취소가 합쳐짐**.
- **SUBMITTED** 상태 SO 상세에 `제출 철회` 버튼(취소와 구분). 권한: 제출자(BRANCH_MANAGER)·ADMIN. 취소는 REQUESTED, 철회는 SUBMITTED로 상태별 분기.

### 2. 입출고 'ADJUST' 유형 추가 — 입출고 내역
MOVES type이 IN/OUT 2종뿐(실사보정도 IN/OUT로 기록) → 실제 StockMovement는 IN/OUT/**ADJUST** 3종. 조정 이동이 원장에서 구분 안 됨.
- movement 배지·필터에 **ADJUST(조정)** 추가, 실사보정이 type=ADJUST로 기록되게.

### 3. SO/CO 작성 버튼 권한 게이트 — `correctness` (라이브 403 위험)
CAPS에 `createSo`/`createCo` 플래그가 없어 작성 버튼이 권한 게이트 없이 노출됨. HQ_MANAGER도 `sales-orders`·`customer-orders` 뷰 접근 가능(ALLOWED) → **HQ 페르소나에서 '보충요청/수주 등록' 버튼이 보이면 백엔드 POST(BRANCH_STAFF·BRANCH_MANAGER·ADMIN만)와 충돌, HQ는 403.**
- CAPS에 `createSo`/`createCo`(BRANCH_*·ADMIN) 추가하고 작성 버튼을 그걸로 게이트. (HQ는 읽기/결정만 — SO 작성=지점 전용)

### 4. 접근성 보강 — `ux` (확정 결함)
현재 `aria-*`·`tabindex`·`onKeyDown` **0건**:
- 표 행 선택 **키보드 지원**(role=button·tabIndex·Enter), 슬라이드오버 **포커스 트랩**+초기 포커스, SkuPicker **↑↓/Enter**, 아이콘 버튼 aria-label.

## 우선순위
| P | 항목 |
|---|---|
| P1 | 3 SO/CO 작성 버튼 권한(라이브 403) · 1 withdraw · 2 ADJUST 유형 |
| P2 | 4 접근성 |

## 제외 (만들지 말 것)
- **창고 간 이동(transfer)** — 현재본이 뺀 게 옳음. ① inventory에 엔드포인트 없음(데이터 정직성) ② SHIPPED·배송중 없이 승인→도착확인으로 끝나는 단순 모델에 in-transit transfer는 부정합 ③ 현대 파츠는 본사 중앙창고→지점 분배 모델이라 지점끼리 당기지 않음(부족 시 HQ 보충요청=이미 있음). 재도입 불필요.

> 유지(바꾸지 말 것): 생산/요청 화면, 상태 enum, 권한 CAPS 모델, 지점 스코프, 데이터 정직 고지, 콘솔 밀도·상태색.
