package kr.autolog.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class InboxPayloadTest {
    @Test
    fun `payload round-trips through shared Json`() {
        val original: InboxPayload = UsersPayload(listOf(UserDto("u1", "김운전", "영업", null)))
        val text = AutoLogJson.encodeToString(InboxPayload.serializer(), original)
        assertEquals(original, AutoLogJson.decodeFromString(InboxPayload.serializer(), text))
    }
}
