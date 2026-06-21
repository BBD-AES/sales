# 모바일 OIDC 인증 — 백엔드 작업 핸드오프

> 작성 2026-06-21 · 질문: "모바일 앱(네이티브 안드로이드)이 Keycloak Bearer JWT로 게이트웨이/MSA를 호출하려면 백엔드에 정확히 뭘 해야 하나?"
> 방법: `security-gateway` · `bbd-security-core` · `infra` · `user` 4개 레포 병렬 분석 → 종합 → 핵심 주장 적대 검증(6/6 holds=true, file:line 근거).

## TL;DR — 백엔드 코드 작업은 사실상 **없음**

게이트웨이도 서비스도 **이미 Bearer JWT를 받습니다.** 모바일이 막혔던 게 아니라, 막을 게 없습니다.
- **유일한 필수 작업** = Keycloak에 **모바일용 public/PKCE 클라이언트 1개 생성**(콘솔 작업, 인프라/인증팀, **0.5~1인일**). 코드 PR 아님.
- 게이트웨이 코드 변경은 전부 **선택/검증**: (a) `/api/auth/me`를 모바일도 쓰게 하려면 ~15줄, (b) TokenRelay가 순수 Bearer 경로에서 헤더 보존하는지 실측 1건.
- 서비스(`bbd-security-core` + sales 등 MSA): **변경 0.**

---

## 현재 구조 — 왜 추가 작업이 거의 없나

**게이트웨이 = dual security chain** (`security-gateway/config/SecurityConfig.java`)
- `@Order(1) bearerTokenSecurityFilterChain` — `Authorization: Bearer` 헤더가 있는 요청만 `securityMatcher`로 선점(`:66-80`) → **STATELESS**(`:91-92`) + CSRF off(`:89`) + `oauth2ResourceServer().jwt()`(`:111-113`)로 Keycloak JWT 검증.
- `@Order(2) webSecurityFilterChain` — 그 외 브라우저 요청은 `oauth2Login` + JSESSIONID 세션(`:119-164`).
- → **세션 전용이 아니라 세션+Bearer 둘 다 이미 구현돼 있음.** 모바일 Bearer는 자동으로 (1)번 체인으로 처리됨.

**서비스 = 독립 resource-server** (`bbd-security-core/config/BbdSecurityAutoConfiguration.java`)
- `bbdSecurityFilterChain`이 `AutoConfiguration.imports`로 **모든 MSA에 자동 등록** → STATELESS + `oauth2ResourceServer().jwt()`(`:118-129`).
- 게이트웨이가 주입한 신뢰 헤더(X-User 등)가 아니라 **토큰 자체를 동일 issuer로 독립 재검증**. 토큰 출처(웹 세션 relay vs 모바일 직접발급)를 구분하지 않음.

**인가 = `@RequireRole`** (`RoleAuthorizationAspect`)
- JWT의 role claim이 아니라 **`sub` → UserSnapshot(Redis/User-service) 조회** 기반. → 토큰 출처 무관, 모바일 전용 role 매핑 불필요.

**토큰 전달 = `TokenRelay`** (`application-*.yml` 라우트 필터)
- 웹 세션 경로는 `OAuth2AuthorizedClient`에서 토큰을 꺼내 relay. 순수 Bearer 경로엔 AuthorizedClient가 없어 **들어온 `Authorization` 헤더가 그대로 다운스트림에 보존**되는 구조(→ 아래 ‘검증 1건’).

---

## 해야 할 일

### 🔴 필수 (1건) — Keycloak 모바일 클라이언트 생성

| 항목 | 값 |
|---|---|
| 위치 | Keycloak Admin Console, realm **`bbd`** (`https://bbd-keycloak.inwoohub.com`) — 레포에 realm export 없음(외부 호스팅) |
| Client type | **public** (Client authentication OFF) |
| Flow | Standard flow + **PKCE(S256) 필수** |
| Valid Redirect URIs | 앱 딥링크 (예: `com.bbd.mobile:/oauth2redirect` 또는 `bbdmobile://oauth`) — **AppAuth redirectUri와 정확히 일치** |
| Web Origins | 비움(네이티브는 CORS 비대상) |
| Client scopes | 커스텀 claim(`employee_number`/`position`/`name`) 필요 시 `bbd-claims`를 Default/Optional로 |
| 담당 | 인프라/인증팀 · 공수 **S(0.5~1일)** |

> 기존 `security-gateway` 클라이언트는 **confidential(client-secret 보유)**이라 모바일 재사용 불가 → 신규 public 1개 필수. 같은 `bbd` realm 발급 JWT면 게이트웨이/MSA가 클라이언트 무관하게 그대로 검증함.

### 🟡 선택 — 게이트웨이 `/api/auth/me` 모바일 대응 (~15줄)
현재 `AuthPrincipalExtractor`는 `principal instanceof OidcUser`(세션)일 때만 authenticated를 만들고(`:86-102`), 그 외는 unauthenticated(`:111`). → 유효한 Bearer 토큰(=`Jwt` principal)인데도 `/me`가 "로그인 필요"로 거짓 미인증.
**대안:** 모바일이 `/me` 대신 토큰 claim을 직접 디코드하거나 user-service 본인조회 API를 쓰면 **게이트웨이 변경 0.** `/me`를 공용으로 쓸 거면 아래 분기 추가:

```java
// AuthPrincipalExtractor.java:102 부근, OidcUser 분기 다음
if (principal instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
    String subject = jwt.getSubject();
    if (subject == null) return AuthPrincipal.unauthenticated();
    return AuthPrincipal.authenticated(
        subject,
        jwt.getClaimAsString("preferred_username"),
        jwt.getClaimAsString("employee_number"),
        jwt.getClaimAsString("name"),
        jwt.getClaimAsString("email"),
        jwt.getClaimAsString("position"));
}
```
claim 키가 OidcUser 경로와 동일(`bbd-claims` 스코프가 채움)이라 매핑 재작업 없음. 공수 **S**.

### 🟡 검증 (1건) — TokenRelay 헤더 보존
`TokenRelay`는 본래 `oauth2Login`의 AuthorizedClient 기반이라, **세션 없는 순수 Bearer 경로**에서 원본 `Authorization` 헤더를 다운스트림에 그대로 넘기는지 코드만으로는 단정 불가(분석 4건 중 2건이 이 한 건만 미확정으로 명시). → 실제 요청 1건으로 확인. 유실되면 해당 bearer 라우트에 헤더 passthrough만 보장(토큰 교환/재발급 불필요 — MSA가 어차피 독립 검증). 공수 **S**.

### ⚪ 서비스(MSA) — 변경 0
`bbd-security-core` 자동설정이 이미 모든 MSA를 resource-server로 만듦. 모바일 사용자는 **User-service에 해당 `sub`의 UserSnapshot(ACTIVE + 적절 role)만 존재하면** 그대로 인가됨.

---

## AppAuth(안드로이드)에 넣을 값

| 키 | 값 |
|---|---|
| discoveryUri | `https://bbd-keycloak.inwoohub.com/auth/realms/bbd/.well-known/openid-configuration` |
| issuer | `https://bbd-keycloak.inwoohub.com/auth/realms/bbd` (JWT `iss`가 이와 정확히 일치해야 게이트웨이/MSA 검증 통과) |
| realm | `bbd` |
| clientId | **신규 모바일 public client id** (운영팀 확정, 예: `bbd-mobile`) |
| redirectUri | Keycloak Valid Redirect URIs에 등록한 앱 딥링크와 **정확히 일치** |
| responseType | `code` (PKCE 자동, S256) · client-secret 없음 |
| scopes | `openid profile email` (+ 커스텀 claim 필요 시 `bbd-claims`) |
| API 호출 | 게이트웨이 베이스 + `/sales`·`/user` 등에 `Authorization: Bearer <access_token>` |

토큰 획득/갱신/revoke는 앱이 Keycloak token/revocation 엔드포인트와 **직접** 처리 — 게이트웨이 `oauth2Login`/로그아웃 경로는 사용 안 함.

---

## ⚠️ 별건 — 긴급 보안 이슈 (모바일과 무관, 즉시 조치 권장)

**`security-gateway`(public 레포)의 `.env`가 커밋돼 OAuth2 client-secret·issuer가 평문 노출.**
- `.env`가 `.gitignore`(53–55행)에 있으나 **이미 추적 중**이라 무효 → `git ls-files`에 `.env` 존재.
- 조치: ① Keycloak에서 해당 client-secret **즉시 회전**, ② `git rm --cached .env` + 히스토리 정리(BFG/filter-repo), ③ 다른 레포도 동일 점검.

---

## 미해결 질문 (운영/인증팀 확정 필요)
1. 모바일 clientId + redirect 딥링크 스킴 확정(예: `bbd-mobile` + `com.bbd.mobile:/oauth2redirect`). Keycloak Valid Redirect URIs ↔ AppAuth redirectUri **정확 일치**.
2. **audience 검증 정책**: 현재 게이트웨이/MSA 모두 `jwt(Customizer.withDefaults())`로 audience 미검증 → 동일 realm의 어떤 클라이언트 토큰도 통과. 모바일/웹 토큰을 구분 강제하려면 `bbd-security-core` 1곳에 audience validator 추가(전 MSA 일괄). 현재 취약점은 아니나 정책 결정.
3. 모바일 사용자 `sub`의 UserSnapshot이 User-service에 ACTIVE + 적절 role로 존재하는지(없으면 인증은 되나 `@RequireRole` 인가에서 막힘). 캐시 TTL 300초 → role 변경 반영 최대 5분 지연.
4. 모바일이 게이트웨이 경유 vs MSA 직접호출(서비스 체인은 직접호출도 유효 JWT면 인증) — 라우팅 정책.
