# `kr.autolog.vehicle.mail`

[앱 B] 운행기록부 메일 발송 (F-22)과 SMTP 계정 저장 (F-24).

| 파일 | 역할 |
|---|---|
| `MailService` | `MailApi` 구현. 설정 확인 → 메일 작성 → 발송, 결과를 `MailResult`로 |
| `SmtpMailer` | Jakarta Mail SMTP. 587 STARTTLS / 465 SSL, 오류를 AUTH·NETWORK·INVALID_RECIPIENT로 분류 |
| `MailComposer` | 제목·본문·첨부 이름 (순수 함수) |
| `SmtpCredentialStore` | 계정 저장. 실제 구현은 `EncryptedSmtpCredentialStore`(EncryptedSharedPreferences) |

남은 일: 오프라인 "전송 대기" 큐(reports.sendState, 앱 A Room + WorkManager)와 연결, 587 차단 시 HTTPS 메일 API 폴백, 에뮬레이터·차량 LTE에서 587 실제 발송 검증.
