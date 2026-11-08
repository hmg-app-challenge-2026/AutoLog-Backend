package kr.autolog.vehicle

import org.junit.Assert.assertEquals
import org.junit.Test

// 로직은 화면 없이 JVM 단위 테스트로 검증한다. (./gradlew :vehicle-core:test)
class PurposeTest {
    @Test
    fun `commute purposes are counted as commute category`() {
        assertEquals(Purpose.Category.COMMUTE, Purpose.COMMUTE_IN.category)
        assertEquals(Purpose.Category.COMMUTE, Purpose.COMMUTE_OUT.category)
    }
}
