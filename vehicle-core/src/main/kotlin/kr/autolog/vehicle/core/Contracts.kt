// 앱 A · 앱 B · 프론트의 경계 (기술명세 8.2).
// - 프론트(app/ui)는 이 파일의 인터페이스만 부른다.
// - 앱 A·B도 서로의 구현을 직접 참조하지 않고 이 인터페이스로만 쓴다.
// - 시그니처 변경은 contract/ 브랜치 PR로만, A·B 둘 다 승인. (CONVENTIONS.md 5장)
//
// TODO(A,B): 11.08–09에 함께 작성
//   앱 A: TripSessionApi, LocalStore, ReportApi, DeviceApi
//   앱 B: ContextApi, PurposeEngine, VoiceApi, ReceiptApi, MailApi
@file:Suppress("ktlint:standard:no-empty-file")

package kr.autolog.vehicle.core
