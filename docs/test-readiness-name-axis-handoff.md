# [요청] 데모/테스트 성공을 위한 데이터 정렬 (이름축 유지)

> 작성 2026-06-22 · 요청: **sales팀** → 대상: **신원(user)팀 · inventory팀** (procurement는 e2e 구매 데모 시) · item = sales가 완료
> 결정: **테넌시 인가는 이름축(`tenancyName`/창고이름) 유지** — 코드축(tenancyCode) 도입 안 함.

## 정렬 원리 (이름축이라 "이름 한 줄"이 세 곳에서 글자까지 같아야 함)
지점 사용자가 자기 주문에 접근하려면 런타임에 이 셋이 **byte-identical** 이어야 함:

```
user-service tenancyName  ==  inventory Warehouse.name  ==  주문에 박힌 스냅샷(dealerName/toWarehouseName)
        (인가 앵커)              (생성 시 sales가 스냅샷)         (생성 시점에 얼어붙음)
```

→ 한 글자(공백·번호·꼬리표)만 달라도 즉시 403. 그래서 **canonical 지점명 1개를 정하고 모두 거기에 맞추는 것**이 전부.

추가로, 주문 생성/충족이 되려면 **참조 데이터가 존재**해야 함: ① item에 SKU, ② inventory에 그 창고코드(이름 해석용). (재고 수량은 배포가 stub 모드라 OK.)

---

## 팀별 할 일

### ✅ item — sales가 완료 (2026-06-22, admin 토큰으로 시드)
canonical SKU 7종 POST 완료 + sales 해석 검증(200). **추가 작업 없음.**
| SKU | 이름 | 단가 | category | sourcing |
|---|---|---|---|---|
| OIL-FLT-001 | 오일필터 | 3200 | ENGINE_FILTER | BUY |
| BRK-PAD-RR-001 | 브레이크 패드 (후륜) | 35000 | BRAKE | BUY |
| BAT-12V-60 | 배터리 12V 60Ah | 95000 | ELECTRICAL | BUY |
| RLY-12V-30A-01 | 릴레이 12V 30A | 8500 | ELECTRICAL | **BUY**(무재고→구매 분기 시연) |
| TIR-225-45-17 | 타이어 225/45 R17 | 135000 | TIRE_WHEEL | BUY |
| WSH-FLU-2L | 워셔액 2L | 4500 | WIPER_WASHER | BUY |
| CLT-DSK-MED-01 | 클러치 디스크 (중형) | 78000 | POWERTRAIN | **MAKE**(부족→생산 분기 시연) |

### ✅ 신원(user) — 완료 (2026-06-22 확인)
- [x] **BR001 `tenancyName` → `강남 1지점`** (꼬리표 `Phase61 175544` 제거됨, version 7→9). user-service 코드가 만든 게 아니라 외부 SCIM 프로비저닝이 박은 stale였음(`tenancyName`은 `ScimPatchMapper`로만 입력).
- [x] **BR002 status PENDING → ACTIVE**.
- [ ] (잔여 권고) **SCIM 프로비저닝 소스(midPoint 등)가 데모 유저 `tenancyName`에 테스트 태그를 안 붙이게** — BR001이 v9까지 churn된 원인. 재발 시 또 깨짐.
- 참고: SCIM PATCH·`/internal/snapshot`은 service-account 게이트(admin 토큰 403)라 `tenancyName` 수정은 신원팀만 가능.

### ✅ inventory 창고 마스터 — sales가 완료 (2026-06-22, admin 토큰)
배포 inventory엔 손POST 정크만 있었음(`WH-HQ-001`=`바꿨다`, `WH-BR-001`=`창고~`, `WH-BR-002`=`창고~~~~~`, `WH-01`=`서울본점`; `WH-BR-003/004` 부재 — `LocalDataSeeder`가 `@Profile("local")`이라 배포엔 미적재). 전부 정리(PUT/POST):
| code | name | type |
|---|---|---|
| WH-HQ-001 | 본사 중앙창고 | HQ |
| WH-BR-001 | 강남 1지점 | DEALER |
| WH-BR-002 | 분당 1지점 | DEALER |
| WH-BR-003 | 부산 1지점 | DEALER |
| WH-BR-004 | 대구 1지점 | DEALER |
sales 해석 검증(`GET /warehouses/{code}` → canonical 200) + **WH-BR-001 name == BR002 tenancyName(`강남 1지점`) MATCH**. (잔여 정크 `WH-01 서울본점`은 미참조라 방치; 필요시 deactivate.)
- ⚠️ **재고 충족 흐름 미동작(e2e 확인)**: 배포 `inventory.mode=stub`인데 **stub의 `reserve`가 항상 0 반환**(`availability`는 999인데 reserve=0, WH-HQ-001에 대해서도) → **모든 SO가 approve 시 BACKORDERED로 막힘**, IN_FULFILLMENT/RECEIVED 도달 불가. inventory 실 stock 테이블도 비어있음(`totalElements=0`). **SO 충족까지 시연하려면**: inventory를 `rest` 모드 + 창고별 실재고 시드, 또는 stub `reserve`를 `availability`와 일치(`min(qty,available)`)하게 수정. (CO close·멱등은 reserve 무관이라 정상 동작.)

### 🟩 sales팀 (우리 몫 — 참고)
- [ ] `data.sql`의 주문 스냅샷(dealerName/toWarehouseName)을 canonical과 일치 정렬(현 `강남 1지점` 기준이면 OK, 신원팀 canonical에 맞춰 확정).
- [ ] **기존 오염 주문 재생성**: `창고~`로 스냅샷된 라이브 주문(SO-2026-0007/0010 등)은 이름이 얼어붙어 못 고침 → 정렬 후 **신규 주문을 만들어** 데모에 사용하거나, 두 테이블 TRUNCATE 후 `data.sql` 재기동.
- [ ] SKU 참조: ✅ 해결됨(item 시드 완료).

### 🟦 procurement팀 — e2e 구매 데모 시에만 (sales 단독 테스트엔 non-blocking)
- [ ] `sales.purchase-requested`(warehouseCode + sku + soNumber) 소비 시 **canonical 창고코드/SKU 수용**(현 시드는 `SKU-1001`/`WH-002` 등 다른 네임스페이스).
- [ ] actor 컬럼이 V14에서 **BIGINT userId**로 바뀜 → 문자열 사번 넣지 말 것(시드 로드 실패).

---

## 정렬 후 검증 시나리오 (sales가 이렇게 확인)
1. **지점 가시성**: br_mgr(BR001)로 `GET /sales-orders`·`/customer-orders` → 자기 지점(강남) 주문 **200**(현재 빈 목록/403).
2. **상세/쓰기**: br_mgr가 자기 강남 주문 상세 200, 타 지점 403.
3. **생성 happy-path**: br_mgr가 SO 생성(OIL-FLT-001 등) → submit → (HQ)approve → receive.
4. **백오더 분기**: RLY-12V-30A-01(stub 재고 0) 주문 → approve 시 BACKORDERED → purchase-requested 발행.
5. **CO close(#69)**: CO 생성 → confirm → close → stub no-op 차감 + CLOSED.

→ 1번이 통과하면 이름축 앵커 정렬 성공(= 신원팀 tenancyName 정리가 핵심).

**✅ 검증 결과(2026-06-22)**: br_mgr 목록 SO 3·CO 1건 노출(전 0), 자기지점 CO-0001/SO-0002 **200**(전 403), 교차지점 대구 CO-0004 **403**(정상), 옛 `창고~` 스냅샷 SO-0007 **403**(얼어붙음=예상), br_staff(BR002) 목록 **200**(전 AUTH006). **이름축 정렬 성공.**

---

## 부록 — 현재 불일치 실측 (2026-06-22)
| 식별자 | canonical 기대 | 현재 실태 |
|---|---|---|
| 지점명(강남) | `강남 1지점` (1개로 고정) | user-service `강남 1지점 Phase61 175544` / JWT `강남 1지점` / inventory `창고~` / sales `강남 1지점` |
| SKU | sales 7종 | ✅ item 시드 완료 |
| 창고코드 | `WH-BR-001~004`,`WH-HQ-001` | inventory에 일부만(손POST), 이름 오타 |
| BR002 | ACTIVE | PENDING(AUTH006) |
