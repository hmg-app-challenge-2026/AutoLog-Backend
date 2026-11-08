# `kr.autolog.vehicle.fake`

`core/Contracts.kt` 의 Fake 구현. 프론트가 화면을 만들 때, A·B가 서로를 기다리지 않고 테스트할 때 쓴다. main 소스셋에 둔다.

| Fake | 인터페이스 | 만든 사람 | 화면에서 바꿔 볼 것 |
|---|---|---|---|
| `FakeContext` | ContextApi | B | `setDestination(null)` 목적지 없음, `arrive()` 도착 |
| `FakePurposeEngine` | PurposeEngine | B | `suggestion = UNSURE` 낮은 신뢰도, `driver = null` 운전자 미확정 |
| `FakeVoiceApi` | VoiceApi | B | `purpose = VoiceResult.NotUnderstood("…")` 인식 실패 |
| `FakeReceiptApi` | ReceiptApi | B | `SAMPLE_RECEIPT` / `SAMPLE_UNSURE` / `SAMPLE_FAILED` 세 가지 영수증 |
| `FakeMailApi` | MailApi | B | `configured = false` 설정 전, `nextResult = Failed(…)` 발송 실패 |
| `FakeTripSession` 등 | TripSessionApi, LocalStore, ReportApi, DeviceApi | A | (A 작성 예정) |
