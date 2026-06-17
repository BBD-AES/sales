FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew
# GitHub Packages(비공개 bbd-security-core) 인증 — 워크플로가 --build-arg 로 전달, build.gradle 이 System.getenv 로 읽음.
ARG GITHUB_USERNAME
ARG GITHUB_TOKEN
# 토큰을 ENV/이미지 레이어에 굽지 않도록 gradle 프로세스에만 인라인 주입(멀티스테이지라 build 스테이지는 폐기됨).
RUN GITHUB_USERNAME="$GITHUB_USERNAME" GITHUB_TOKEN="$GITHUB_TOKEN" ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]