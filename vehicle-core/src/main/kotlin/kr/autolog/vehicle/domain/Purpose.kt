package kr.autolog.vehicle.domain

/** 국세청 운행기록부 구분 (기획서 3.2). */
enum class PurposeCategory(val labelKo: String) {
    BUSINESS("일반 업무용"),
    COMMUTE("출퇴근용"),
    NON_BUSINESS("비업무용"),
    UNDETERMINED("미확정"),
}

/** 세부 목적. 저장할 때는 name 문자열로 쓴다(TripEntity.purposeDetail). */
enum class PurposeDetail(val category: PurposeCategory, val labelKo: String) {
    CLIENT_VISIT(PurposeCategory.BUSINESS, "거래처 방문"),
    FIELD_WORK(PurposeCategory.BUSINESS, "외근"),
    BUSINESS_TRIP(PurposeCategory.BUSINESS, "출장"),
    MEETING(PurposeCategory.BUSINESS, "회의"),
    BRANCH_MOVE(PurposeCategory.BUSINESS, "지점 이동"),
    COMMUTE_IN(PurposeCategory.COMMUTE, "출근"),
    COMMUTE_OUT(PurposeCategory.COMMUTE, "퇴근"),
    PERSONAL(PurposeCategory.NON_BUSINESS, "개인 이동"),
    NEEDS_CHECK(PurposeCategory.UNDETERMINED, "확인 필요"),
    ;

    companion object {
        /** 서버·Room 문자열을 안전하게 읽는다. 모르는 값이면 null. */
        fun fromName(name: String?): PurposeDetail? = entries.firstOrNull { it.name == name }
    }
}

/** 어느 규칙이 추천했는지. 출처를 남기기 위한 값(CONVENTIONS 7.1). */
enum class PurposeRuleId {
    SCHEDULE,
    COMMUTE_IN,
    COMMUTE_OUT,
    CLIENT,
    BRANCH,
    PAST_CONFIRMED,
    WEEKEND_UNKNOWN,
    FALLBACK,
}

/** 목적 추천 결과. 화면은 [detail]을 큰 버튼으로 보여 주고 사용자가 1탭으로 확정한다. */
data class Suggestion(
    val detail: PurposeDetail,
    val confidence: Double,
    val rule: PurposeRuleId,
    /** 배차에서 온 경우 그 배차 id. */
    val scheduleId: String? = null,
) {
    val category: PurposeCategory get() = detail.category

    /** 확신이 낮으면 화면이 추천 버튼 대신 선택지를 먼저 보여 준다(기획서 3.2 "확신도가 낮으면 반드시 묻는다"). */
    val isConfident: Boolean get() = confidence >= CONFIDENT_THRESHOLD

    companion object {
        const val CONFIDENT_THRESHOLD = 0.8
    }
}
