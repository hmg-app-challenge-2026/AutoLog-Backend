package kr.autolog.vehicle.mail

import kr.autolog.vehicle.core.ReportFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.YearMonth

class MailComposerTest {
    private fun report(review: Int) = ReportFile(YearMonth.of(2026, 1), File("x.xlsx"), "12가 3456", 3, 0.0, review)

    @Test
    fun `제목은 연월과 차량번호를 담는다`() {
        assertEquals("[AutoLog] 2026-01 운행기록부 — 12가 3456", MailComposer.subject(report(0)))
    }

    @Test
    fun `검토할 건이 없으면 없다고 적는다`() {
        assertTrue(MailComposer.body(report(0)).contains("검토 필요 없음"))
    }

    @Test
    fun `첨부 이름에는 공백을 넣지 않는다`() {
        assertEquals("운행기록부_2026-01_12가3456.xlsx", MailComposer.attachmentName(report(0)))
    }
}
