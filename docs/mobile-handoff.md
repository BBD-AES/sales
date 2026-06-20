# Android 모바일 앱 — Claude Design 핸드오프

> 작성 2026-06-15 · 대상: 현대 파츠 통합 부품 ERP, **현장 정비사용 안드로이드 앱**
> 근거: 안내북 v2 Part 3 화면 정의서(M-* 6장) + 4개 서비스 실제 REST 엔드포인트 교차 검증
> 원칙: **엔드포인트가 있는 기능은 전부 모바일에 구현. 엔드포인트 없는 필수 기능은 §6 갭으로 분리(백엔드 협의 필요).**
>
> **결정 로그(2026-06-15)**
> 1. **M-SCAN-OUT(정비 사용 출고)는 이번 범위에서 보류.** 안내북상 "권한 모델 확장 학습 미션"이고 출고 엔드포인트 신설이 필요해 지금 안 함. 모바일 쓰기 동작은 **도착 입고 확인(receive) 하나**로 축소.
> 2. **백오더 설계 유지(line별 부족=대기, 품절=BACKORDERED).** ERP 정석. 단 ①품절→구매요청→입고→자동충족 루프를 데모 가능하게, ②안내북상 필수 범위 아님(도전 미션)이라 해피패스 우선, ③화면 배지는 안내북 6종으로 매핑.
> 3. **M-WORKLOG는 inventory 이동이력이 아니라 sales `received_by`로 구현.** StockMovement employee 필드 마이그레이션 불필요. `GET /api/v1/sales-orders?received_by=` 필터 추가 완료(커밋 fcfd88f).
> 4. **SO 작성(SO-05)은 웹 전용 확정 — 모바일 미지원.** Part 2(p90,148)·시드(SO-2026-0003 "모바일 등록")와 충돌하지만 **확정 산출물인 Part 3 화면 정의서(SO-05=웹 전용, p484)를 권위로 채택.** 시드의 "모바일 등록" 표기는 서술상 흔적일 뿐 SO 레코드 로드에 영향 없음. 모바일은 작성 화면 없이 재고 0/안전재고 미만 시 "웹에서 발주 요청" 안내만.

---

## 1. 앱 개요

| 항목 | 내용 |
|---|---|
| 플랫폼 | Android (네이티브) |
| 단일 사용자 | **BRANCH_STAFF (현장 정비사)** — 페르소나 정민수. BRANCH_MANAGER도 선택 허용. HQ_*·ADMIN·카운터 유형은 웹 전용(앱 사용 X) |
| 핵심 가치 | 손이 더러운 현장에서 **바코드 스캔**으로 입·출고를 즉시 처리(부품 코드 암기 불필요) |
| 화면 수 | **이번 범위 5장** (M-HOME / M-SCAN-IN / M-INVENTORY / M-MY / M-WORKLOG) + M-SCAN-OUT 보류(결정 로그 1) |
| 인증 | **Keycloak OIDC** (Authorization Code + PKCE, 네이티브 앱 권장). 모든 API 호출에 `Authorization: Bearer <JWT>`. 게이트웨이가 역할(Role/Tenancy) 전파 |
| 데이터 범위 | **자기 지점(Tenancy)만** — 서버가 타 지점 데이터를 404로 은닉. 앱은 소속 창고만 노출 |
| 해상도 | 세로 360~414px 기준, Android 12+ |
| 오프라인 | 카메라 권한 거부·미지원 시 **수동 코드 입력 자동 전환(필수)**. 네트워크 끊기면 액션 카드 비활성 + "네트워크 연결 필요". 오프라인 큐는 자유 영역 |

### 디자인 시스템 가이드(현장 특화)
- **큰 터치 타깃**: 장갑 낀 손·더러운 손 전제. 주요 버튼 최소 56dp, 액션 카드는 화면 폭 절반 이상.
- **스캔 우선**: 홈에서 입고 스캔이 가장 큰 카드. 카메라 뷰파인더는 전체 폭. (출고 스캔 카드는 이번 범위 보류 — 결정 로그 1)
- **상태 = 색 배지**: 재고 정상(녹)/부족(주황)/없음(적), 작업 구분 입고(↑녹)/출고(↓적)/조정(⟳회).
- **수량 스테퍼**: 직접 타이핑 대신 +/− 스테퍼 + 빠른 버튼(+1/+10). 현장에서 키보드 최소화.
- **바텀시트 상세**: 목록 → 탭 → 바텀시트로 상세(풀스크린 전환 최소화).
- **0건/누락 표기**: 0은 "0"으로 표시(숨김 금지), 빈 값은 "—". 빈 목록은 전용 메시지.

### 네비게이션
하단 탭바 4개: **홈 · 스캔 · 재고 · 마이**. (스캔 탭은 입고/출고 선택 시트)

---

## 2. 모바일 필수 vs 웹 전용 — 기능 분배

### 모바일에 **반드시** 있어야 (현장 업무)
| 기능 | 화면 | 엔드포인트 | 상태 |
|---|---|---|---|
| 로그인 | (Keycloak) | Keycloak OIDC | ✅ 외부 |
| 도착 입고 확인(전량) | M-SCAN-IN | `PATCH /api/v1/sales-orders/{soNumber}/receive` (sales) | ✅ |
| 바코드→부품 정보 | M-SCAN-IN/OUT | `GET /api/v1/items/{sku}` (item) | ✅ |
| 스캔 시 현재고 표시 | M-SCAN-IN | `GET /api/v1/stocks/{warehouseCode}/{sku}` (inventory) | ✅ |
| 재고 조회 | M-INVENTORY | `GET /api/v1/stocks*` (inventory) | ✅ |
| 내 작업 이력(내가 받은 SO) | M-WORKLOG | `GET /api/v1/sales-orders?received_by=&status=RECEIVED` (sales) | ✅ 구현완료 |
| ~~정비 사용 출고~~ | ~~M-SCAN-OUT~~ | 보류 | ⏸ 결정 로그 1 |
| 마이페이지 | M-MY | `GET /api/v1/users/internal/snapshot` (user) | ✅ |
| 비밀번호 변경 | M-MY | (Keycloak 계정 콘솔) | ✅ 외부 |

### 웹 전용 — **모바일에 넣지 않음**
| 기능 | 이유 |
|---|---|
| SO 작성(SO-05) | **웹 전용 확정**(결정 로그 4, Part 3 p484 권위). 모바일은 작성 화면 없이 "웹에서 발주 요청" 안내만 |
| SO 승인·거절(SO-02) | ADMIN·HQ_MANAGER 권한 — 모바일 사용자는 권한 없음 |
| SO 출고 처리(SO-03) | HQ 권한, 본사 웹 |
| 발주 목록(SO-01 본사 / SO-04 지점) | 웹 목록 화면 |
| 재고 조정(IV-02) | ADMIN·HQ_MANAGER 전용, 웹 모달. **모바일은 조정 액션 없음** |
| 구매(PO-*) 전체 | HQ 웹, BRANCH 전면 차단 |
| 부품 마스터(IM)·창고(WH)·사용자(US)·대시보드(DA) | 본사/관리 웹 |

> 핵심: 모바일은 **현장 입·출고 + 조회**만. 작성·승인·결재·관리는 전부 웹.

---

## 3. 화면별 상세 스펙

### M-HOME — 모바일 홈
- **목적**: 현장 도착 즉시 "오늘 할 일" 파악 + 스캔 진입.
- **데이터**: `GET /api/v1/sales-orders?status=IN_FULFILLMENT&to_warehouse_code={myWh}` (도착 대기 건수), `GET /api/v1/sales-orders?received_by={me}&status=RECEIVED` (최근 활동).
- **구성**:
  - 헤더: 사용자명·소속 지점 + 알림 벨(배지, NT 미도입 시 0/숨김).
  - 인사 + "오늘 처리할 항목 · 도착 대기 N건" 칩.
  - **큰 액션 카드**: [입고 스캔] (카메라 권한 거부 시 수동 입력 모드 자동 전환). 출고 스캔 카드는 이번 범위 보류(결정 로그 1).
  - 보조 카드 2개: [재고 조회] [내 작업 이력].
  - 최근 활동 3행 + "더보기"(→ M-WORKLOG).
  - 하단 탭바 4(홈·스캔·재고·마이).
- **상태**: 도착 대기 0건 → "도착 대기 항목이 없습니다". 네트워크 끊김 → 액션 카드 비활성.

### M-SCAN-IN — 입고 스캔 (= SO-06 도착 입고 확인의 모바일 흐름)
- **목적**: 도착한 박스 바코드 스캔 → 발주 연결 입고 확인.
- **데이터**:
  - 바코드(=SKU) → `GET /api/v1/items/{sku}` (부품명·코드) + `GET /api/v1/stocks/{myWh}/{sku}` (현재고).
  - 관련 발주: `GET /api/v1/sales-orders?status=IN_FULFILLMENT&to_warehouse_code={myWh}` 중 해당 SKU 포함 건.
  - 확정: `PATCH /api/v1/sales-orders/{soNumber}/receive` (전량 수령, body 없음).
- **구성**:
  - 카메라 뷰파인더(전체 폭) + 플래시 토글 + "수동 입력" 토글.
  - 스캔 결과 카드: 부품명 · 코드 · 현재고.
  - 수량 스테퍼(필수) · 수신 창고(자기 지점 활성, 필수) · **관련 발주 드롭다운**(자기 지점 IN_FULFILLMENT만, 잔여 초과 차단) · 메모.
  - 발주 연결 시: receive 확정 → IN_FULFILLMENT → RECEIVED 전환(전량 수령). **모바일은 SO 연결 입고만 지원**(미연결 일반 입고은 범위 외).
  - 확정 후: "M-HOME 복귀 또는 계속 스캔" 선택 모달.
- **에러**: 미등록 부품·비활성 부품 안내, 잔여 초과 차단, 409 동시 처리 재조회.

### M-SCAN-OUT — 출고 스캔 (정비 사용 출고)  ⏸ 이번 범위 보류
- **결정(로그 1)**: 출고 엔드포인트 신설이 필요한 "확장 미션"이라 이번 범위에서 **구현하지 않음.** 화면도 만들지 않음(홈 카드 비노출).
- **추후 재개 시 스펙**(참고용 보존): 뷰파인더 + 수량(현재고 이하) · 출고 창고 · 사용 사유(수리·교환·검사·기타) · 작업번호 → MovementType.OUT. 선행: inventory에 사용 출고(consume) 엔드포인트 신설.

### M-INVENTORY — 재고 조회  ✅
- **목적**: 현장에서 부품 재고 즉시 확인.
- **데이터**: 목록 `GET /api/v1/stocks?warehouseCode={myWh}&sku=&category=&belowSafety=&page=&size=`, 상세 `GET /api/v1/stocks/{sku}`(창고별), 단건 `GET /api/v1/stocks/{warehouseCode}/{sku}`.
- **구성**:
  - 요약 카드(부족·없음 배지 개수).
  - 검색바 + 바코드 아이콘(스캔으로 검색) + 상태 칩(전체/정상/부족/없음).
  - 카드 리스트: 부품명·코드·현재고·안전재고·상태 배지. **무한 스크롤 + pull-to-refresh**.
  - 탭 → 부품 상세 **바텀시트**: 창고별 재고 표 + 최근 이동 5건.
  - **조정 액션 없음**(조정은 웹 IV-02 전용).
- **상태**: 0건 → 빈 메시지. 응답 <2초.

### M-MY — 마이페이지  ✅
- **목적**: 본인 정보 확인 + 보안/로그아웃.
- **데이터**: `GET /api/v1/users/internal/snapshot?keycloakSub={sub}` (이름·사번·소속·Role·상태). 비밀번호 변경은 Keycloak 계정 콘솔.
- **구성**:
  - 프로필 카드(이름·사번·이메일·소속·Role 배지 — 표시 전용, "수정은 관리자 문의").
  - 내 작업 이력 5건(→ M-WORKLOG).
  - 비밀번호 카드(→ Keycloak), 알림 설정(NT 도입 시), 로그아웃(확인 모달).

### M-WORKLOG — 내 작업 이력  ✅
- **목적**: 본인이 도착 확인(receive)한 SO 이력 조회.
- **데이터(결정 로그 3)**: `GET /api/v1/sales-orders?received_by={me}&status=RECEIVED&start_date=&end_date=&page=&size=`. inventory StockMovement 대신 **sales의 `received_by` 필터**(커밋 fcfd88f 구현)로 "본인 작업만"을 백엔드에서 강제. employee 필드 마이그레이션 불필요.
- **구성**:
  - 검색 + 기간 칩(기본 30일·최대 365일).
  - 날짜 그룹 헤더 + 작업 카드(SO 번호·도착일·품목 수·출발 창고).
  - 탭 → SO 상세(`GET /api/v1/sales-orders/{soNumber}`) 바텀시트.
  - "기간 · 총 N건" 요약 항상 노출.
- **참고**: 출고 스캔이 범위에 들어오면 그때 사용 출고 이력을 별도 소스로 합칠지 재검토(현재는 단일 소스).

---

## 4. 공통 UX 규칙 (전 화면)

> **SO 상태 enum 주의(중요)**: 모바일이 호출하는 sales API의 실제 상태값은 안내북 6종과 다르다.
> 실제 enum = `REQUESTED / SUBMITTED / IN_FULFILLMENT / BACKORDERED / REJECTED / CANCELED / RECEIVED`.
> 쿼리·필터는 **반드시 이 실제 값**을 쓴다(안내북의 SHIPPED/DELIVERED 아님). 모바일 관련 값은 **IN_FULFILLMENT**(=출고되어 도착 대기), **RECEIVED**(=도착 확인 완료).
> **화면 배지 표기**만 안내북 용어로 매핑(결정 로그 2): IN_FULFILLMENT→"도착 대기", RECEIVED→"입고 완료". BACKORDERED는 모바일에 거의 안 보임(HQ/웹 관심).

- API 대기 중 버튼 비활성 + 스피너 + 중복 제출 차단.
- 상태 변경(입고 확정·출고 확정)은 확인 모달 경유.
- HTTP 분기: 400 인라인 / 403 토스트("권한이 없습니다") / 404 "대상 없음" / 409 "다른 사용자가 먼저 처리"+재조회 / 5xx "일시적 오류"+재시도.
- 토큰 만료 → 로그인 화면 복귀 + 작성 내용 폐기.
- 페이지네이션: 모바일은 **무한 스크롤(20행/페이지)**.
- 기간 필터 기본 30일·최대 365일.
- 비활성 부품: 안내 문구 + 신규 작업 차단(이력엔 정상 표시).

---

## 5. 엔드포인트 매핑 요약 (있는 건 전부 구현)

| 모바일 동작 | 서비스 | 엔드포인트 |
|---|---|---|
| 부품 단건 조회(스캔 결과) | item | `GET /api/v1/items/{sku}` |
| 부품 자동완성(수동 검색) | item | `GET /api/v1/items/search/auto?keyword=&size=` |
| 부품 다건 조회 | item | `GET /api/v1/items?sku=A&sku=B` (최대 50) |
| 재고 단건/창고별/목록 | inventory | `GET /api/v1/stocks/{wh}/{sku}`, `/stocks/{sku}`, `/stocks?...` |
| 활성 창고 목록 | inventory | `GET /api/v1/warehouses/active` |
| 도착 대상 SO 목록 | sales | `GET /api/v1/sales-orders?status=IN_FULFILLMENT&to_warehouse_code=` |
| 내 작업 이력(받은 SO) | sales | `GET /api/v1/sales-orders?received_by=&status=RECEIVED` |
| SO 상세 | sales | `GET /api/v1/sales-orders/{soNumber}` |
| 도착 입고 확인 | sales | `PATCH /api/v1/sales-orders/{soNumber}/receive` |
| 내 정보 | user | `GET /api/v1/users/internal/snapshot?keycloakSub=` |
| 로그인/비번 | Keycloak | OIDC / 계정 콘솔 |

---

## 6. 미해결 — 필수 범위인데 확인 필요 (도전/확장 제외)

> 도전·확장 미션(부분 입고/차이 사유, 정비 사용 출고, 미연결 일반 입고)은 이번 범위에서 제외하여 갭에서 뺐다.
> SO 작성 클라이언트 충돌은 **웹 전용으로 확정**(결정 로그 4)되어 갭에서 종결. 이번 범위 기준 receive(전량 도착 확인)·재고 조회·worklog는 모두 엔드포인트가 충족된다.
> 아래는 **구현 전 확인하면 좋은 가정** 2건뿐(블로커 아님).

**6-1. 바코드 = SKU 가정** ℹ️
item은 SKU 조회만, 바코드 전용 필드·조회 없음. **스캔한 바코드 페이로드를 SKU로 직접 사용**한다는 전제로 설계. 물리 바코드가 SKU와 다른 체계면 매핑 테이블 필요 → 확인.

**6-2. 마이페이지 조회가 internal-snapshot뿐** ℹ️ → user팀 요청 발행
`GET /api/auth/me`는 모바일(Bearer)에서 동작 안 하고 role·tenancy도 없음. ERP 프로필을 다 주는 건 `GET /users/internal/snapshot?keycloakSub=`(internal 전용·파라미터 방식)뿐. → **user팀에 `GET /api/v1/users/me` 신설 요청 작성**([user-me-endpoint-request.md](./user-me-endpoint-request.md)). 임시로는 토큰 sub로 internal/snapshot 호출(self-match로 안전).
