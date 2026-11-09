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

    @Test
    fun `옛 서버의 기기 등록 응답에 번호판이 없어도 읽힌다`() {
        val old = """{"device_token":"t","vehicle_id":"v","org_id":"o"}"""

        val r = AutoLogJson.decodeFromString(DeviceRegisterResponse.serializer(), old)

        assertEquals(null, r.plateNo)
        assertEquals(
            "12가3456",
            AutoLogJson.decodeFromString(
                DeviceRegisterResponse.serializer(),
                AutoLogJson.encodeToString(DeviceRegisterResponse.serializer(), r.copy(plateNo = "12가3456")),
            ).plateNo,
        )
    }
}
