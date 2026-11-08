package kr.autolog.relay.receipt

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kr.autolog.shared.AutoLogJson
import kr.autolog.shared.ReceiptFields
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class ReceiptRecord(
    val receiptId: UUID,
    val vehicleId: UUID,
    val sha256: String,
    val storageKey: String,
    val thumbKey: String,
    val takenAt: Instant,
    val ocrStatus: String,
    val fields: ReceiptFields?,
    val confidence: Map<String, Double>,
    val arithOk: Boolean?,
    val expiresAt: Instant,
)

interface ReceiptRepository {
    fun exists(vehicleId: UUID, sha256: String): Boolean

    /** (vehicle_id, sha256) 유니크 위반이면 DuplicateKeyException. */
    fun insert(r: ReceiptRecord)

    /** 그 차의 영수증 썸네일 키. 없거나(만료 삭제) 다른 차 것이면 null. */
    fun thumbKey(receiptId: UUID, vehicleId: UUID): String?

    /** 만료된 영수증의 저장소 키 목록을 돌려주고 행을 지운다. */
    fun deleteExpired(now: Instant): List<String>
}

@Repository
class JdbcReceiptRepository(private val jdbc: JdbcClient) : ReceiptRepository {
    override fun exists(vehicleId: UUID, sha256: String): Boolean = jdbc
        .sql("SELECT count(*) FROM receipts WHERE vehicle_id = :v AND sha256 = :h")
        .param("v", vehicleId)
        .param("h", sha256)
        .query(Long::class.java)
        .single() > 0

    override fun insert(r: ReceiptRecord) {
        jdbc
            .sql(
                """
                INSERT INTO receipts (receipt_id, vehicle_id, sha256, storage_key, thumb_key, taken_at,
                  ocr_status, ocr_fields, ocr_confidence, arith_ok, expires_at)
                VALUES (:id, :v, :h, :sk, :tk, :taken, :status, CAST(:fields AS jsonb), CAST(:conf AS jsonb), :arith, :exp)
                """.trimIndent(),
            ).param("id", r.receiptId)
            .param("v", r.vehicleId)
            .param("h", r.sha256)
            .param("sk", r.storageKey)
            .param("tk", r.thumbKey)
            .param("taken", Timestamp.from(r.takenAt))
            .param("status", r.ocrStatus)
            .param("fields", r.fields?.let { AutoLogJson.encodeToString(ReceiptFields.serializer(), it) })
            .param(
                "conf",
                AutoLogJson.encodeToString(MapSerializer(String.serializer(), Double.serializer()), r.confidence),
            )
            .param("arith", r.arithOk)
            .param("exp", Timestamp.from(r.expiresAt))
            .update()
    }

    override fun thumbKey(receiptId: UUID, vehicleId: UUID): String? = jdbc
        .sql("SELECT thumb_key FROM receipts WHERE receipt_id = :id AND vehicle_id = :v")
        .param("id", receiptId)
        .param("v", vehicleId)
        .query(String::class.java)
        .optional()
        .orElse(null)

    override fun deleteExpired(now: Instant): List<String> = jdbc
        .sql("DELETE FROM receipts WHERE expires_at <= :now RETURNING storage_key, thumb_key")
        .param("now", Timestamp.from(now))
        .query { rs, _ -> listOf(rs.getString("storage_key"), rs.getString("thumb_key")) }
        .list()
        .flatten()
        .distinct()
}
