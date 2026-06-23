# [핸드오프] 서비스 간 시드/목업 데이터 정합 + 테넌시 코드축 전환

> 작성 2026-06-22 · 작성: sales팀 · 대상: **inventory · procurement · item · 신원(user)팀 전원 + 목업/시드 담당자**
> 근거: 6개 레포 전수 감사(sales·inventory·procurement·user·item·bbd-security-core) + 라이브 배포 테스트(`deployed-sales-env-findings`)

## 한 줄 (심각도: 🔴 데모 차단)
**서비스마다 따로 만든 시드/목업이 식별자 네임스페이스를 갈라놨습니다.** 창고코드·SKU·테넌시이름 셋 다 서비스별로 다르고, **SKU의 진실의 출처(item-service)는 빈 테이블**입니다. 라이브에서 관측된 `ITEM002`·지점유저 자기지점 `403`·inventory stub은 전부 이 드리프트의 증상입니다. 공유 정합 표준 없이 각 팀이 demo 데이터를 독립 생성한 게 근본 원인.

---

## 1. 마스터데이터 소유권 (누가 진실의 출처인가)

| 식별자 | 소유 서비스 | 현재 상태 |
|---|---|---|
| **창고코드↔이름↔타입** | **inventory** (`Warehouse.code` UNIQUE) | 시드 3개: `WH-HQ-001`(본사 중앙창고/HQ), `WH-DLR-001`(강북 대리점창고/DEALER), `WH-DLR-002`(부산 대리점창고/DEALER) |
| **SKU(상품 마스터)** | **item** (`Item.sku`) | ⚠️ **시드 0건(빈 테이블)**. 포맷 무강제 자유문자열 |
| **공급사(Vendor)** | **procurement** (`vendor.code` `^V\d{6}$`) | V000001~V000003 |
| **테넌시(소속)** | **user(신원)** (`tenancyType` enum + `tenancy_name`) | ⚠️ **코드 없음**. `tenancy_name`은 자유텍스트(FK·유일성·룩업테이블 없음). 실 시드는 외부 `keycloak-users.json`(미커밋) |
| **사번/keycloak_sub** | **user(신원)** (`employee_number`, `keycloak_sub` UNIQUE) | 패턴 무강제 |
| **SO/CO 번호·고객** | **sales** | data.sql |

핵심: **창고=inventory, SKU=item, 테넌시/사번=user가 권위.** 다른 서비스는 이 값을 *복사*해야 하고 *발명하면 안 됨*.

---

## 2. 드리프트 표 (서비스별 실제 값 — 안 맞음)

### 2-1. 창고코드 🔴 — 교집합이 `WH-HQ-001` 하나뿐
| 서비스 | 창고코드(+이름) |
|---|---|
| **inventory (권위)** | `WH-HQ-001` 본사 중앙창고 · `WH-DLR-001` 강북 대리점창고 · `WH-DLR-002` 부산 대리점창고 |
| sales `data.sql` | `WH-HQ-001` · `WH-BR-001` 강남 1지점 · `WH-BR-002` 분당 1지점 · `WH-BR-003` 부산 1지점 · `WH-BR-004` 대구 1지점 |
| procurement `proc_data.sql` | `WH-HQ-001` · `WH-002` · `WH-001` · `WH-010` · `WH-01` · `W000003`(깨진 값) |

→ sales의 `WH-BR-*` 는 inventory에 **존재하지 않음**(inventory는 `WH-DLR-*`). 지점창고 코드·이름·개수가 전부 다름 → sales 주문이 참조하는 창고로 inventory 예약/출고 불가. procurement는 또 다른 포맷(`WH-002`,`WH-01`).

### 2-2. SKU 🔴 — 4개 네임스페이스, 권위는 빈 테이블
| 서비스 | SKU |
|---|---|
| **item (권위)** | **없음** (테스트 throwaway: `HQ-Test`,`INT-0736572`,`AC-0580313`… 포맷 3종 혼재) |
| sales `data.sql` | `OIL-FLT-001`,`BRK-PAD-RR-001`,`BAT-12V-60`,`RLY-12V-30A-01`,`TIR-225-45-17`,`WSH-FLU-2L`,`CLT-DSK-MED-01` |
| inventory `LocalDataSeeder` | `SKU-1001`~`SKU-1050` (`SKU-%04d`) |
| procurement `proc_data.sql` | `SKU-1001`,`SKU-1002`,`SKU-3001`,`SKU-TEST-001`,`SKU-FIX46-A/B` |

→ **`OIL-FLT-001`은 어디에도 없음** → 라이브 `ITEM002` 확정 원인. item이 비어 있어 sales 주문 생성은 fail-fast(폴백 없음)로 차단됨.

### 2-3. 테넌시 이름 🔴 — '강남' 표기 3종 → 이름축 인가 403
| 서비스 | 값 |
|---|---|
| **user (권위)** | 픽스처 `본사`·`강남 지점`·`지점`·`인천 지점` (실값 외부 미커밋, 분당/부산/대구 **없음**) |
| sales `data.sql` | `강남 1지점`·`분당 1지점`·`부산 1지점`·`대구 1지점`·`본사` |
| sales 테스트 픽스처 | `강남지점` (공백 없음) |

→ `강남 지점` ≠ `강남 1지점` ≠ `강남지점`. 이름축 인가는 문자열 정확일치라 **곧바로 403**(라이브 관측과 일치).

### 2-4. 사번 🟠 — 포맷 불일치 + procurement는 타입까지 바뀜
| 서비스 | 값 |
|---|---|
| **user (권위)** | 픽스처 `EMP-001`/`EMP-1`/`BR-001`/`SCIM-001` (패턴 무강제, 실값 외부) |
| sales `data.sql` | `BR001`~`BR004`,`HQ001` |
| procurement | V14 이후 actor 컬럼 **BIGINT**(`10001`…). 그런데 `proc_data.sql`은 옛 STRING `E001`/`EMP-001` → **로드 시 캐스팅 실패** |

---

## 3. 근본 원인 — 공유 fixture 없음, 시드 메커니즘 제각각

| 서비스 | 시드 방식 | 자동 적재? |
|---|---|---|
| sales | `src/main/resources/data.sql` (`spring.sql.init.mode=always`) | ✅ |
| inventory | `config/LocalDataSeeder.java` (`@Profile("local")` ApplicationRunner) | ✅(local만) |
| procurement | `proc_data.sql` (수동 pg_dump) | ❌ 손으로 실행 |
| item | **없음** | — |
| user | 외부 `keycloak-users.json`(미커밋) → Node 스크립트 | ❌ 외부 |

→ **단일 출처가 없어 5팀이 각자 demo 식별자를 발명**. 조정 지점이 0이라 드리프트는 필연.

---

## 4. ★★ 목업/시드 담당자께 (가장 중요) ★★

**서비스마다 목업을 따로 찍지 마세요. 그게 지금 모든 403/404의 원인입니다.**

1. **식별자는 발명하지 말고 권위 서비스 값을 복사**: 창고코드=inventory, SKU=item, 테넌시이름/사번=user. 새 값이 필요하면 *권위 서비스에 먼저 추가*하고 그 값을 가져다 쓰세요.
2. **단일 정합 레지스트리를 만드세요** — 창고코드·SKU·테넌시이름·사번의 canonical 목록 1장(아래 §6 초안)을 레포에 커밋하고, 모든 서비스 시드가 *그것만* 참조. (이상적으론 공유 seed fixture 모듈/SQL 1벌.)
3. **이미 박힌 함정**:
   - 창고: `WH-BR-*`(sales) ≠ `WH-DLR-*`(inventory) ≠ `WH-0xx`(procurement). **inventory 코드로 통일**.
   - SKU: `OIL-FLT-001`(sales) ≠ `SKU-1001`(inv/proc). item에 **하나로 시드**.
   - 테넌시: `강남 1지점`/`강남 지점`/`강남지점` 공백·번호 차이 → **user 표기로 글자까지 통일**(이름축이라 1글자만 달라도 인가 깨짐).
   - 사번: procurement는 **BIGINT userId**(예 10001)로 바뀜 — 문자열 `BR001` 넣으면 로드 실패. proc 시드는 숫자 userId로.
4. **재적재 주의**: sales `data.sql`은 `ON CONFLICT DO NOTHING` 멱등이라 옛 시드가 남아 있으면 새 값으로 안 덮임 → 정합 맞추려면 해당 테이블 TRUNCATE 후 재기동. inventory `LocalDataSeeder`도 `count()>0`이면 skip.

---

## 5. 코드축(name→code) 전환 — 가능하지만 "필드 추가"보다 큼

라이브 403의 구조적 해법은 이름축→코드축인데, 감사 결과 **단순히 tenancyCode 필드만 추가하면 끝이 아닙니다.**

**현황**
- inventory·procurement: 이미 **code-axis**(warehouseCode로 필터). 단 인가가 아니라 자유 파라미터(창고 confinement 없음).
- sales: **name-axis**. 단 주문에 코드 저장 + 도메인 `ownedByWarehouse(code)` 이미 존재(데드코드) → 스왑 준비됨.
- user·security-core: **tenancyCode 없음**. 테넌시는 `tenancyType`+`tenancy_name`뿐.

**전환에 필요한 변경**
| 주체 | 변경 | 규모 |
|---|---|---|
| 신원(user) ⭐ | `/internal/snapshot` 응답에 `tenancyCode` 추가 + 각 유저에 코드 부여 | 중(cross-team 핵심) |
| bbd-security-core | `domain/UserSnapshot` + `application/model/CurrentUserSnapshotResult`(+`from`/`toDomain`)에 `tenancyCode` 추가 — **3곳 동시** | 소(공유lib, additive) |
| sales | `CurrentUser`+`warehouseCode`, 어댑터 채움, 인가 6곳 `ownedByWarehouseName`→`ownedByWarehouse`, 목록 predicate 코드로 | 소 |
| inventory/procurement | (선택) warehouseCode를 인가축으로 승격하려면 caller confinement 추가 | 중 |

**🔴 더 큰 공백**: **테넌시↔창고 매핑이 org 어디에도 없습니다.** user-service는 창고를 전혀 모르고(WH-* 개념 0), inventory는 테넌시를 모릅니다. 그래서 "tenancyCode = 어떤 값?"의 답이 없음 — *지점 사용자의 tenancyCode가 곧 그 지점의 inventory 창고코드*가 되도록 **새 바인딩을 정의·소유**해야 합니다(누가 소유할지 합의 필요. 후보: user가 tenancyCode로 inventory 창고코드를 보유). 이걸 안 정하면 코드축은 이름 드리프트를 코드 드리프트로 옮길 뿐.

→ 점진(dual-read): user가 `tenancyCode` **추가**(기존 name 유지) → 각 서비스가 "코드 있으면 코드, 없으면 이름" 폴백 → 준비되면 flip. 비파괴.

---

## 6. 권장 canonical 표준값 (초안 — 팀 합의용)

데모용 최소 정합 세트 제안. **모든 서비스 시드가 이것만 참조**하도록:

- **창고**(inventory 권위로 확정): `WH-HQ-001` 본사 중앙창고 / 지점은 inventory의 `WH-DLR-001`·`WH-DLR-002`를 쓰거나, 데모 서사(강남/분당…)가 필요하면 **inventory에 그 창고들을 먼저 시드**하고 그 코드를 전 서비스가 채택.
- **SKU**(item에 시드 — 현재 빈 테이블): sales가 쓰는 도메인 포맷 7종(`OIL-FLT-001`,`BRK-PAD-RR-001`,`BAT-12V-60`,`RLY-12V-30A-01`,`TIR-225-45-17`,`WSH-FLU-2L`,`CLT-DSK-MED-01`)으로 통일 권장(가장 의미 있는 포맷). inventory/procurement 시드의 `SKU-1001`은 이걸로 교체.
- **테넌시 이름**(user 권위): 표기 1개로 고정(예 `강남 1지점`) 후 sales data.sql·테스트·user fixture 글자까지 일치.
- **사번**: 포맷 1개 고정. procurement는 숫자 userId라 매핑표 필요.

---

## 7. 팀별 액션 아이템 (우선순위)

**🔴 데모 차단(즉시)**
- **item**: canonical SKU 7종 **시드 추가**(현재 0건 → 모든 주문/예약 차단의 원흉).
- **inventory**: 데모 지점창고 코드·이름을 §6대로 확정/시드(또는 sales가 `WH-DLR-*`에 맞추도록 통보).
- **sales**: data.sql 창고코드 `WH-BR-*`→inventory 코드, 테넌시이름·SKU를 canonical로 정렬. 테스트 픽스처 `강남지점`→통일 표기.
- **신원(user)**: canonical 테넌시이름·사번 vocabulary 공개(현재 실값이 미커밋 외부라 아무도 못 맞춤). `keycloak-users.json`의 권위 값 공유.

**🟠 정합/안정**
- **procurement**: `proc_data.sql` 사번을 BIGINT userId로 교체(V14 호환), 창고코드 포맷·고아 vendorCode(`V000006`,`D000001`) 정리.
- **전팀**: §6 canonical 레지스트리 1장 커밋 + 시드가 그것만 참조하도록.

**🟢 구조(중기)**
- 테넌시↔창고 바인딩 소유자 합의 → user `tenancyCode` 추가(=창고코드) → security-core 3곳 → 서비스별 name→code 스왑(dual-read 점진).

---

## 부록 — 인가/스코핑 축 현황
| 서비스 | 축 | 비고 |
|---|---|---|
| sales | **name** | 주문 코드 저장+`ownedByWarehouse(code)` 데드코드 존재(스왑 준비됨) |
| inventory | code(필터) | warehouseCode 자유 파라미터, caller confinement 없음(아무 창고나 조회/변경 가능) |
| procurement | code(미사용) | warehouseCode 검증·인가 없음, HQ-only 역할게이트뿐 |
| item | none | SKU 글로벌, 인가 전부 주석처리 |
| user/security-core | name | tenancyType+tenancy_name만, tenancyCode 없음 |
