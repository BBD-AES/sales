# 멱등성 표준 (Idempotency Standard) — BBD-AES MSA 공통

> 상태: 확정(2026-06-23) · 대상: 5개 서비스(sales/inventory/item/procurement/user) + 게이트웨이
> 목적: 재시도·더블클릭으로 인한 **중복 변경**(중복 주문/예약/발주 등)을 막는 공통 규약.

---

## 1. 적용 범위
- **필수**: 모든 `POST` + **부수효과 있는 상태전이** `PATCH`(submit / approve / reserve / confirm / close / cancel 등).
- **선택**: 자연 멱등 `PATCH`(단순 `field = value` 처럼 재시도해도 결과가 같은 것).
- **제외**: `GET` 및 부수효과 없는 조회성 요청.

## 2. 헤더
- 이름: **`Idempotency-Key`** (Stripe de-facto + IETF `draft-ietf-httpapi-idempotency-key-header`).
  - 금지: `X-Idempotency-Key`(RFC 6648 — `X-` 접두사 폐기), 기타 변형 이름.
- 값: **클라이언트 생성 UUID**(v7 권장 — 시간순이라 인덱스 locality 우수). 서버는 **opaque 문자열**로 취급.
  - 검증: 빈 값 거부, 길이 ≤ 255.
- 규칙: **논리적 작업 1개당 키 1개**. 재시도/더블클릭 시 **동일 키 재전송**.

## 3. 책임 분리 (핵심)
| 레이어 | 역할 |
|---|---|
| **게이트웨이** | 변경 라우트에 헤더 **강제**(없으면 `400`) + 헤더 **그대로 전파**. **dedup 안 함.** |
| **각 MSA (공유 라이브러리)** | 멱등 **판정**. `bbd-platform-core`(구 bbd-security-core)의 `@Idempotent`(@RequireRole 과 동일 AOP 패턴)로 적용 — 보일러플레이트 0. |
| **DB `UNIQUE(idempotency_key)`** | **정확성의 최종 보루.** 쓰기와 같은 트랜잭션. Redis가 없어도 멱등 성립. |

> **게이트웨이가 dedup·응답 replay를 하지 않는 이유**: ① 부수효과 at-most-once는 쓰기와 같은 트랜잭션/유니크 제약에 묶여야 보장됨(서비스만 가능) ② 응답 replay 의미·스코프는 엔드포인트별 비즈니스 결정 ③ stateless 라우터를 stateful 임계점으로 만들면 Redis 장애 시 전 트래픽 차단(blast radius). → 게이트웨이는 **강제·전파**만, **판정은 서비스**.

## 4. 판정 메커니즘
1. 비즈니스 트랜잭션 실행 — 멱등 대상 테이블에 **`UNIQUE(idempotency_key)`**.
2. 성공 → **커밋 후** `SET idem:{service}:{principal}:{key} true EX 86400` (24h) → `201/200`.
3. DB 유니크 위반(동시/재시도 중복) → **`409`**.
4. (선택 단락) 진입 시 Redis `EXISTS`로 명백한 중복은 DB 안 거치고 `409`.

> **Redis는 "커밋 후 SET".** 쓰기 전 `SETNX` 금지 — 커밋 전에 실패/크래시하면 키가 박혀 정당한 재시도를 24h 막음(보상 `DEL` 필요해 복잡). 동시성은 **DB 유니크 제약이 원자적으로** 처리(하나만 성공, 나머지 위반 → `409`). → **Redis = 부하 절감 최적화, 정확성 = DB.** Redis가 비어 있어도 안전.

## 5. Redis 키 규약
- 키: **`idem:{service}:{principalId}:{idempotencyKey}`**
  - `service` prefix **필수** — 공유 Redis 충돌 방지 + `SCAN idem:sales:*` 디버깅/모니터링.
  - `principalId` 스코프 — 다른 유저가 같은 키로 결과를 가로채거나 충돌시키는 것 방지(replay 권한 누수 차단).
- 값: **`true`** — 완료 응답 **캐시하지 않음**(통일).
- TTL: **24h**(`86400s`). 별도 in-flight 락 없음 — 동시성은 DB가 담당.

## 6. 중복 응답 계약 (클라이언트 필수)
- 응답을 캐시하지 않으므로 **중복 = `409 Conflict`** (원본 `201`·바디를 돌려주지 않음).
- **클라이언트는 `409`를 "이미 성공"으로 처리** — 목록 이동/재조회. **에러 토스트 금지.**
- (선택) 같은 키 + 다른 바디 → `422`(요청 fingerprint 저장 시).

## 7. 에러 코드
| 코드 | 의미 | 발생 위치 |
|---|---|---|
| `400` | `Idempotency-Key` 헤더 누락 | 게이트웨이 |
| `409` | 중복 / 이미 처리됨 (예: `IDEM001`) | 서비스 |
| `422` | (선택) 같은 키 · 다른 바디 | 서비스 |

## 8. 레퍼런스 & 마이그레이션
- **레퍼런스 구현**: `sales` #71 — `uk_idempotency_key` DB 제약 + replay 소유권 인가.
- **변경점(중요)**: #71은 중복 시 **원본 응답 반환**이었음 → 본 표준은 **`409`**(응답 캐시 없음). `sales`는 중복 분기를 `409`로, `frontend-react`는 `409`를 "이미 처리"로 정렬 필요.
- **공유 라이브러리**: **`bbd-platform-core`**(구 `bbd-security-core`)의 `com.bbd.securitycore.idempotency` 패키지 — `@Idempotent` + AOP + Redis 빠른길. 각 서비스는 의존성 좌표 `com.bbd:bbd-platform-core:0.0.7` + `@Idempotent` 부착 + DB UNIQUE 추가. (모듈은 보안만이 아닌 플랫폼 스타터라 rename.)

## 9. 체크리스트 (팀별)
- [ ] **게이트웨이**: 변경 라우트 헤더 강제(`400`) + 전파
- [ ] **공유 스타터** `bbd-platform-core`(구 security-core) 의존성 + `@Idempotent` 적용
- [ ] 멱등 대상 테이블에 `UNIQUE(idempotency_key)`
- [ ] 중복 응답 `409`로 통일 + 클라 `409` 처리
- [ ] Redis 키 `idem:{service}:{principal}:{key}` = `true`, TTL 24h
