# Pleos SDK 검증 결과

> 기술명세 6장 순서대로 채운다. 에뮬레이터 값인지 실차 값인지 반드시 표기.

| # | 항목 | 결과 | 환경 | 날짜 | 비고 |
|---|---|---|---|---|---|
| 1 | Vehicle Service 연결 | | | | |
| 2 | Odometer 단위·소수점·갱신 주기 | | | | |
| 3 | NaviHelper 목적지 콜백 | | | | |
| 4 | Fused Location 콜백·accuracy | | | | |
| 4a | 내비 전환 후 백그라운드 콜백 | | | | |
| 4b | 절전 후 프로세스 생존 | | | | |
| 5 | CarInfo·DrivingMode 제공 범위 | | | | |
| 7 | XLSX 생성 속도·SMTP 587 | | | | |
| 8 | STT | | | | |

## AAR 정적 분석 (앱 B, 2026-11-08)

> 에뮬레이터 실행 전, Gradle 캐시의 AAR을 `javap`로 열어 확인한 **시그니처**다. 실제 동작(콜백이 오는지, 값 범위)은 에뮬레이터에서 다시 확인한다.

### NaviHelper 3.0.5 (`ai.pleos.playground.navi.helper.NaviHelper`)

| 항목 | 확인한 사실 | 명세와 차이 |
|---|---|---|
| 초기화 | `initialize()` / `initialize(onSuccess: () -> Unit, onError: (Throwable) -> Unit)`, `release()` | — |
| 조회 | `getDestinationInfo()`, `getRouteStateInfo()`, `getCurrentLocationInfo()`, `getRecentDestinationInfo()` 모두 **반환값 없음(void)** | 명세 2.3은 onComplete/onFailed 콜백 쌍을 가정. 실제는 **결과가 `NaviHelperEventListener`로 온다** → `NaviTracker.requestAndAwait` 로 처리 |
| 리스너 | `addListener/removeListener(NaviHelperEventListener)`. 전부 default 메서드라 필요한 것만 override | — |
| 목적지 | `onDestinationInfo(DestinationInfo)`: name, address, latitude, longitude, remainDistance(m), remainTime, poiId | — |
| 변경 알림 | `onDestinationChanged()` 는 **인자 없음** → 받으면 `getDestinationInfo()` 재호출 | — |
| 도착 | `onDestinationArrived(DestinationArrivedInfo)`: name, address, duration, distance | F-04 도착 신호로 사용 |
| 경로 상태 | `onRouteStateInfo(RouteStateInfo)`: isRouting, naviMode, routeOption … / `onRouteStarted`, `onRouteEnded`, `onRouteCancelled` | — |
| 현재 위치 | `onCurrentLocationInfo(CurrentLocationInfo)`: latitude, longitude, heading, address | F-07 폴백 |
| 상태·오류 | `onNavigationStatus(NavigationStatus: INACTIVE/INITIALIZING/FREE_DRIVE/ROUTE_DRIVE)`, `onError(NaviErrorCode)` | — |
| 매니페스트 | `<queries>` 에 `ai.pleos.playground.intent.action.NAVI_START` (SDK가 자동 병합), 서비스 최소 버전 2.0.1 | 별도 권한 선언 없음 |

### FusedLocation 1.0.4 (`ai.pleos.playground.location.fused.FusedLocation`)

| 항목 | 확인한 사실 | 명세와 차이 |
|---|---|---|
| 초기화 | `initialize()`, `release()` (콜백 없음) | — |
| 콜백 | `registerFusedLocationCallback / unregisterFusedLocationCallback(FusedLocationListener)`, `onChanged(LocationData)` 하나 | — |
| 값 | `LocationData(latitude, longitude, altitude, heading: Float, confidence: Float, timestampMillis)` | **accuracy(m) 필드 없음, confidence만 있음** → 명세 6장 대체안 "반경 매칭에 고정 여유 50 m" 적용 예정. confidence 범위는 [검증 필요] |
| 권한 | `PleosPermission.FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"` → vehicle-core 매니페스트에 선언 | 런타임 요청 필요 여부 [검증 필요] |

## Gleo AI SpeechToText 3.3.1 — AAR 정적 분석 (앱 B, 2026-11-09)

에뮬레이터 실행 전, Pleos Nexus의 `ai.pleos.playground:SpeechToText:3.3.1` AAR을 javap로 확인한 결과다.

| 항목 | 내용 |
|---|---|
| 좌표 | `ai.pleos.playground:SpeechToText:3.3.1` (의존: `ai.pleos.playground.security:universal-access:0.6.0`, gson, core-ktx). TextToSpeech 3.3.1도 같은 저장소에 있음 |
| 생성 | `SpeechToText(context, Mode)` — Mode: `HYBRID` / `SERVER` / `ON_DEVICE` |
| 순서 | `initialize(OnInitializeListener{onSuccess, onFailure(ErrorType)})` → `addListener(ResultListener)` → `request()` → (결과) → `stop()` → 앱 종료 때 `release()` |
| 결과 | `ResultListener.onUpdated(text: String, isFinal: Boolean)`, 그 밖에 `onReady`, `onStartedRecognition`, `onEndedRecognition`, `onUpdatedEpdData`, `onUpdatedRms` |
| 오류 | `onError(ErrorType)` — AUDIO_ERROR, MIC_ERROR, NETWORK_ERROR, TIMEOUT, UNAUTHENTICATED, PERMISSION_DENIED, UNAVAILABLE_LANGUAGE, NOT_SUPPORTED_API 등 |
| 서비스 | 매니페스트에 `ai.pleos.playground.caas.intent.action.SPEECH_START` queries, CAAS 최소 버전 4.1.29. 마이크는 CAAS 서비스가 쓰는 구조로 보임 |
| 기타 | `registerApp(...)`(서버 연결용으로 보임), `setLocale`, `sendAudio` 존재 |

**[검증 필요]** HYBRID가 `registerApp` 없이 동작하는지, `isFinal=true`가 한 번만 오는지, 앱에 RECORD_AUDIO 권한이 필요한지. 확인 전까지 `SpeechGateway.start()`가 실패하면 `available=false`로 두고 마이크 버튼을 숨긴다(F-09 예외).
