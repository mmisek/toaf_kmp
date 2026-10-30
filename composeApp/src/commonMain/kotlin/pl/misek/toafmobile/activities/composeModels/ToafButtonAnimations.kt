package pl.misek.toafmobile.activities.composeModels

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/*
 * Looping button animations, pure Compose Canvas (no assets), works in commonMain.
 * Each scene is drawn in its own fixed coordinate space and scaled to fit the canvas.
 */

private const val SCENE_W = 160f
private const val SCENE_H = 100f
private const val PI_F = PI.toFloat()
private val OUTLINE = Color(0xFF22180F)

/** Doctor with a stethoscope listening to a car, heart monitor beating above it. */
@Composable
fun DoctorAnimation(modifier: Modifier = Modifier) {
    val t = rememberLoopTime(DOCTOR_PERIOD)
    SceneCanvas(modifier) { drawDoctorScene(t) }
}

/** Magnifying glass sliding over a car, x-ray view under the lens shows its modules. */
@Composable
fun CarScanAnimation(modifier: Modifier = Modifier) {
    val t = rememberLoopTime(SCAN_PERIOD)
    SceneCanvas(modifier) { drawScanScene(t) }
}

@Composable
private fun rememberLoopTime(period: Float): Float {
    val transition = rememberInfiniteTransition(label = "buttonAnimation")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = period,
        animationSpec = infiniteRepeatable(
            animation = tween((period * 1000).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )
    return t
}

@Composable
private fun SceneCanvas(modifier: Modifier, draw: DrawScope.() -> Unit) {
    Canvas(modifier.aspectRatio(SCENE_W / SCENE_H)) {
        val s = min(size.width / SCENE_W, size.height / SCENE_H)
        withTransform({
            translate((size.width - SCENE_W * s) / 2f, (size.height - SCENE_H * s) / 2f)
            scale(s, s, pivot = Offset.Zero)
        }) { draw() }
    }
}

/* ======================= helpers ======================= */

private fun DrawScope.blob(path: Path, fill: Color, stroke: Float = 1.6f) {
    drawPath(path, fill)
    drawPath(path, OUTLINE, style = Stroke(stroke, join = StrokeJoin.Round))
}

private fun DrawScope.blobCircle(c: Offset, r: Float, fill: Color, stroke: Float = 1.6f) {
    drawCircle(fill, r, c)
    drawCircle(OUTLINE, r, c, style = Stroke(stroke))
}

private fun DrawScope.blobOval(c: Offset, rx: Float, ry: Float, fill: Color) {
    val tl = Offset(c.x - rx, c.y - ry)
    val sz = Size(rx * 2, ry * 2)
    drawOval(fill, tl, sz)
    drawOval(OUTLINE, tl, sz, style = Stroke(1.6f))
}

private fun DrawScope.blobRect(tl: Offset, sz: Size, fill: Color, r: Float = 0f) {
    drawRoundRect(fill, tl, sz, CornerRadius(r))
    drawRoundRect(OUTLINE, tl, sz, CornerRadius(r), style = Stroke(1.6f))
}

private fun DrawScope.limb(a: Offset, b: Offset, w: Float, col: Color) {
    drawLine(OUTLINE, a, b, w + 3f, StrokeCap.Round)
    drawLine(col, a, b, w, StrokeCap.Round)
}

private fun heartPath(c: Offset, r: Float) = Path().apply {
    moveTo(c.x, c.y + r * 0.9f)
    cubicTo(c.x - r * 1.6f, c.y - r * 0.2f, c.x - r * 0.6f, c.y - r * 1.3f, c.x, c.y - r * 0.4f)
    cubicTo(c.x + r * 0.6f, c.y - r * 1.3f, c.x + r * 1.6f, c.y - r * 0.2f, c.x, c.y + r * 0.9f)
    close()
}

/* ======================= doctor ======================= */

private const val DOCTOR_PERIOD = 2.4f
private const val ECG_X0 = 84f
private const val ECG_X1 = 156f
private const val ECG_BASE = 28f
private const val ECG_BEAT_W = 36f

private val SKIN = Color(0xFFE8B98A)
private val COAT = Color(0xFFF4F4F4)
private val HAIR = Color(0xFF4A3020)
private val STEEL = Color(0xFFD5D9DF)
private val TUBE = Color(0xFF2B2B2B)
private val CAR = Color(0xFF2E5FBF)
private val GLASS = Color(0xFFBFE3FF)
private val ECG_GREEN = Color(0xFF5CFF7A)

// one heartbeat as (phase, y offset) - P wave, QRS spike, T wave
private val ECG_SHAPE = listOf(
    0f to 0f, 0.3f to 0f, 0.36f to -2.5f, 0.42f to 0f, 0.5f to 0f, 0.53f to 2f,
    0.57f to -12f, 0.61f to 4f, 0.65f to 0f, 0.75f to 0f, 0.82f to -3.5f, 0.9f to 0f, 1f to 0f,
)

private fun ecg(x: Float): Float {
    val u = ((x - ECG_X0) / ECG_BEAT_W).let { it - it.toInt() }
    for (i in 1 until ECG_SHAPE.size) {
        val (u1, y1) = ECG_SHAPE[i]
        if (u <= u1) {
            val (u0, y0) = ECG_SHAPE[i - 1]
            return y0 + (y1 - y0) * (u - u0) / (u1 - u0)
        }
    }
    return 0f
}

private fun DrawScope.drawDoctorScene(t: Float) {
    val head = ECG_X0 + (ECG_X1 - ECG_X0) * (t / DOCTOR_PERIOD)
    val phase = ((head - ECG_X0) / ECG_BEAT_W).let { it - it.toInt() }
    val beat = if (phase in 0.55f..0.8f) sin((phase - 0.55f) / 0.25f * PI_F) else 0f

    drawMonitor(head, beat)
    val carDy = -2f * beat
    drawCar(carDy)

    val piece = Offset(93f + 3f * sin(t / DOCTOR_PERIOD * 2 * PI_F), 63f + carDy)
    drawDoctor(t)
    // stethoscope: ear tubes joined under the chin, main tube down to the chest piece
    val tubes = Path().apply {
        moveTo(25f, 50f); quadraticBezierTo(24f, 66f, 38f, 74f)
        moveTo(51f, 50f); quadraticBezierTo(52f, 66f, 38f, 74f)
        moveTo(38f, 74f); cubicTo(40f, 98f, 72f, 96f, piece.x, piece.y)
    }
    drawPath(tubes, TUBE, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    limb(Offset(62f, 82f), Offset(piece.x - 5f, piece.y + 4f), 8f, COAT)
    blobCircle(Offset(piece.x - 3f, piece.y + 3f), 4f, SKIN)
    blobCircle(piece, 3.5f, STEEL)
}

private fun DrawScope.drawMonitor(head: Float, beat: Float) {
    blobRect(Offset(81f, 8f), Size(78f, 34f), Color(0xFF15211A), 4f)
    // faint full trace, bright trace up to the sweep head
    val full = Path()
    val lit = Path()
    var x = ECG_X0
    while (x <= ECG_X1) {
        val y = ECG_BASE + ecg(x)
        if (x == ECG_X0) full.moveTo(x, y) else full.lineTo(x, y)
        if (x <= head) { if (x == ECG_X0) lit.moveTo(x, y) else lit.lineTo(x, y) }
        x += 1f
    }
    drawPath(full, ECG_GREEN, alpha = 0.15f, style = Stroke(1.4f))
    drawPath(lit, ECG_GREEN, style = Stroke(1.8f, join = StrokeJoin.Round))
    drawCircle(Color.White, 2f, Offset(head, ECG_BASE + ecg(head)))
    val heart = heartPath(Offset(150f, 15f), 3.5f * (1f + 0.35f * beat))
    drawPath(heart, Color(0xFFE53935))
}

private fun DrawScope.drawCar(dy: Float) {
    translate(0f, dy) {
        val body = Path().apply {
            moveTo(84f, 82f); lineTo(84f, 70f); quadraticBezierTo(84f, 66f, 90f, 65f)
            lineTo(103f, 64f); lineTo(110f, 52f); quadraticBezierTo(112f, 50f, 116f, 50f)
            lineTo(136f, 50f); quadraticBezierTo(140f, 50f, 142f, 53f); lineTo(148f, 64f)
            quadraticBezierTo(156f, 65f, 156f, 72f); lineTo(156f, 82f); close()
        }
        blob(body, CAR)
        blob(Path().apply { moveTo(113f, 54f); lineTo(124f, 54f); lineTo(124f, 62f); lineTo(107f, 62f); close() }, GLASS)
        blob(Path().apply { moveTo(127f, 54f); lineTo(137f, 54f); lineTo(143f, 62f); lineTo(127f, 62f); close() }, GLASS)
        blobOval(Offset(87f, 71f), 2f, 2.5f, Color(0xFFFFE066))
    }
    for (wx in listOf(100f, 142f)) {
        blobCircle(Offset(wx, 84f), 7f, Color(0xFF26262A))
        drawCircle(STEEL, 3f, Offset(wx, 84f))
    }
}

private fun DrawScope.drawDoctor(t: Float) {
    blobRect(Offset(33f, 56f), Size(10f, 16f), SKIN)
    val coat = Path().apply {
        moveTo(4f, 100f); lineTo(8f, 82f); quadraticBezierTo(12f, 72f, 26f, 70f)
        lineTo(50f, 70f); quadraticBezierTo(64f, 72f, 68f, 82f); lineTo(72f, 100f); close()
    }
    blob(coat, COAT)
    blob(Path().apply { moveTo(31f, 70f); lineTo(38f, 86f); lineTo(45f, 70f); close() }, Color(0xFF9FC5E8))
    blob(Path().apply { moveTo(37f, 73f); lineTo(39f, 73f); lineTo(40.5f, 83f); lineTo(38f, 86f); lineTo(35.5f, 83f); close() }, Color(0xFFD7262E))
    blobRect(Offset(13f, 84f), Size(11f, 7f), COAT)
    drawLine(CAR, Offset(16f, 80f), Offset(16f, 86f), 1.6f, StrokeCap.Round)

    // listening: head tilts slowly towards the car
    val tilt = 0.06f + 0.05f * sin(t / DOCTOR_PERIOD * 2 * PI_F)
    rotateRad(tilt, pivot = Offset(38f, 62f)) {
        blobOval(Offset(24f, 47f), 3f, 4f, SKIN)
        blobOval(Offset(52f, 47f), 3f, 4f, SKIN)
        blobCircle(Offset(38f, 46f), 14f, SKIN)
        val hair = Path().apply {
            arcTo(Rect(Offset(38f, 45f), 15f), 190f, 160f, true)
            quadraticBezierTo(46f, 34f, 36f, 37f)
            quadraticBezierTo(28f, 38f, 24f, 44f)
            close()
        }
        blob(hair, HAIR)
        // head mirror on a band, with a travelling glint
        drawLine(Color(0xFF555A60), Offset(24f, 39f), Offset(52f, 39f), 2f)
        blobCircle(Offset(44f, 37f), 5f, STEEL)
        drawCircle(OUTLINE, 1.3f, Offset(44f, 37f))
        val glint = sin(t / DOCTOR_PERIOD * 2 * PI_F * 2).coerceAtLeast(0f)
        drawCircle(Color.White, 1.6f, Offset(42f, 35f), alpha = glint)

        val blink = t in 1.9f..2.0f
        for (ex in listOf(33f, 43f)) {
            if (blink) drawLine(OUTLINE, Offset(ex - 2f, 48f), Offset(ex + 2f, 48f), 1.4f, StrokeCap.Round)
            else drawCircle(OUTLINE, 1.7f, Offset(ex, 48f))
            drawLine(OUTLINE, Offset(ex - 2.5f, 44f), Offset(ex + 2.5f, 43.5f), 1.3f, StrokeCap.Round)
        }
        drawArc(OUTLINE, 20f, 140f, false, Offset(33f, 50f), Size(10f, 6f), style = Stroke(1.4f, cap = StrokeCap.Round))
    }
}


/* ======================= car scan ======================= */

private const val SCAN_PERIOD = 4f
private const val LENS_R = 19f
private const val ZOOM = 1.6f

private val XRAY_BG = Color(0xFF0D2A4A)
private val XRAY_LINE = Color(0xFF8FE3FF)

// control modules visible in the x-ray view: rear, seats, dashboard, engine bay
private val SCAN_MODULES = listOf(Offset(24f, 64f), Offset(72f, 64f), Offset(108f, 50f), Offset(138f, 64f))

private fun scanCarBody() = Path().apply {
    moveTo(12f, 78f); lineTo(12f, 64f); quadraticBezierTo(12f, 58f, 20f, 57f)
    lineTo(46f, 54f); lineTo(62f, 38f); quadraticBezierTo(66f, 34f, 74f, 34f)
    lineTo(108f, 34f); quadraticBezierTo(114f, 34f, 118f, 38f); lineTo(132f, 54f)
    lineTo(144f, 56f); quadraticBezierTo(150f, 58f, 150f, 66f); lineTo(150f, 78f); close()
}

private fun scanCarWindows() = listOf(
    Path().apply { moveTo(62f, 40f); lineTo(88f, 40f); lineTo(88f, 52f); lineTo(52f, 52f); close() },
    Path().apply { moveTo(92f, 40f); lineTo(114f, 40f); lineTo(126f, 52f); lineTo(92f, 52f); close() },
)

private val SCAN_WHEELS = listOf(Offset(40f, 80f), Offset(124f, 80f))

private fun DrawScope.drawScanCar() {
    drawOval(Color(0x47000000), Offset(10f, 87f), Size(142f, 8f))
    blob(scanCarBody(), CAR)
    for (w in scanCarWindows()) blob(w, GLASS)
    drawLine(OUTLINE, Offset(90f, 40f), Offset(90f, 76f), 1.4f)
    drawLine(OUTLINE, Offset(74f, 60f), Offset(80f, 60f), 1.6f, StrokeCap.Round)
    drawLine(OUTLINE, Offset(100f, 60f), Offset(106f, 60f), 1.6f, StrokeCap.Round)
    blobOval(Offset(147f, 62f), 2.5f, 3f, Color(0xFFFFE066))
    blobRect(Offset(12f, 60f), Size(3f, 6f), Color(0xFFD7262E))
    for (w in SCAN_WHEELS) {
        blobCircle(w, 12.5f, Color(0xFF1B3F85))
        blobCircle(w, 11f, Color(0xFF26262A))
        drawCircle(STEEL, 4.5f, w)
    }
}

/** What the lens reveals: car outline, wiring harness and blinking modules. */
private fun DrawScope.drawScanCarXray(t: Float) {
    val line = Stroke(1.2f, join = StrokeJoin.Round)
    drawPath(scanCarBody(), XRAY_LINE, style = line)
    for (w in scanCarWindows()) drawPath(w, XRAY_LINE, alpha = 0.5f, style = line)
    for (w in SCAN_WHEELS) {
        drawCircle(XRAY_LINE, 11f, w, style = line)
        drawCircle(XRAY_LINE, 4.5f, w, alpha = 0.5f, style = line)
    }
    val harness = Path().apply {
        moveTo(SCAN_MODULES[0].x, SCAN_MODULES[0].y)
        for (i in 1 until SCAN_MODULES.size) lineTo(SCAN_MODULES[i].x, SCAN_MODULES[i].y)
    }
    drawPath(harness, Color(0xFFFFB74D), alpha = 0.7f, style = Stroke(1f))
    SCAN_MODULES.forEachIndexed { i, m ->
        drawRect(Color(0xFF16466E), Offset(m.x - 5f, m.y - 3.5f), Size(10f, 7f))
        drawRect(XRAY_LINE, Offset(m.x - 5f, m.y - 3.5f), Size(10f, 7f), style = Stroke(1f))
        val on = (t * 3f + i * 0.7f) % 2f < 1f
        drawCircle(if (on) Color(0xFF5CFF7A) else Color(0xFF1E5A2A), 1.4f, Offset(m.x + 2.5f, m.y - 1f))
    }
}

private fun DrawScope.drawScanScene(t: Float) {
    drawScanCar()

    // figure-eight path over the car
    val u = t / SCAN_PERIOD * 2 * PI_F
    val c = Offset(80f + 50f * sin(u), 58f + 12f * sin(2 * u))

    val lens = Path().apply { addOval(Rect(c, LENS_R)) }
    withTransform({
        clipPath(lens)
        scale(ZOOM, ZOOM, pivot = c)
    }) {
        drawRect(XRAY_BG, Offset(-100f, -100f), Size(SCENE_W + 200f, SCENE_H + 200f))
        drawScanCarXray(t)
    }
    drawArc(
        Color.White, 200f, 60f, false,
        Offset(c.x - LENS_R + 4f, c.y - LENS_R + 4f), Size((LENS_R - 4f) * 2, (LENS_R - 4f) * 2),
        alpha = 0.6f, style = Stroke(2.2f, cap = StrokeCap.Round)
    )
    // handle at 45 degrees down-right from the rim
    val d = 0.7071f
    limb(Offset(c.x + (LENS_R + 2f) * d, c.y + (LENS_R + 2f) * d), Offset(c.x + (LENS_R + 20f) * d, c.y + (LENS_R + 20f) * d), 6f, Color(0xFF3A2A1C))
    drawCircle(OUTLINE, LENS_R + 1.5f, c, style = Stroke(5.5f))
    drawCircle(Color(0xFF8A9099), LENS_R + 1.5f, c, style = Stroke(3f))
}

@Preview(showBackground = true, backgroundColor = 0xff5CA363)
@Composable
fun ButtonAnimationsPreview() {
    Column {
        DoctorAnimation(Modifier.height(100.dp))
        CarScanAnimation(Modifier.height(100.dp))
    }
}
