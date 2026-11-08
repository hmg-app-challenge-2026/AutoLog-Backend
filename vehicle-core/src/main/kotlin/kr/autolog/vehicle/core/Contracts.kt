// 앱 A · 앱 B · 프론트의 경계 (기술명세 8.2).
// - 프론트(app/ui)는 이 파일의 인터페이스와 ContractTypes.kt 의 타입만 쓴다.
// - 앱 A·B도 서로의 구현을 직접 참조하지 않고 이 인터페이스로만 쓴다.
// - 시그니처 변경은 contract/ 브랜치 PR로만, A·B 둘 다 승인. 같은 PR에서 fake/ 도 고친다. (CONVENTIONS.md 5장)
// - 여기 함수는 예외를 던지지 않는다. 실패는 반환값(null, 빈 목록, *Result)으로 알린다. (CONVENTIONS.md 7.5)
package kr.autolog.vehicle.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kr.autolog.shared.ReceiptFields
import kr.autolog.shared.ReceiptPayload

// ===== 앱 A 제공 =====
// TODO(A): TripSessionApi, LocalStore, ReportApi, DeviceApi (기술명세 8.2)

// ===== 앱 B 제공 =====

/** 내비 목적지와 현재 좌표 (F-06, F-07). 구현: NaviGateway + LocationGateway. */
interface ContextApi {
    /** 지금 내비에 설정된 목적지. 없으면 null. 앱 A: 운행 종료 때 Trip에 저장 / 프론트: 메인 화면 표시. */
    val destination: StateFlow<DestinationInfo?>

    /** 가장 최근 좌표. 오래된 값일 수 있다. 프론트: 위치 표시. */
    val latestFix: StateFlow<LocationFix?>

    /** 내비가 알려 준 도착. 앱 A: 정차 감지 보조 신호 (F-04 "RouteState=도착"). */
    val arrivals: Flow<ArrivalInfo>

    /** 30초 이내 좌표. 없으면 잠깐(최대 3초) 기다리고, 그래도 없으면 null. 앱 A: 출발·도착 스냅샷 (F-07). */
    suspend fun freshFix(): LocationFix?

    /** 내비 최근 목적지. 실패하면 빈 목록. 프론트: 목적지 없이 끝난 운행의 도착지 후보 (F-06). */
    suspend fun recentDestinations(): List<DestinationInfo>
}

/**
 * 목적·운전자·장소 추천 (F-08, F-15, F-17, F-20). 결과는 추천일 뿐이고 확정은 사용자가 한다.
 * 구현: domain/ 의 PurposeRules, DriverResolver, UserOrdering, PlaceSuggester.
 */
interface PurposeEngine {
    /** 앱 A: 운행 종료(FINALIZING) 때 불러 PendingReview에 담는다. 근거가 없으면 NEEDS_CHECK(신뢰도 낮음). */
    suspend fun suggest(trip: TripFacts): Suggestion

    /** 앱 A: 운전자가 비어 있는 운행에 채울 후보. 근거가 없으면 null — 미확정으로 둔다 (F-20). */
    suspend fun driverCandidate(trip: TripFacts): DriverCandidate?

    /** 프론트: 운전자 선택 화면 (F-15). 배차된 사람 → 최근 운전자 → 이름순. */
    fun orderedUsers(vehicleId: String, now: Long): Flow<List<UserCard>>

    /** 프론트: 목적 확정 직후 "이곳을 거래처로 등록할까요?"를 띄울지 (F-17). */
    suspend fun shouldProposePlace(trip: TripFacts, detail: PurposeDetail): Boolean
}

/** 음성 입력 (F-09, F-18). 정차 중에만 부른다. 구현: speech/ (Gleo STT). */
interface VoiceApi {
    /** 프론트: 목적 확인 화면의 마이크 버튼. */
    suspend fun listenPurpose(): VoiceResult<VoicePurpose>

    /** 프론트: 주유 기록의 마이크 버튼. */
    suspend fun listenFuel(): VoiceResult<VoiceFuel>
}

/** 영수증 (F-19, F-30). 구현: domain/ReceiptMatcher + 서버 QR 토큰 API. */
interface ReceiptApi {
    /** 프론트: [영수증 저장] 버튼 → 폰으로 찍을 QR 표시. */
    suspend fun issueQr(): QrResult

    /** 프론트: 영수증 확인 화면의 연결 운행 후보. 최대 3개, 촬영 ±2시간, 주유소 정차 우선. */
    suspend fun candidates(receipt: ReceiptPayload): List<TripCandidate>

    /** 프론트: [확정]. 고친 값으로 비용을 저장하고 운행에 연결한 뒤 inbox를 ack한다. [tripId]가 null이면 운행 미연결. */
    suspend fun confirm(receipt: ReceiptPayload, tripId: String?, edits: ReceiptFields): ConfirmResult
}

/** 운행기록부 메일 (F-22, F-24). 구현: mail/ (SMTP). */
interface MailApi {
    /** 프론트: 보내기 버튼. 앱 A가 만든 XLSX를 첨부해 보낸다. */
    suspend fun send(report: ReportFile, to: List<String>): MailResult

    /** 프론트: 설정 화면. 접속을 한 번 확인한 뒤 비밀번호는 암호화해 저장한다. */
    suspend fun saveSmtp(config: SmtpConfig): MailResult

    /** 프론트: 보내기 버튼 활성화 여부. */
    fun smtpConfigured(): Boolean
}

// ===== 앱 B(서버) 제공, 앱 A(InboxSyncWorker) 소비 — shared 모듈 =====
// POST /v1/devices · GET /v1/inbox · POST /v1/inbox/{id}/ack · POST /v1/qr-tokens   (기술명세 4.3)
