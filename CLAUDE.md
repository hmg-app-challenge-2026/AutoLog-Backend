# AutoLog — AI 코딩 도구용 안내

이 저장소에서 코드를 쓰거나 고칠 때는 아래 컨벤션을 따른다.

@CONVENTIONS.md

- 명세는 `docs/`에 있다. 기능 ID(F-xx)는 `docs/AutoLog_기능명세.md` 3장 기준.
- 작업 전에 지금 사용자가 앱 A / 앱 B / 프론트 중 누구인지 확인하고, 그 사람의 소유 폴더(CONVENTIONS.md 1.2) 밖은 고치지 않는다.
- 커밋 전 `./gradlew ktlintFormat` 후 `./gradlew ktlintCheck test`.
