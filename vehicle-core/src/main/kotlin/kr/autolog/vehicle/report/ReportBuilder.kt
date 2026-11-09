package kr.autolog.vehicle.report

import kr.autolog.vehicle.core.ExpenseSource
import kr.autolog.vehicle.core.PurposeCategory
import kr.autolog.vehicle.core.ReportApi
import kr.autolog.vehicle.core.ReportBuildResult
import kr.autolog.vehicle.core.ReportFile
import kr.autolog.vehicle.report.XlsxWriter.Cell
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** 운행기록부 재료. Room 구현은 data/RoomReportSource. */
interface ReportSource {
    suspend fun trips(from: Long, to: Long): List<ReportTrip>

    suspend fun expenses(from: Long, to: Long): List<ReportExpense>

    suspend fun schedules(from: Long, to: Long): List<ReportSchedule>

    suspend fun gasStations(): Set<String>

    /** 계산한 검토 표시를 운행에 남긴다(이력 화면용). */
    suspend fun saveReviewFlags(flags: Map<String, Set<ReviewFlag>>) {}
}

/**
 * 운행기록부 XLSX (F-21) — [ReportApi] 구현. 국세청 업무용승용차 운행기록부 양식 + 검토 열.
 * 시트: ① 운행기록부 ② 비용 ③ 요약 ④ 검토. 사람이 확정한 목적만 업무거리에 넣고, 미확정은 0 + 비고. 셀 수식 없음.
 */
class ReportBuilder(
    private val source: ReportSource,
    private val outputDir: () -> File,
    private val plateNo: () -> String?,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: ZoneId = ZoneId.of("Asia/Seoul"),
    private val writer: XlsxWriter = XlsxWriter(),
    private val log: (String) -> Unit = {},
) : ReportApi {
    override suspend fun build(month: YearMonth): ReportBuildResult = try {
        val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val trips = source.trips(from, to).sortedBy { it.startTime }
        val expenses = source.expenses(from, to).sortedBy { it.occurredAt }
        val review = ReviewCalculator.calculate(
            trips,
            expenses,
            source.schedules(from, to),
            source.gasStations(),
            clock.millis(),
        )
        source.saveReviewFlags(review.tripFlags)
        val summary = Summary.of(trips, expenses, review)
        val plate = plateNo() ?: UNREGISTERED_PLATE
        val dir = outputDir().apply { mkdirs() }
        val file = File(dir, "운행기록부_${plate.replace(Regex("[\\s/\\\\:]"), "")}_${month.format(YYYYMM)}.xlsx")
        file.outputStream().use { out ->
            writer.write(
                listOf(
                    logSheet(month, plate, trips, review, summary),
                    expenseSheet(expenses, trips, review),
                    summarySheet(month, plate, summary),
                    reviewSheet(review),
                ),
                out,
            )
        }
        log("report ${file.name}: ${trips.size} trips, ${review.count} reviews")
        ReportBuildResult.Built(ReportFile(month, file, plate, trips.size, summary.totalKm, review.count))
    } catch (e: IOException) {
        log("report failed: ${e.message}")
        ReportBuildResult.Failed("운행기록부를 저장하지 못했어요. 저장 공간을 확인해 주세요")
    }

    /** 시트 1과 시트 3이 같은 숫자를 쓰도록 한 번만 계산한다(F-21 수용 기준 "시트 1 합계 = 시트 3"). */
    data class Summary(
        val tripCount: Int,
        val totalKm: Double,
        val commuteKm: Double,
        val businessKm: Double,
        val nonBusinessKm: Double,
        val undeterminedKm: Double,
        val expenseWon: Int,
        val liters: Double,
        val reviewCount: Int,
    ) {
        /** 업무사용비율(%) = (출퇴근 + 일반 업무) ÷ 총 주행거리. 국세청 양식 기준. */
        val businessRatio: Double get() = if (totalKm > 0) (commuteKm + businessKm) / totalKm * 100 else 0.0

        companion object {
            fun of(trips: List<ReportTrip>, expenses: List<ReportExpense>, review: ReviewResult): Summary {
                fun km(c: PurposeCategory?) = sumKm(trips.filter { it.purpose?.category == c }.map { it.distanceKm })
                return Summary(
                    tripCount = trips.size,
                    totalKm = sumKm(trips.map { it.distanceKm }),
                    commuteKm = km(PurposeCategory.COMMUTE),
                    businessKm = km(PurposeCategory.BUSINESS),
                    nonBusinessKm = km(PurposeCategory.NON_BUSINESS),
                    undeterminedKm = km(null) + km(PurposeCategory.UNDETERMINED),
                    expenseWon = expenses.sumOf { it.amountWon },
                    liters = sumKm(expenses.map { it.liters }),
                    reviewCount = review.count,
                )
            }
        }
    }

    private fun logSheet(month: YearMonth, plate: String, trips: List<ReportTrip>, review: ReviewResult, s: Summary) =
        XlsxWriter.Sheet(
            name = "운행기록부",
            rows = buildList {
                add(listOf(Cell.Text("업무용승용차 운행기록부", bold = true)))
                add(listOf(Cell.Text("사용 기간: ${month.format(YYYY_MM)} · 차량번호: $plate")))
                add(LOG_HEADER.map { Cell.Text(it, bold = true) })
                trips.forEach { t ->
                    val cat = t.purpose?.category
                    add(
                        listOf(
                            Cell.Text(date(t.startTime)),
                            Cell.Text(dayOfWeek(t.startTime)),
                            text(t.dept),
                            Cell.Text(t.userName ?: "미확정"),
                            text(route(t)),
                            num(t.startKm),
                            num(t.endKm),
                            num(t.distanceKm),
                            num(if (cat == PurposeCategory.COMMUTE) t.distanceKm else null),
                            num(if (cat == PurposeCategory.BUSINESS) t.distanceKm else null),
                            Cell.Text(t.purpose?.labelKo ?: "미확정"),
                            text(remark(t)),
                            text(review.tripFlags[t.tripId]?.joinToString(", ") { it.labelKo }),
                        ),
                    )
                }
                add(
                    listOf(Cell.Text("합계", bold = true), null, null, null, null, null, null) +
                        listOf(s.totalKm, s.commuteKm, s.businessKm).map { Cell.Num(it, bold = true) },
                )
                add(listOf(Cell.Text("업무사용비율(%)", bold = true), Cell.Num(s.businessRatio, bold = true)))
            },
            widths = listOf(11, 5, 10, 8, 30, 12, 12, 10, 10, 10, 12, 26, 20),
            merges = listOf("A1:M1", "A2:M2"),
        )

    private fun expenseSheet(
        expenses: List<ReportExpense>,
        trips: List<ReportTrip>,
        review: ReviewResult,
    ): XlsxWriter.Sheet {
        val tripById = trips.associateBy { it.tripId }
        return XlsxWriter.Sheet(
            name = "비용",
            rows = buildList {
                add(EXPENSE_HEADER.map { Cell.Text(it, bold = true) })
                expenses.forEach { e ->
                    add(
                        listOf(
                            Cell.Text(date(e.occurredAt)),
                            Cell.Text("주유"),
                            text(e.merchant),
                            text(e.fuelType),
                            num(e.liters, 2),
                            num(e.unitPrice?.toDouble(), 0),
                            Cell.Num(e.discountWon.toDouble(), 0),
                            Cell.Num(e.amountWon.toDouble(), 0),
                            text(e.cardLast4?.let { "카드 ****$it" }),
                            text(
                                e.tripIds.mapNotNull {
                                    tripById[it]
                                }.joinToString(", ") { "${date(it.startTime)} ${route(it)}" },
                            ),
                            Cell.Text(EVIDENCE.getValue(e.source)),
                            text(review.expenseFlags[e.expenseId]?.joinToString(", ") { it.labelKo }),
                        ),
                    )
                }
                add(
                    listOf(Cell.Text("합계", bold = true)) + List(6) { null } +
                        Cell.Num(expenses.sumOf { it.amountWon }.toDouble(), 0, bold = true),
                )
            },
            widths = listOf(11, 6, 16, 8, 9, 9, 9, 11, 14, 32, 10, 14),
        )
    }

    private fun summarySheet(month: YearMonth, plate: String, s: Summary) = XlsxWriter.Sheet(
        name = "요약",
        rows = listOf(
            listOf(Cell.Text("${month.format(YYYY_MM)} 운행 요약 · $plate", bold = true)),
            listOf(Cell.Text("운행 건수"), Cell.Num(s.tripCount.toDouble(), 0)),
            listOf(Cell.Text("총 주행거리(km)"), Cell.Num(s.totalKm)),
            listOf(Cell.Text("출·퇴근용(km)"), Cell.Num(s.commuteKm)),
            listOf(Cell.Text("일반 업무용(km)"), Cell.Num(s.businessKm)),
            listOf(Cell.Text("비업무용(km)"), Cell.Num(s.nonBusinessKm)),
            listOf(Cell.Text("미확정(km)"), Cell.Num(s.undeterminedKm)),
            listOf(Cell.Text("업무사용비율(%)"), Cell.Num(s.businessRatio)),
            listOf(Cell.Text("주유 비용 합계(원)"), Cell.Num(s.expenseWon.toDouble(), 0)),
            listOf(Cell.Text("주유량(L)"), Cell.Num(s.liters, 2)),
            listOf(Cell.Text("검토 필요(건)"), Cell.Num(s.reviewCount.toDouble(), 0)),
        ),
        widths = listOf(20, 14),
    )

    private fun reviewSheet(review: ReviewResult) = XlsxWriter.Sheet(
        name = "검토",
        rows = listOf(listOf("구분", "일시", "대상", "해소 방법").map { Cell.Text(it, bold = true) }) +
            review.issues.map { i ->
                listOf(
                    Cell.Text(i.flag.labelKo),
                    Cell.Text(dateTime(i.at)),
                    Cell.Text(i.target),
                    Cell.Text(i.flag.resolution),
                )
            },
        widths = listOf(16, 17, 30, 60),
    )

    private fun remark(t: ReportTrip): String = listOfNotNull(
        t.note,
        "앱 미실행 중 운행(시각 추정)".takeIf { t.timeEstimated && t.note?.contains("앱 미실행") != true },
        "목적 미확정 — 업무거리 0".takeIf { t.purpose == null || t.purpose.category == PurposeCategory.UNDETERMINED },
        "운전자 미확정".takeIf { t.userId == null },
    ).joinToString(" · ")

    private fun route(t: ReportTrip) = listOfNotNull(t.originLabel, t.destinationLabel).joinToString(" → ")

    private fun date(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).format(DATE)

    private fun dateTime(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).format(DATE_TIME)

    private fun dayOfWeek(ms: Long) =
        Instant.ofEpochMilli(ms).atZone(zone).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)

    private fun text(v: String?) = v?.takeIf { it.isNotBlank() }?.let { Cell.Text(it) }

    private fun num(v: Double?, decimals: Int = 1) = v?.let { Cell.Num(it, decimals) }

    companion object {
        const val UNREGISTERED_PLATE = "미등록"

        /** 10진수 그대로 더한다. Double로 더하면 23.4 + 12.8 + 8.6 + 5.0 = 49.800000000000004가 된다. */
        fun sumKm(values: List<Double?>): Double = values.filterNotNull().fold(java.math.BigDecimal.ZERO) { acc, v ->
            acc + java.math.BigDecimal(v.toString())
        }.toDouble()
        private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        private val YYYYMM = DateTimeFormatter.ofPattern("yyyyMM")
        private val YYYY_MM = DateTimeFormatter.ofPattern("yyyy-MM")
        val LOG_HEADER = listOf(
            "사용일자", "요일", "부서", "성명", "출발 → 도착", "주행 전 계기판(km)", "주행 후 계기판(km)", "주행거리(km)",
            "출·퇴근용(km)", "일반 업무용(km)", "목적", "비고", "검토",
        )
        val EXPENSE_HEADER = listOf(
            "일자", "구분", "상호", "유종", "수량(L)", "단가(원)", "할인(원)", "금액(원)", "결제수단", "연결 운행", "증빙", "검토",
        )
        private val EVIDENCE = mapOf(
            ExpenseSource.OCR to "영수증",
            ExpenseSource.VOICE to "음성 입력",
            ExpenseSource.TAP to "직접 입력",
        )
    }
}
