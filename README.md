# AutoLog — 차가 스스로 쓰는 운행·지출 장부

> 운전만 하세요. 기록은 차가 합니다.

2026 현대자동차그룹 차량용 앱 개발 공모전 (Pleos App Market). 기획·기능·기술 명세는 팀 내부 문서로 관리한다.

## 구조

| 경로 | 무엇 | 실행 위치 | 차량 APK에 포함 |
|---|---|---|---|
| `shared/` | inbox payload DTO (Kotlin JVM) | — | ✅ (vehicle-core 가 의존) |
| `vehicle-core/` | 차량 로직 — SDK·운행 기록·Room·inbox·엑셀·메일 (화면 없음) | Pleos 에뮬레이터 · 헤드유닛 | ✅ (app 이 의존) |
| `app/` | 차량 화면 (Compose). `vehicle-core` 의 `AutoLog` API만 사용 | Pleos 에뮬레이터 · 헤드유닛 | ✅ |
| `relay/` | 중계 서버 (Spring Boot + PostgreSQL + MinIO) | 서버 VM | ❌ |
| `web/admin` | 운영 웹 (React + TS + Vite) | nginx 정적 | ❌ |
| `web/camera` | 폰 영수증 카메라 페이지 (HTML 1장) | nginx 정적 | ❌ |
| `ops/` | docker-compose, nginx, .env.example | 서버 VM | ❌ |

의존 방향: `app` → `vehicle-core` → `shared`. 화면과 로직의 경계는 `vehicle-core/.../AutoLog.kt`.

원칙: **운행 기록의 원본은 차량 Room.** 서버는 영수증 OCR·기준 정보만 차로 넣어주는 우체통이며, 서버를 꺼도 운행·엑셀·메일은 동작한다.

## 빌드

요구: JDK 17+, Android SDK 35, Node 20+, Docker.

```bash
./gradlew :shared:test               # 공용 DTO
./gradlew :vehicle-core:test         # 차량 로직 단위 테스트 (화면·에뮬레이터 없이)
./gradlew :app:assembleDebug         # 차량 앱 → app/build/outputs/apk/debug/
./gradlew :relay:bootJar             # 서버 jar → relay/build/libs/relay.jar
```

```bash
cd web/admin && npm install && npm run dev     # 운영 웹 개발 서버
```

```bash
cd ops && cp .env.example .env && docker compose up -d   # 서버 일체 (relay jar·web 빌드 후)
```

APK에 무엇이 들어갔는지 확인: Android Studio **Build → Analyze APK** 또는
`./gradlew :app:dependencies --configuration releaseRuntimeClasspath`.

## Pleos SDK

Maven: `https://nexus-playground.pleos.ai/repository/maven-releases/`. 검증 결과는 [`vehicle-core/docs/sdk-findings.md`](vehicle-core/docs/sdk-findings.md).
