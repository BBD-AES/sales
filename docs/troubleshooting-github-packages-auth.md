# 트러블슈팅: GitHub Packages 인증 — "Username must not be null" / 401

> 공유 라이브러리(`com.bbd:bbd-platform-core`, GitHub Packages)를 빌드에서 못 받을 때.
> 특히 **"나만 빌드가 깨진다"** / **"되던 게 갑자기 안 된다"** 상황.

---

## 증상
```
Execution failed for task ':compileJava'.
> Could not resolve com.bbd:bbd-platform-core:0.0.7.
   > Could not get resource '.../com/bbd/bbd-platform-core/0.0.7/bbd-platform-core-0.0.7.pom'.
      > Username must not be null!          ← 자격증명 자체가 없음
      (또는)  Received status code 401 Unauthorized   ← 자격증명은 있는데 권한 부족
```

## 한 줄 원인
**private GitHub Packages** 아티팩트를 내려받을 **자격증명이 그 PC에 설정돼 있지 않음.**
- `Username must not be null` = 자격증명 미설정(username 이 null).
- `401 Unauthorized` = 자격증명은 있으나 토큰에 **`read:packages` 스코프 없음**(또는 만료).

---

## 왜 "나만 됨 / 나만 안 됨"
자격증명은 **레포(build.gradle)에 없습니다.** 각자 PC에서 읽습니다:
```gradle
credentials {
    username = findProperty("gpr.user") ?: System.getenv("GITHUB_USERNAME")
    password = findProperty("gpr.key")  ?: System.getenv("GITHUB_TOKEN")
}
```
- `findProperty("gpr.user")` → 각 PC의 **`~/.gradle/gradle.properties`** (홈 디렉터리, 레포 아님)
- `System.getenv(...)` → 그 셸의 **환경변수**

→ **설정한 사람만 빌드됨.** 그래서 사람마다 갈림.

### + 로컬 캐시 함정 (이게 "되던 게 갑자기 안 됨"의 정체)
이전 버전(예: `bbd-security-core:0.0.6`)이 이미 `~/.gradle/caches/` 에 받아져 있으면, gradle 은 **네트워크/인증을 건너뛰고 캐시를 씁니다.**
→ 자격증명을 설정 안 한 PC도 **"그동안은 잘 됐던"** 착각.
→ **새 버전 / 새 아티팩트**(`bbd-platform-core:0.0.7`, rename) 는 캐시에 없어 **처음 받아야** 하므로, 그동안 숨어 있던 **자격증명 부재가 그제서야 드러남.**

---

## 왜 build.gradle 에 직접 안 넣나 (중요 — 의도된 설계)
build.gradle 은 **git 에 커밋되는 공유 파일**입니다. 여기에 PAT(비밀)을 박으면:
1. 레포에 **비밀 노출** (누구나 봄).
2. GitHub **Secret Scanning** 이 푸시를 감지해 **그 토큰을 자동 폐기** → 전원 빌드 붕괴.

그래서 build.gradle 은 일부러 **값을 들고 있지 않고** `findProperty/getenv` 로 **외부에서** 읽습니다.
→ **비밀은 레포 밖(각 PC / CI secret)에 둔다.** 이건 버그가 아니라 보안 원칙입니다.

---

## 해결 (각 PC 1회 설정)

### 권장: `~/.gradle/gradle.properties` (레포 무관, 한 번만)
```properties
gpr.user=<본인 GitHub username>
gpr.key=<read:packages 스코프 PAT>
```
- PAT 발급: GitHub → **Settings → Developer settings → Personal access tokens (classic)** → **`read:packages`** 체크 → 발급.
- 이후 그냥 `./gradlew build`. (env 매번 export 불필요.)
- `gpr.user/gpr.key` 는 **모든 레포 공통**(findProperty 쪽) → 레포마다 env 이름이 달라도 이걸로 한 방에 해결.

### 임시: 환경변수 (이번 셸만)
> ⚠️ env 이름은 **레포마다 다를 수 있음** — build.gradle 의 `getenv(...)` 확인. (소비 레포=`GITHUB_USERNAME`, 일부 레포=`GITHUB_ACTOR`.)
```bash
GITHUB_USERNAME=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew build
# (gh 토큰에 read:packages 필요:  gh auth refresh -s read:packages)
```

---

## 그래도 안 되면 — 캐시 갱신
설정을 맞췄는데도 옛 에러가 남으면 캐시가 꼬인 것:
```bash
./gradlew build --refresh-dependencies
# 또는 해당 아티팩트 캐시만 제거 후 재시도
rm -rf ~/.gradle/caches/modules-2/files-2.1/com.bbd/bbd-platform-core
```

---

## 예방 (온보딩)
- 각 레포 **README 에 한 줄**: "빌드 전 `~/.gradle/gradle.properties` 에 `gpr.user`/`gpr.key`(read:packages PAT) 설정."
- **공유 PAT 1개 vs 각자 PAT**: 공유는 *회전·감사·유출* 리스크(그 1명 PAT 만료 시 전원 깨짐) → 가능하면 **각자 PAT**(또는 각자 `gh` 토큰). 동작은 동일.
- **CI(GitHub Actions)** 는 `GITHUB_TOKEN`(+actor) 자동 주입 → 영향 없음. **로컬 온보딩만의 이슈.**

## 핵심 교훈 (2줄)
1. **비밀은 VCS 밖.** build.gradle 이 값을 안 들고 외부에서 읽는 건 보안 설계지 불편이 아님.
2. **로컬 캐시가 설정 부재를 가린다.** "되던 게 갑자기 안 됨"은 보통 *새 버전/rename* 이 캐시 크러치를 깬 것.
