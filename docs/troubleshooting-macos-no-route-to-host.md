# 로컬에서 `No route to host` (macOS) 트러블슈팅

> **한 줄 요약:** 코드/네트워크 문제 아님. macOS가 **앱별로 "같은 네트워크(LAN) 안 기기와의 통신"을 막는 기능(Local Network Privacy)** 때문이다. 통과 권한이 있는 터미널로 앱을 **직접** 띄우면 풀린다.

대상: macOS(특히 Sequoia/15 이상)에서 sales 앱을 로컬 구동하는 팀원
작성 배경: `192.168.200.220`(item 등 사내 LAN 서비스) 호출이 IDE에서만 깨지는 현상

---

## 1. 증상

- `curl` 로 사내 IP를 호출하면 **정상(200)**
  ```bash
  curl http://192.168.200.220/item/api/v1/items/AC-0267488   # ✅ 200
  ```
- 그런데 **Spring 앱(JVM)에서 같은 주소를 호출**하면 실패
  ```
  org.springframework.web.client.ResourceAccessException:
    I/O error on GET request for "http://192.168.200.220/...": No route to host
  java.net.NoRouteToHostException: No route to host
  ```
- **DB(RDS)·Kafka 등 인터넷 호스트는 잘 붙는데, `192.168.x.x` 사내 IP만** 안 됨
- 터미널에서 `./gradlew bootRun` 으로 띄워도 **여전히 실패**

---

## 2. 원인 (쉽게)

macOS에는 **"이 앱이 같은 와이파이/LAN 안의 다른 기기와 통신해도 되는지" 를 앱마다 허락받는 경비원**(Local Network Privacy, 이하 **LNP**)이 있다.

- **터미널/curl** → 이미 허락받음 → ✅
- **우리 앱(JVM)** → 허락 못 받음 → ❌ → 이때 뜨는 게 `No route to host` (진짜 라우팅 장애가 아니라 **차단의 위장 증상**)

`192.168.200.220` 은 **같은 LAN 안 기기**라서 이 경비원의 검사 대상이고,
RDS/Kafka 는 **인터넷 경유**라 검사 대상이 아니다. → "DB는 되는데 사내 IP만 안 되는" 이유.

### 왜 터미널에서 띄워도 안 됐나 (핵심 함정)
`./gradlew bootRun` 을 터미널에 쳐도, 실제 앱을 실행하는 건 터미널이 아니라
**백그라운드에 따로 떠 있는 "Gradle 데몬"** 이다. (`launchd` 직속으로 분리돼 있음)

```
SalesApplication(앱) ──부모──> Gradle 데몬 ──부모──> launchd(시스템)
                                  └ 터미널과 분리됨 = 터미널 권한 상속 못 함
```

즉 **"허락받은 터미널이 직접 실행한 게 아니라, 허락 없는 데몬이 대신 실행"** 해서 차단된 것.
LNP는 **소켓을 여는 프로세스의 "책임 프로세스"** 기준으로 허락 여부를 따지는데, 그 책임 프로세스가 터미널이 아니라 데몬이었기 때문.

---

## 3. 5초 자가진단

아래 둘 다 맞으면 LNP 확정:

1. 같은 주소를 **`curl` 로는 성공**하는데 앱에서만 `No route to host`
2. 로그에서 **인터넷 호스트(RDS·Kafka 등)는 연결 성공**, **`192.168.x.x` 사내 IP만 실패**

(확실히 하려면) 앱 프로세스의 부모가 Gradle 데몬인지 확인:
```bash
ps -eo pid,ppid,command | grep -i 'GradleDaemon\|SalesApplication' | grep -v grep
```
→ 앱의 부모가 `GradleDaemon`, 그 데몬의 부모가 `1`(launchd) 이면 이 문서의 케이스.

---

## 4. 해결 방법

### 방법 A — 터미널에서 `java -jar` 로 직접 실행 (권장, 가장 확실)

데몬을 안 거치고 **터미널이 앱을 직접 자식으로 실행**하게 만든다.

```bash
# 1) 권한 없는 Gradle 데몬 종료
./gradlew --stop

# 2) 실행 가능한 jar 빌드
./gradlew clean bootJar

# 3) jar 이름 확인 ('-plain' 안 붙은 것)
ls build/libs

# 4) 터미널이 직접 실행
java -jar build/libs/sales-<버전>.jar
```
> ⚠️ `sales-<버전>-plain.jar` 는 실행 불가. `-plain` 안 붙은 걸 실행할 것.

그리고 **그 터미널 앱에 LAN 통신 권한 부여**:
- **시스템 설정 → 개인정보 보호 및 보안 → 로컬 네트워크** → 사용하는 터미널(**Terminal.app / iTerm**) **토글 ON**
  (처음 호출 시 권한 팝업이 뜨면 허용)

> ⚠️ **IntelliJ 내장 터미널 금지.** 내장 터미널에서 실행하면 책임 프로세스가 IntelliJ가 되어 동일하게 막힌다. 반드시 **별도 Terminal.app / iTerm** 사용.

---

### 방법 B — IntelliJ로 계속 돌리고 싶을 때

1. **시스템 설정 → 개인정보 보호 및 보안 → 로컬 네트워크 → IntelliJ IDEA 토글 ON**
2. IntelliJ **Settings → Build, Execution, Deployment → Build Tools → Gradle**
   → **"Build and run using"** 을 **`IntelliJ IDEA`** 로 변경
   (`Gradle` 로 두면 또 분리된 데몬을 거쳐 무효가 됨)
3. 이제 IntelliJ가 앱을 직접 실행 → 책임 프로세스 = IntelliJ → 권한 적용됨

---

### 방법 C — 권한 토글이 켜져 있는데도 안 될 때 (stale grant 버그)

macOS 15/26에서 토글이 ON인데도 안 먹는 알려진 버그가 있음.
- 해당 앱(터미널/IntelliJ) **로컬 네트워크 토글 OFF → ON** 사이클
- 앱 **완전 종료(⌘Q) 후 재실행**

---

## 5. 시간 낭비 방지 — 이건 원인이 아님 / 효과 없음

- ❌ 코드·URL·토큰 문제 아님 (curl 되면 코드 정상)
- ❌ VPN/사내망 라우팅 문제 아님
- ❌ IPv6 문제 아님 (대상이 IPv4 리터럴)
- ❌ 앱 방화벽(socketfilterfw)·재부팅·IPv4 강제 옵션 → 효과 없음
- ❌ `tccutil reset LocalNetwork` → **설계상 실패**("Failed to reset"). LNP 설정은 TCC DB 밖(`com.apple.networkextension.*`)에 저장돼서 그럼. 고장 아님.
- ❌ 그냥 `./gradlew bootRun` (데몬 경유) → 터미널에서 쳐도 막힘

---

## 6. 빠른 체크리스트

```
[ ] curl 로는 되는데 앱만 No route to host?            → LNP 의심
[ ] 로그에서 인터넷은 OK, 192.168.x.x 만 실패?          → LNP 확정
[ ] ./gradlew --stop
[ ] ./gradlew clean bootJar
[ ] 별도 터미널(Terminal/iTerm)에서 java -jar 실행
[ ] 시스템설정 > 로컬 네트워크 > 그 터미널 ON
[ ] 다시 호출 → 성공
```
