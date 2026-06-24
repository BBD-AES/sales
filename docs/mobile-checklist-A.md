# 모바일(A안) 체크리스트 — 구현 / 연동

> 두 축을 따로 체크: **구현**(Compose UI·로직 완성) / **연동**(실 API LIVE 동작 검증, USE_API=true).
> `[x]` 완료 · `[ ]` 미완 · `[~]` 부분/미검증. 기준: [mobile-design-handoff-A.md](./mobile-design-handoff-A.md) · 감사: [mobile-gap-audit-A.md](./mobile-gap-audit-A.md).
> 클로드 처리분=모바일 PR 링크. (모바일 레포 BBD-AES/mobile)

## 🔐 인증 / 신원
- [x] 구현 · [~] 연동 — **로그인 OIDC(Keycloak/PKCE)** — AppAuth. 연동=토큰 교환·리다이렉트 LIVE 검증.
- [x] 구현 · [~] 연동 — **신원 조립** = `/users/me`(권위 role+지점명) → 실패 시 `/api/auth/me` 폴백. 연동=LIVE 응답 확인.
- [x] 구현 · [ ] 연동 — **창고코드 매핑(C1)** — 지점명(tenancyName)→`GET /inventory/.../warehouses` 이름매칭→warehouseCode 보강(Login·InventoryRepository.resolveWarehouseByName). **모바일 PR**. 연동=LIVE 이름매칭 동작 + inventory warehouses 응답형태 실측 필요.

## 📥 입고 스캔 (SO 수령)
- [x] 구현 · [~] 연동 — QR/발주번호 스캔→전량 수령 폼→`PATCH /sales/.../{so}/receive`. 연동=LIVE receive 상태전이(IN_FULFILLMENT→RECEIVED) 검증.
- [x] 구현 · — — 전량만(부분 수령 없음) 제약 준수.

## 📤 출고 스캔 (재고 차감) ★
- [x] 구현 · [~] 연동 — 부품 스캔→수량·사유→`POST /inventory/.../stocks/outbound`(referenceNumber=멱등키). 연동=warehouse 매핑(C1) 의존 + LIVE 차감 검증.
- [x] 구현 · [x] 연동 — **409 INSUFFICIENT_STOCK 분기**(재고부족·가용 X·수량조정) + 401 + offline. 코드 완비(감사 ✅).
- [x] 구현 · — — 부분 차감 없음(한 라인 부족=전체 실패) 준수.

## 🧾 현장 수주 (CO 작성) ★
- [x] 구현 · [~] 연동 — 고객명(공백→'현장 즉시판매')·부품 라인·메모→`POST /sales/.../customer-orders`. 연동=warehouse 매핑 의존 + LIVE 생성 검증.
- [x] 구현 · — — **멱등 키 폼-고정(C3)** — 폼 진입당 UUID 1개 재사용(`Order.kt idemKey`→repo). **모바일 PR**.
- [x] 구현 · — — 확정/종료 버튼 없음(작성 OPEN까지만) 준수.

## 📦 재고 조회
- [x] 구현 · [~] 연동 — `GET /inventory/.../stocks?warehouseCode` 필터·검색·상세→출고 연계. 연동=warehouse 매핑 의존.

## 🗂 작업 이력
- [x] 구현 · [~] 연동 — `GET /sales/.../sales-orders?received_by={emp}` 기간·검색. 연동=LIVE 검증.

## 👤 마이
- [x] 구현 · [~] 연동 — 프로필·권한(정비사='STR 작성은 웹')·로그아웃.
- [x] 구현 · [~] 연동 — **지점 매핑 대기 폴백** — 창고 이름매칭 실패 시 'My.kt 매핑 대기' 안내. 연동=매칭 성공/실패 양 케이스 검증.

## 🚚 도착 대기 큐
- [x] 구현 · [~] 연동 — 헤더/홈 진입 시트, SO 행→입고스캔 프리셋.

## 🧹 정리(시연 안정화)
- [x] 구현 · — — **잔존 안내문 제거(C2)** — Scan.kt '출고 준비 중…(보류)' 삭제. **모바일 PR**.
- [ ] · — — **부품 마스터 검색 API** (C4) — 현재 스캔 후 lookup=시드. 백엔드 `GET /inventory/.../parts?name=&sku=` 필요(준성/inventory).

## 🎨 프로토타입 정렬 (디자인 — handoff 기준으로 BBD Mobile.html 수정)
- [ ] 디자인 — P1 현장수주(CO) 화면 **추가**(프로토타입에 없음)
- [ ] 디자인 — P2 홈 '보충발주(PO)' 카드·OrderScreen **제거**(A안 초과)
- [ ] 디자인 — P3 탭 **4개**(홈·재고·작업이력·마이)로, '스캔'은 작업카드/헤더
- [ ] 디자인 — P4 출고 스캔 **409/401/offline 분기** 표시 추가
- [ ] 디자인 — P5 마이 '지점 매핑 대기' 배너
- [ ] 디자인 — P6 토큰값 통일(배경 #f6f3f2, navy #002c5f)
- [ ] 디자인 — P7 입고 수량 고정(전량) · P8 도착큐 트럭 진입점

## 🔴 시연 운영
- [~] — **시연 모드 결정**: API 모드는 warehouse 이름매칭(C1) LIVE 검증 후, 미검증 시 **seed 모드**로 시연(전 기능 안정).
