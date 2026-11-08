package kr.autolog.relay.receipt

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.max

/**
 * 긴 변 1600px JPEG 썸네일 (기술명세 4.5 단계 3).
 * ImageIO가 못 읽는 형식(HEIC 등)이면 null → 원본을 썸네일로 쓴다.
 * TODO: 카드번호 영역 블러 — Gemini가 좌표를 주지 않아 아직 불가. 카드번호는 OCR 결과에서만 마스킹한다.
 */
object Thumbnailer {
    const val MAX_EDGE_PX = 1600

    fun jpeg(image: ByteArray): ByteArray? {
        val src = runCatching { ImageIO.read(ByteArrayInputStream(image)) }.getOrNull() ?: return null
        val scale = MAX_EDGE_PX.toDouble() / max(src.width, src.height)
        val (w, h) = if (scale <
            1
        ) {
            (src.width * scale).toInt() to (src.height * scale).toInt()
        } else {
            src.width to src.height
        }
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        out.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(src, 0, 0, w, h, null)
            dispose()
        }
        return ByteArrayOutputStream().also { ImageIO.write(out, "jpg", it) }.toByteArray()
    }
}
