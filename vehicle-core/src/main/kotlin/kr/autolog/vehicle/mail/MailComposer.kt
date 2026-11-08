package kr.autolog.vehicle.mail

import kr.autolog.vehicle.core.ReportFile
import java.util.Locale

/** 운행기록부 메일의 제목·본문·첨부 이름 (F-22). 순수 함수라 JVM 테스트가 된다. 본문은 사용자가 입력하지 않는다. */
object MailComposer {
    const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    /** 예: "[AutoLog] 2026-11 운행기록부 — 12가3456" */
    fun subject(r: ReportFile): String = "[AutoLog] ${r.month} 운행기록부 — ${r.plateNo}"

    fun body(r: ReportFile): String = buildString {
        appendLine("${r.month} ${r.plateNo} 운행기록부를 보내드립니다.")
        appendLine()
        appendLine("- 운행 ${r.tripCount}건")
        appendLine("- 총 주행거리 ${String.format(Locale.ROOT, "%,.1f", r.totalKm)} km")
        if (r.reviewCount > 0) {
            appendLine("- 검토 필요 ${r.reviewCount}건 (첨부 엑셀의 \"검토\" 열을 확인해 주세요)")
        } else {
            appendLine("- 검토 필요 없음")
        }
        appendLine()
        append("이 메일은 차량의 AutoLog 앱이 자동으로 작성했습니다.")
    }

    /** 예: "운행기록부_2026-11_12가3456.xlsx" */
    fun attachmentName(r: ReportFile): String = "운행기록부_${r.month}_${r.plateNo.replace(" ", "")}.xlsx"
}
