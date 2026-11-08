<!-- 제목: type(scope): 요약 (기능ID)  예) feat(vehicle): Odometer 리스너를 Flow로 노출 (F-05) -->

## 무엇을
<!-- 한두 줄 -->

## 기능 ID
<!-- F-05, F-10 … (명세 3장) -->

## 확인 방법
<!-- 단위 테스트 이름 / 에뮬레이터 logcat 캡처 / curl 예시 -->

## 체크리스트 (CONVENTIONS.md 10장)
- [ ] 내 소유 폴더만 고쳤다 (아니면 소유자를 리뷰어로 넣었다)
- [ ] `Contracts.kt`·`shared` 변경이면 `contract/` 브랜치이고 Fake도 같이 고쳤다
- [ ] 새 규칙·계산에 단위 테스트가 있다
- [ ] 거리를 추정하지 않는다. 자동값에 `source`·`status`가 있다
- [ ] SDK import는 Gateway 패키지 안에만 있다
- [ ] 로그·코드에 비밀값·카드번호·정밀 좌표가 없다
- [ ] 검증한 SDK 동작은 `sdk-findings.md`에 적었다
