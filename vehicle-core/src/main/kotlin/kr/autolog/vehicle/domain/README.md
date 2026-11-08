# `kr.autolog.vehicle.domain`

[앱 B] 차 안 판단 로직. Android 의존 없이 JVM 테스트로 돈다(CONVENTIONS 7.4). 데이터는 `core/DomainStore`(앱 A가 Room으로 구현)로 읽고 쓴다.

| 파일 | 기능 | 역할 |
|---|---|---|
| `PurposeRules`, `PlaceMatcher` | F-08 | 목적 추천 규칙 a–h, 좌표 → 등록 장소 |
| `UserOrdering` | F-15 | 운전자 카드 순서: 배차 ±1h → 직전 운전자 → 최근 → 가나다 |
| `DriverResolver` | F-15, F-20 | 운전자 후보: 선택 1.0 → 배차 1명 0.95 → 2h 내 직전 운행 0.60 → 미확정 |
| `DefaultPurposeEngine` | F-08, F-15, F-17, F-20 | `PurposeEngine` 구현. 저장소에서 읽어 위 규칙에 넘김, 거래처 등록 제안·저장 |
| `ReceiptMatcher`, `ReceiptArithmetic` | F-19 | 영수증 ±2h 운행 후보(주유소 정차 우선, 최대 3), 금액 검산 |
| `ReceiptService` | F-19, F-30 | `ReceiptApi` 구현. QR 발급, 후보, 확정(검산·중복·카드 4자리·고친 필드 기록) |
| `SettingsService`, `RecipientBook` | F-24 | `SettingsApi` 구현. 수신자·직원 직접 입력, 월말 자동 발송 스위치, 지금 동기화 |
