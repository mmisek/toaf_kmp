package pl.misek.toafmobile.easteregg

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.time.TimeSource

/**
 * TOAF vs T4 easter egg. TOAF lands a jab, then a cross, and T4 goes down flat on his back.
 *
 * Pure Compose Canvas, no assets or dependencies, works in commonMain.
 *
 * @param loop replay forever instead of stopping on the last frame
 * @param logo optional real TOAF logo drawn on the chest; null draws a placeholder steering wheel
 * @param onFinished called once the animation ends (never when [loop] is true)
 */
@Composable
fun ToafBoxingEasterEgg(
    modifier: Modifier = Modifier,
    loop: Boolean = false,
    winTitle: String = "K.O.!",
    winSubtitle: String = "TOAF wygrywa",
    logo: Painter? = null,
    onFinished: () -> Unit = {},
) {
    val time = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(loop) {
        do {
            time.snapTo(0f)
            time.animateTo(
                targetValue = TOAF_BOXING_DURATION,
                animationSpec = tween((TOAF_BOXING_DURATION * 1000).toInt(), easing = LinearEasing),
            )
        } while (loop)
        onFinished()
    }

    Canvas(modifier.aspectRatio(SCENE_W / SCENE_H)) {
        val s = min(size.width / SCENE_W, size.height / SCENE_H)
        val ox = (size.width - SCENE_W * s) / 2f
        val oy = (size.height - SCENE_H * s) / 2f
        withTransform({
            translate(ox, oy)
            scale(s, s, pivot = Offset.Zero)
        }) {
            clipRect(0f, 0f, SCENE_W, SCENE_H) {
                drawBoxingScene(time.value, textMeasurer, logo, winTitle, winSubtitle)
            }
        }
    }
}

/** Ready-made dialog: plays once, tap anywhere to close. */
@Composable
fun ToafBoxingEasterEggDialog(onDismiss: () -> Unit, logo: Painter? = null) {
    Dialog(onDismissRequest = onDismiss) {
        ToafBoxingEasterEgg(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
            logo = logo,
        )
    }
}

/**
 * Hidden trigger, e.g. on the version label in About: [taps] quick taps fire [onTrigger].
 * Usage: Text("v1.4.2", Modifier.easterEggTrigger { showEgg = true })
 */
@Composable
fun Modifier.easterEggTrigger(taps: Int = 7, windowMs: Long = 2500, onTrigger: () -> Unit): Modifier {
    var count by remember { mutableIntStateOf(0) }
    var first by remember { mutableLongStateOf(0L) }
    val clock = remember { TimeSource.Monotonic.markNow() }
    return this.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
        val now = clock.elapsedNow().inWholeMilliseconds
        if (count == 0 || now - first > windowMs) { count = 0; first = now }
        count++
        if (count >= taps) { count = 0; onTrigger() }
    }
}

/* ======================= timeline ======================= */

const val TOAF_BOXING_DURATION = 6f
private const val SCENE_W = 520f
private const val SCENE_H = 300f
private const val GROUND = 262f
private const val HIT1 = 1.15f
private const val HIT2 = 2.32f
private const val LAND = 2.92f
private const val PI_F = PI.toFloat()

private const val G_FA1 = 1.1f
private const val G_FA2 = -1.25f
private const val G_BA1 = 1.25f
private const val G_BA2 = -1.45f
private const val UPPER = 34f
private const val FORE = 32f
private val HIP = Offset(0f, -85f)
private val F_SHOULDER = Offset(21f, -134f)
private val B_SHOULDER = Offset(-19f, -134f)

private fun clamp01(v: Float) = v.coerceIn(0f, 1f)
private fun smooth(v: Float): Float { val x = clamp01(v); return x * x * (3 - 2 * x) }
private fun seg(t: Float, a: Float, b: Float) = smooth((t - a) / (b - a))
private fun mix(a: Float, b: Float, k: Float) = a + (b - a) * k
private fun backOut(v: Float): Float { val x = clamp01(v) - 1f; val c = 1.7f; return 1 + (c + 1) * x.pow(3) + c * x.pow(2) }

private class Pose(var x: Float, var crouch: Float) {
    var y = 0f; var rot = 0f; var lean = 0.06f; var head = 0f
    var fa1 = G_FA1; var fa2 = G_FA2; var ba1 = G_BA1; var ba2 = G_BA2
    var backFront = false; var spread = 1f; var stars = 0f
}

private fun poses(t: Float): Pair<Pose, Pose> {
    val a = Pose(150f, 3f + sin(t * PI_F * 2 * 1.6f) * 3f)
    val b = Pose(285f, 3f + sin(t * PI_F * 2 * 1.6f + 1.3f) * 3f)

    // TOAF: jab
    if (t >= 1.0f && t < 1.5f) {
        val k = if (t < HIT1) seg(t, 1.0f, HIT1) else 1 - seg(t, 1.25f, 1.5f)
        a.fa1 = mix(G_FA1, -0.63f, k); a.fa2 = mix(G_FA2, -0.6f, k)
        a.lean = mix(0.06f, 0.18f, k); a.x += 18f * k
    }
    // TOAF: wind-up and cross
    if (t >= 1.95f && t < 2.2f) {
        val w = seg(t, 1.95f, 2.2f)
        a.lean = mix(0.06f, -0.04f, w); a.ba1 = mix(G_BA1, 1.6f, w); a.ba2 = mix(G_BA2, -1.9f, w); a.x -= 6f * w
    }
    if (t >= 2.2f && t < 2.95f) {
        val ext = t < HIT2
        val k = if (ext) seg(t, 2.2f, HIT2) else 1 - seg(t, 2.55f, 2.95f)
        val fLean = if (ext) -0.04f else 0.06f
        val fBa1 = if (ext) 1.6f else G_BA1
        val fBa2 = if (ext) -1.9f else G_BA2
        val fX = if (ext) -6f else 0f
        a.lean = mix(fLean, 0.38f, k); a.ba1 = mix(fBa1, -0.62f, k); a.ba2 = mix(fBa2, -0.6f, k)
        a.x += mix(fX, 34f, k); a.backFront = k > 0.15f
        a.fa1 = mix(G_FA1, 1.35f, k); a.fa2 = mix(G_FA2, -1.6f, k)
    }
    // TOAF: victory
    if (t >= 3.3f) {
        val v = seg(t, 3.3f, 3.6f)
        a.fa1 = mix(G_FA1, -1.45f, v); a.fa2 = mix(G_FA2, -1.75f, v)
        a.ba1 = mix(G_BA1, -1.75f, v); a.ba2 = mix(G_BA2, -1.45f, v)
        a.lean = mix(0.06f, 0f, v)
        if (t >= 3.6f) {
            val h = abs(sin((t - 3.6f) * PI_F * 1.6f))
            a.y = -h * 14f; a.crouch = (1 - h) * 6f
        }
    }

    // T4: hit by the jab
    if (t >= HIT1 && t < 1.75f) {
        val k = if (t < 1.23f) seg(t, HIT1, 1.23f) else 1 - seg(t, 1.23f, 1.75f)
        b.lean -= 0.32f * k; b.head = -0.45f * k; b.x += 14f * k; b.fa1 += 0.5f * k; b.ba1 += 0.3f * k
    }
    // T4: knocked flat
    if (t >= HIT2) {
        val f = clamp01((t - HIT2) / (LAND - HIT2))
        val g = f * f
        val e = smooth(f)
        b.rot = -PI_F / 2 * g
        b.x += 12f * f
        b.y = -20f * g
        b.crouch = mix(b.crouch, 0f, e)
        b.lean = -0.3f * sin(f * PI_F)
        b.head = -0.55f * sin(f * PI_F) + 0.15f * e
        b.fa1 = mix(G_FA1, -1.75f, e); b.fa2 = mix(G_FA2, -1.95f, e)
        b.ba1 = mix(G_BA1, -1.4f, e); b.ba2 = mix(G_BA2, -1.25f, e)
        b.spread = mix(1f, 0.35f, e)
        if (t >= LAND) {
            val bb = clamp01((t - LAND) / 0.28f)
            val bounce = sin(bb * PI_F) * (1 - bb * 0.3f)
            b.rot = -PI_F / 2 + 0.09f * bounce; b.y = -20f - 6f * bounce
            b.stars = seg(t, 3.05f, 3.3f)
        }
    }
    return a to b
}

private fun rotP(p: Offset, a: Float, c: Offset): Offset {
    val s = sin(a); val co = cos(a); val dx = p.x - c.x; val dy = p.y - c.y
    return Offset(c.x + dx * co - dy * s, c.y + dx * s + dy * co)
}

private class Arm(val s: Offset, val e: Offset, val g: Offset)

private fun armPoints(p: Pose, front: Boolean): Arm {
    val sh = if (front) F_SHOULDER else B_SHOULDER
    val a1 = if (front) p.fa1 else p.ba1
    val a2 = if (front) p.fa2 else p.ba2
    val hip = Offset(HIP.x, HIP.y + p.crouch)
    val s = rotP(Offset(sh.x, sh.y + p.crouch), p.lean, hip)
    val e = Offset(s.x + UPPER * cos(p.lean + a1), s.y + UPPER * sin(p.lean + a1))
    val g = Offset(e.x + FORE * cos(p.lean + a2), e.y + FORE * sin(p.lean + a2))
    return Arm(s, e, g)
}

private fun toWorld(p: Pose, facing: Float, local: Offset): Offset {
    val r = rotP(local, p.rot, Offset.Zero)
    return Offset(p.x + facing * r.x, GROUND + p.y + r.y)
}

/* ======================= look ======================= */

private val OUTLINE = Color(0xFF22180F)

private class Style(
    val facing: Float, val skin: Color, val skinHi: Color, val skinSh: Color,
    val shirt: Color, val shirtSh: Color, val shorts: Color, val trim: Color,
    val gloves: Color, val glovesHi: Color, val hair: Color, val shortHair: Boolean,
    val label: String, val labelColor: Color, val hasLogo: Boolean,
)

private val TOAF_STYLE = Style(
    facing = 1f, skin = Color(0xFFD9A066), skinHi = Color(0xFFF0C48E), skinSh = Color(0xFFA86E3C),
    shirt = Color(0xFF1E6B3C), shirtSh = Color(0xFF14502C), shorts = Color(0xFF1B1B1E), trim = Color(0xFFF2C230),
    gloves = Color(0xFFD7262E), glovesHi = Color(0xFFFF8A80), hair = Color(0xFF2B1A10), shortHair = true,
    label = "TOAF", labelColor = Color.White, hasLogo = true,
)
private val T4_STYLE = Style(
    facing = -1f, skin = Color(0xFFE0AE80), skinHi = Color(0xFFF6CFA6), skinSh = Color(0xFFB07A4E),
    shirt = Color(0xFFB8BEC6), shirtSh = Color(0xFF8E959E), shorts = Color(0xFF2F4E8C), trim = Color.White,
    gloves = Color(0xFF2E5FBF), glovesHi = Color(0xFF8FB4FF), hair = Color(0xFF6A6A6A), shortHair = false,
    label = "T4", labelColor = Color(0xFF24282E), hasLogo = false,
)

/* ======================= drawing ======================= */

private fun DrawScope.limb(a: Offset, b: Offset, w: Float, col: Color, hi: Color? = null) {
    drawLine(OUTLINE, a, b, w + 4f, StrokeCap.Round)
    drawLine(col, a, b, w, StrokeCap.Round)
    if (hi != null) {
        val dx = b.x - a.x; val dy = b.y - a.y; val len = hypot(dx, dy).coerceAtLeast(1f)
        var nx = -dy / len; var ny = dx / len
        if (ny > 0) { nx = -nx; ny = -ny }
        val o = w * 0.22f
        drawLine(
            hi,
            Offset(a.x + nx * o + dx * 0.15f, a.y + ny * o + dy * 0.15f),
            Offset(b.x + nx * o - dx * 0.15f, b.y + ny * o - dy * 0.15f),
            w * 0.28f, StrokeCap.Round,
        )
    }
}

private fun DrawScope.blob(path: Path, fill: Color) {
    drawPath(path, fill)
    drawPath(path, OUTLINE, style = Stroke(2.5f, join = StrokeJoin.Round))
}

private fun DrawScope.blobCircle(c: Offset, r: Float, fill: Color) {
    drawCircle(fill, r, c)
    drawCircle(OUTLINE, r, c, style = Stroke(2.5f))
}

private fun DrawScope.blobOval(c: Offset, rx: Float, ry: Float, fill: Color) {
    val tl = Offset(c.x - rx, c.y - ry); val sz = Size(rx * 2, ry * 2)
    drawOval(fill, tl, sz)
    drawOval(OUTLINE, tl, sz, style = Stroke(2.5f))
}

private fun DrawScope.drawArm(p: Pose, st: Style, front: Boolean) {
    val (s, e, g) = armPoints(p, front).let { Triple(it.s, it.e, it.g) }
    limb(s, e, 14f, st.skin, st.skinHi)
    limb(s, Offset(s.x + (e.x - s.x) * 0.32f, s.y + (e.y - s.y) * 0.32f), 17f, st.shirt)
    limb(e, g, 12f, st.skin, st.skinHi)
    val ga = atan2(g.y - e.y, g.x - e.x)
    val cuff = Offset(g.x - cos(ga) * 9f, g.y - sin(ga) * 9f)
    limb(cuff, Offset(cuff.x + cos(ga) * 2f, cuff.y + sin(ga) * 2f), 13f, Color(0xFFF4F4F4))
    blobCircle(g, 12.5f, st.gloves)
    drawArc(
        st.glovesHi, 180f, 126f, false,
        topLeft = Offset(g.x - 3f - 4.5f, g.y - 4f - 4.5f), size = Size(9f, 9f),
        style = Stroke(3f, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawLegs(p: Pose, st: Style) {
    val hipY = HIP.y + p.crouch
    val feet = listOf(Offset(-22f * p.spread - 2f, 0f), Offset(24f * p.spread, 0f))
    val hips = listOf(Offset(-10f, hipY), Offset(10f, hipY))
    val boot = Color(0xFF26262A)
    for (i in 0..1) {
        val h = hips[i]; val f = feet[i]; val side = if (i == 0) -1f else 1f
        val k = Offset((h.x + f.x) / 2 + side * (3f + p.crouch * 1.4f), (h.y + f.y) / 2)
        limb(h, k, 17f, st.skin, st.skinHi)
        limb(k, Offset(f.x, f.y - 10f), 14f, st.skin, st.skinHi)
        limb(Offset(f.x, f.y - 14f), Offset(f.x, f.y - 4f), 14f, boot)
        blobOval(Offset(f.x + 4f, f.y - 3f), 11f, 5f, boot)
    }
    val y = hipY
    val shorts = Path().apply {
        moveTo(-21f, y - 17f); lineTo(21f, y - 17f); lineTo(26f, y + 14f); lineTo(4f, y + 15f)
        lineTo(0f, y + 6f); lineTo(-4f, y + 15f); lineTo(-26f, y + 14f); close()
    }
    blob(shorts, st.shorts)
    drawRect(st.trim, Offset(-20f, y - 16f), Size(40f, 5f))
    drawRect(st.trim, Offset(21f, y - 10f), Size(3f, 22f))
}

private fun DrawScope.drawLogo(center: Offset, r: Float, col: Color, logo: Painter?) {
    if (logo != null) {
        translate(center.x - r * 1.3f, center.y - r * 1.3f) { with(logo) { draw(Size(r * 2.6f, r * 2.6f)) } }
        return
    }
    drawCircle(col, r, center, style = Stroke(2f))
    drawCircle(col, r * 0.28f, center)
    for (a in listOf(PI_F * 0.5f, PI_F * 1.17f, PI_F * 1.83f)) {
        drawLine(
            col,
            Offset(center.x + cos(a) * r * 0.28f, center.y + sin(a) * r * 0.28f),
            Offset(center.x + cos(a) * r, center.y + sin(a) * r),
            2f,
        )
    }
}

private fun DrawScope.centeredText(
    tm: TextMeasurer, text: String, center: Offset, sizePx: Float, color: Color, outline: Float = 0f,
) {
    val style = TextStyle(fontSize = (sizePx / (density * fontScale)).sp, fontWeight = FontWeight.Bold, color = color)
    val layout = tm.measure(text, style)
    val tl = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f)
    if (outline > 0f) drawText(layout, color = OUTLINE, topLeft = tl, drawStyle = Stroke(outline, join = StrokeJoin.Round))
    drawText(layout, topLeft = tl)
}

private fun DrawScope.drawTorso(p: Pose, st: Style, tm: TextMeasurer, logo: Painter?) {
    val c = p.crouch
    val shirt = Path().apply {
        moveTo(-18f, -97f + c); lineTo(18f, -97f + c); lineTo(22f, -118f + c)
        quadraticBezierTo(31f, -136f + c, 23f, -142f + c); lineTo(8f, -145f + c); lineTo(-8f, -145f + c)
        lineTo(-22f, -142f + c); quadraticBezierTo(-30f, -136f + c, -22f, -118f + c); close()
    }
    blob(shirt, st.shirt)
    val shade = Path().apply {
        moveTo(-18f, -97f + c); lineTo(-11f, -97f + c); lineTo(-15f, -141f + c); lineTo(-21f, -141f + c)
        quadraticBezierTo(-28f, -135f + c, -21f, -118f + c); close()
    }
    drawPath(shade, st.shirtSh)
    // label, un-mirrored so it always reads correctly
    withTransform({
        translate(1f, -117f + c)
        scale(st.facing, 1f, pivot = Offset.Zero)
    }) {
        if (st.hasLogo) {
            drawLogo(Offset(0f, -11f), 7.5f, st.labelColor, logo)
            centeredText(tm, st.label, Offset(0f, 8f), 13f, st.labelColor)
        } else {
            centeredText(tm, st.label, Offset(0f, 1f), 22f, st.labelColor)
        }
    }
    limb(Offset(1f, -143f + c), Offset(2f, -150f + c), 12f, st.skin)
}

private fun DrawScope.drawHead(p: Pose, st: Style) {
    val c = p.crouch
    val cx = 3f; val cy = -161f + c
    rotateRad(p.head, pivot = Offset(1f, -146f + c)) {
        blobCircle(Offset(cx, cy), 16.5f, st.skin)
        val nose = Path().apply {
            arcTo(Rect(Offset(cx + 15f, cy + 1f), 4f), -108f, 234f, true); close()
        }
        blob(nose, st.skin)
        drawArc(st.skinSh, 27f, 126f, false, Offset(cx - 1f, cy - 2f), Size(10f, 10f), style = Stroke(2f))
        blobOval(Offset(cx - 4f, cy + 1f), 3.5f, 5f, st.skinSh)
        drawCircle(OUTLINE, 1.9f, Offset(cx + 9f, cy - 3f))
        drawLine(OUTLINE, Offset(cx + 5f, cy - 8f), Offset(cx + 13f, cy - 7f), 2.2f, StrokeCap.Round)
        drawLine(OUTLINE, Offset(cx + 9f, cy + 8f), Offset(cx + 13f, cy + 7f), 1.8f, StrokeCap.Round)
        val hair = Path().apply {
            if (st.shortHair) {
                arcTo(Rect(Offset(cx, cy), 17.5f), 147.6f, 194.4f, true)
                quadraticBezierTo(cx + 6f, cy - 9f, cx - 2f, cy - 7f)
                quadraticBezierTo(cx - 6f, cy - 1f, cx - 9f, cy + 6f)
            } else {
                arcTo(Rect(Offset(cx, cy), 17f), 180f, 135f, true)
                quadraticBezierTo(cx + 2f, cy - 12f, cx - 8f, cy - 8f)
            }
            close()
        }
        blob(hair, st.hair)
    }
}

private fun DrawScope.drawFighter(p: Pose, st: Style, tm: TextMeasurer, logo: Painter?) {
    withTransform({
        translate(p.x, GROUND + p.y)
        scale(st.facing, 1f, pivot = Offset.Zero)
        rotateRad(p.rot, pivot = Offset.Zero)
    }) {
        drawLegs(p, st)
        if (!p.backFront) drawArm(p, st, front = false)
        rotateRad(p.lean, pivot = Offset(HIP.x, HIP.y + p.crouch)) {
            drawTorso(p, st, tm, logo)
            drawHead(p, st)
        }
        drawArm(p, st, front = true)
        if (p.backFront) drawArm(p, st, front = false)
    }
}

private fun DrawScope.drawBackground() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2B2F35), Color(0xFF4A4F57)), 0f, GROUND), size = Size(SCENE_W, SCENE_H))
    drawRect(
        Brush.radialGradient(listOf(Color(0x38FFF0C8), Color(0x00FFF0C8)), center = Offset(SCENE_W / 2, 70f), radius = 300f),
        size = Size(SCENE_W, SCENE_H),
    )
    val crowd = Color(0xFF23262B)
    for (i in 0 until 18) {
        val x = 10f + i * 30f + (i % 3) * 4f; val y = 150f + (i % 2) * 8f
        drawCircle(crowd, 11f, Offset(x, y))
        drawRect(crowd, Offset(x - 15f, y + 8f), Size(30f, 40f))
    }
    drawRect(Color(0xFF8E2A2A), Offset(0f, GROUND + 14f), Size(SCENE_W, SCENE_H - GROUND))
    drawPath(Path().apply {
        moveTo(30f, 200f); lineTo(SCENE_W - 30f, 200f); lineTo(SCENE_W, GROUND + 14f); lineTo(0f, GROUND + 14f); close()
    }, Color(0xFFC9CCD1))
    drawRect(Color(0xFFB4B8BE), Offset(0f, GROUND + 10f), Size(SCENE_W, 4f))
    for (x in listOf(30f, SCENE_W - 30f)) {
        drawRect(Color(0xFFD8D8D8), Offset(x - 5f, 108f), Size(10f, 94f))
        drawRect(OUTLINE, Offset(x - 5f, 108f), Size(10f, 94f), style = Stroke(2f))
    }
    listOf(Color(0xFFD7262E), Color(0xFFF4F4F4), Color(0xFF2E5FBF)).forEachIndexed { i, col ->
        val y = 120f + i * 24f
        drawLine(OUTLINE, Offset(30f, y), Offset(SCENE_W - 30f, y), 6f, StrokeCap.Round)
        drawLine(col, Offset(30f, y), Offset(SCENE_W - 30f, y), 3.5f, StrokeCap.Round)
    }
}

private fun DrawScope.shadow(x: Float, w: Float) {
    drawOval(Color(0x47000000), Offset(x - w, GROUND + 2f - 7f), Size(w * 2, 14f))
}

private fun DrawScope.burst(at: Offset, k: Float, col: Color) {
    if (k <= 0f) return
    val sc = 0.6f + k * 0.8f
    val path = Path().apply {
        for (i in 0 until 16) {
            val r = if (i % 2 == 1) 9f else 22f
            val a = i / 16f * PI_F * 2
            val px = cos(a) * r; val py = sin(a) * r
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
    val alpha = min(1f, 1.6f - k).coerceIn(0f, 1f)
    withTransform({ translate(at.x, at.y); scale(sc, sc, pivot = Offset.Zero) }) {
        drawPath(path, col, alpha = alpha)
        drawPath(path, OUTLINE, alpha = alpha, style = Stroke(2f))
    }
}

private fun DrawScope.star(c: Offset, r: Float, alpha: Float) {
    val path = Path().apply {
        for (i in 0 until 10) {
            val rr = if (i % 2 == 1) r * 0.45f else r
            val a = -PI_F / 2 + i / 10f * PI_F * 2
            val px = c.x + cos(a) * rr; val py = c.y + sin(a) * rr
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
    drawPath(path, Color(0xFFFFD84A), alpha = alpha)
    drawPath(path, OUTLINE, alpha = alpha, style = Stroke(1.5f))
}

private fun DrawScope.popText(tm: TextMeasurer, text: String, center: Offset, sizePx: Float, fill: Color, k: Float) {
    withTransform({ translate(center.x, center.y); scale(k, k, pivot = Offset.Zero) }) {
        centeredText(tm, text, Offset.Zero, sizePx, fill, outline = sizePx * 0.18f)
    }
}

private fun DrawScope.drawBoxingScene(t: Float, tm: TextMeasurer, logo: Painter?, winTitle: String, winSubtitle: String) {
    val (a, b) = poses(t)

    var sh = 0f
    if (t >= HIT2 && t < HIT2 + 0.2f) sh = 6f * (1 - (t - HIT2) / 0.2f)
    if (t >= LAND && t < LAND + 0.15f) sh = maxOf(sh, 4f * (1 - (t - LAND) / 0.15f))
    if (t >= HIT1 && t < HIT1 + 0.1f) sh = maxOf(sh, 2.5f)

    translate(sin(t * 97f) * sh, cos(t * 83f) * sh * 0.6f) {
        drawBackground()
        shadow(a.x + 2f, 34f)
        val down = clamp01(-b.rot / (PI_F / 2))
        shadow(b.x + 80f * down, mix(34f, 95f, down))
        drawFighter(b, T4_STYLE, tm, null)
        drawFighter(a, TOAF_STYLE, tm, logo)

        if (t >= HIT1 && t < HIT1 + 0.18f) {
            val g = toWorld(a, 1f, armPoints(a, true).g)
            burst(Offset(g.x + 8f, g.y), (t - HIT1) / 0.18f, Color(0xFFFFF3B0))
        }
        if (t >= HIT2 && t < HIT2 + 0.25f) {
            val g = toWorld(a, 1f, armPoints(a, false).g)
            burst(Offset(g.x + 10f, g.y), (t - HIT2) / 0.25f, Color(0xFFFFE066))
        }
        if (b.stars > 0f) {
            val h = toWorld(b, -1f, Offset(3f, -161f))
            for (i in 0 until 3) {
                val ang = t * 4f + i * PI_F * 2 / 3
                star(Offset(h.x + cos(ang) * 22f, h.y - 26f + sin(ang) * 7f), 6f, b.stars)
            }
        }
    }

    if (t >= 3.5f) {
        popText(tm, winTitle, Offset(345f, 58f), 52f, Color(0xFFFFD84A), backOut((t - 3.5f) / 0.35f))
        if (t >= 3.8f) popText(tm, winSubtitle, Offset(345f, 100f), 26f, Color.White, backOut((t - 3.8f) / 0.3f))
    }
}
