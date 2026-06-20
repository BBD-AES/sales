# [요청] user 서비스 — 클라이언트용 self 조회 `GET /api/v1/users/me`

> 요청: sales팀 (모바일·웹 프론트 연동) → 대상: **user 서비스팀**
> 작성 2026-06-15 · 우선순위: 보통(블로커 아님, 임시 우회 가능 — §6)

## 1. 요청 한 줄
현재 로그인 사용자의 **ERP 프로필(role·tenancy 포함)을 토큰만으로 self 조회**하는 공개 엔드포인트 `GET /api/v1/users/me`를 추가해 주세요. 응답은 기존 `internal/snapshot`과 동일 DTO면 됩니다.

## 2. 왜 필요한가 (현재로는 깔끔히 안 됨)
마이페이지(모바일 M-MY, 웹 마이페이지)와 **역할·소속 기반 메뉴/데이터 렌더링**에는 `role + tenancyType + tenancyName + status + employeeNumber`가 필요합니다. 그런데 현재 경로 셋 다 부족합니다:

| 경로 | 한계 |
|---|---|
| 게이트웨이 `GET /api/auth/me` | **모바일(Bearer)에서 동작 안 함**(세션 전용, `AuthController.java`). 반환도 Keycloak 기본 클레임(keycloakSub/username/employeeNumber/displayName/email/position)뿐 — **role·tenancy·status 없음** |
| JWT 클레임 직접 파싱 | role은 `realm_access`에 있으나 **tenancyType·tenancyName·employeeNumber 클레임이 없음**(`UserSecurityConfig` 확인). 토큰만으론 소속을 모름 |
| `GET /api/v1/users/internal/snapshot?keycloakSub=` | ERP 필드를 다 주고 self-match도 안전하지만 **① "internal" 서비스 간 전용**(계약 안정성 미보장) **② keycloakSub를 쿼리파라미터로 직접 전달** 필요(클라가 토큰 sub 추출 후 재전송 — 표준 `/me` 관례 위반) |
| `GET /api/v1/users` (Admin) | `@RequireRole(ADMIN)` 전용 + 타인 조회 가능 → self용 부적합 |

즉 **클라이언트가 자기 ERP 프로필을 표준 방식으로 얻을 공개 경로가 없습니다.**

## 3. 제안 스펙
```
GET /api/v1/users/me
```
| 항목 | 값 |
|---|---|
| 인증 | JWT Bearer (Keycloak access token). 게이트웨이 TokenRelay(웹 세션)·직접 Bearer(모바일) 모두 통과 |
| 권한 | **인증된 사용자 누구나** (@RequireRole 불필요 — 항상 본인) |
| 요청 | **없음**. keycloakSub는 서버가 `jwt.getSubject()`에서 추출(클라가 안 넘김) |
| 응답 200 | 기존 `UserSnapshotResponse` 그대로 |
| 응답 401 | 미인증 |
| 응답 404 | 토큰 sub에 해당하는 user 없음(프로비저닝 전 등) |

**응답 DTO(= 기존 internal/snapshot과 동일):**
```json
{
  "userId": 12, "keycloakSub": "a1b2...", "employeeNumber": "BR002",
  "displayName": "정민수", "email": "...", "position": "정비사",
  "status": "ACTIVE", "role": "BRANCH_STAFF",
  "tenancyType": "BRANCH", "tenancyName": "강남 1지점", "version": 7
}
```

## 4. 구현 힌트 (가벼움)
- **`InternalUserSnapshotController`의 로직을 거의 그대로 재사용.** 차이는 단 하나 — keycloakSub를 쿼리파라미터가 아니라 `@AuthenticationPrincipal Jwt jwt` → `jwt.getSubject()`로 받는 것.
- self-match 검사 불필요(애초에 자기 토큰의 sub만 씀).
- 기존 Redis UserSnapshot 캐시 경로를 그대로 타면 됨.
- `internal/snapshot`은 서비스 간 용도로 **그대로 유지**(이 요청은 신규 추가이지 대체가 아님).

## 5. 영향받는 클라이언트
- **모바일 M-MY(마이페이지)**: 프로필 카드(이름·사번·소속·Role 배지·상태) — 현재 `/api/auth/me` 불가라 이 엔드포인트가 사실상 유일 경로.
- **웹 마이페이지 + 로그인 직후 부트스트랩**: 역할·소속을 받아 **사이드바 메뉴 노출·Tenancy 스코프 UI**를 구성. (데이터 접근 자체는 백엔드가 강제하지만, UI 렌더링에 role·tenancy 필요)

## 6. 임시 우회 (엔드포인트 나오기 전까지)
클라이언트가 **자기 토큰의 sub를 추출해 `GET /api/v1/users/internal/snapshot?keycloakSub={sub}` 호출**. self-match가 막아주므로 보안상 안전. 단 위 §2의 어색함(internal 네이밍·파라미터)을 감수. `/me`가 나오면 교체.

## 7. 확인 요청
- 응답 DTO를 `internal/snapshot`과 **동일하게** 둬도 되는지(필드 노출 정책상 클라이언트에 `userId`/`version`까지 줘도 무방한지). 민감 필드 제외가 필요하면 알려주세요.
- 경로 `/api/v1/users/me` 컨벤션 동의 여부(또는 팀 표준 경로).
