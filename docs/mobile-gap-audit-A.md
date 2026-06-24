# 모바일 갭 감사 — A안 핸드오프 ↔ 프로토타입 / Compose 앱

> 기준: [mobile-design-handoff-A.md](./mobile-design-handoff-A.md) (A안: 모바일=현장 실행, CO 작성 O / SO 작성 X)
> 대조 대상: ① 프로토타입 `~/Downloads/BBD Mobile.html`(+ design_handoff_bbd_mobile JSX) ② Compose 앱 `BBD-mobile`
> 작성 2026-06-24.

## 0. 한 줄 결론
**프로토타입과 Compose 앱이 서로 다른 세대다.** 프로토타입은 **구(舊) 방향**(보충발주 PO 있음·현장수주 CO 없음·탭 5개·409 미표시), **Compose 앱이 A안에 훨씬 가깝다**(CO 있음·PO 없음·409 완벽·역할초과 없음). **시연은 Compose로 돌아가므로** → 시연 차단점은 Compose의 `warehouseCode` 1건뿐, 나머지는 소소. 프로토타입은 Compose/A안에 맞춰 **정렬(역방향 아님)**.

---

## 1. Compose 앱 (★ 시연 대상 — 우선순위 높음)

| # | 화면 | 차원 | 심각도 | 갭 | 권장 |
|---|---|---|:--:|---|---|
| C1 | 마이·재고·출고·수주 **전체** | 역할·지점 매핑 | 🔴 **HIGH** | **`warehouseCode` 빈값.** `/api/auth/me`·`/users/me` 어디에도 창고코드 없음 → `Model.kt:141 warehouse=""`("날조 금지"). **API 모드에서 재고조회·출고·수주·도착조회 전부 빈 창고코드로 실패.** 시드 모드(Seed.USER 고정값)에서만 동작. | **시연=seed 모드로 회피**(권장). API 모드 쓰려면 tenancyName→warehouseCode 해석(로그인 후 `/inventory/warehouses` 매칭) 또는 백엔드 창고 클레임. |
| C2 | 입고 스캔(Scan.kt) | 표시 | 🟡 MED | `Scan.kt:177` **"출고 스캔은 준비 중…백엔드 미연동(보류)"** 잔존 안내문 — 출고는 ScanOut.kt에 완전 구현돼 있는데 구식 문구. 시연 중 혼동. | 안내문 Row 제거. **(본 작업에서 수정)** |
| C3 | 현장 수주(Order.kt) | 멱등 | 🟢 LOW | CO 작성 Idempotency-Key를 `CustomerOrderRepository:40`에서 **매 호출 새 UUID** 생성. 핸드오프 §4.3=폼 진입당 1개(재시도 시 동일 키). 현재는 UI `submitting` 잠금으로 더블탭은 막히나 키 안정성 미흡. | 폼 진입 시 UUID 1개 `remember`→repo에 전달. **(본 작업에서 수정)** |
| C4 | 출고/수주 스캔 | 부품 마스터 | 🟢 LOW | 스캔 후 부품 lookup이 `Seed.PARTS`(클라). 실 환경엔 부품 검색 API 필요. | 백엔드 `GET /inventory/.../parts?name=&sku=` 신설(후속). 시연은 시드 충분. |
| ✅ | 출고 스캔 409 | 상태분기 | — | **409 `INSUFFICIENT_STOCK`→`OutboundResult.Insufficient`→'재고부족·가용 X·수량조정' 분기 + referenceNumber 멱등 + 부분차감 없음 = 완벽 구현.** | 변경 불필요 |
| ✅ | 역할 범위 | 역할분담 | — | **SO 작성/제출·CO 확정/종료·PO/WO 화면 없음 = A안 준수.** | 변경 불필요 |

## 2. 프로토타입 (BBD Mobile.html / JSX — 디자인 정렬 대상)

| # | 화면 | 심각도 | 갭(핸드오프 대비) | 권장 |
|---|---|:--:|---|---|
| P1 | **현장 수주(CO)** | 🔴 HIGH | M-ORDER 화면 **자체가 없음**(Compose엔 있음). | CO 등록 폼 추가(고객명·라인·메모·등록. 확정/종료 금지). |
| P2 | **홈 / 보충발주** | 🔴 HIGH | 홈에 **'보충 발주(PO)' 미니카드 + OrderScreen** = A안 **초과**(웹 전용). | PO 카드·OrderScreen 제거. 작업카드=입고·출고·현장수주 3개만. |
| P3 | 탭바 | 🔴 HIGH | 탭 **5개(홈·스캔·재고·마이, 작업이력 제거)** vs A안 4개(홈·재고·작업이력·마이). 스캔은 탭이 아니라 작업카드. | 탭 4개로, '스캔'은 홈 작업카드/헤더 진입. |
| P4 | 출고 스캔 | 🔴 HIGH | **409 재고부족 분기 미표시**(로컬 초과 경고만). 401·offline 분기도 없음. | 409/401/offline 상태 모달 추가(Compose 참고). |
| P5 | 마이 | 🟡 MED | 지점 하드코딩, **'매핑 대기' 분기 없음**. | tenancy 미매핑 배너 추가. |
| P6 | 토큰 | 🟡 MED | 배경 `#eef1f7` vs A안 `#f6f3f2`; 출고 `#26408f` vs navy `#002c5f`. | 토큰값 핸드오프로 통일. |
| P7 | 입고 스캔 | 🟢 LOW | 수량 스테퍼 편집 가능(전량만이어야). | 입고 수량=발주값 고정/disabled. |
| P8 | 도착 큐 | 🟢 LOW | 트럭 아이콘 진입점 없음(종 아이콘만). | 헤더 트럭 + 큐 시트 진입 추가. |

---

## 3. 권장 액션 (우선순위)
1. **(시연 차단)** C1 `warehouseCode` — **시연은 seed 모드**로 진행(즉효). API 모드 필요 시 tenancyName→warehouseCode 해석 구현(별도).
2. **(즉시·안전)** C2 잔존 안내문 제거 + C3 CO 멱등 키 폼-고정 → **본 작업에서 Compose 수정**.
3. **(디자인)** 프로토타입을 Compose/A안에 정렬: P1(CO 추가)·P2(PO 제거)·P3(탭 4개)·P4(409 분기)가 핵심. → 디자인에 P1~P8 전달.
4. **(후속·백엔드)** 부품 마스터 검색 API(C4/P 공통), warehouseCode 클레임/매핑(C1).
