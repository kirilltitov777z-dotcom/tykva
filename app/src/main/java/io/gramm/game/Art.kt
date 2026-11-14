package io.gramm.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

data class Biome(
    val grass: Int,
    val grid: Int,
    val patch: Int,
    val treeDark: Int,
    val treeLight: Int,
    val rock: Int,
    val rockLight: Int,
    val border: Int
)

val BIOMES = listOf(
    Biome(0xFF5E8C3A.toInt(), 0xFF527C31.toInt(), 0xFF598538.toInt(), 0xFF2F5A2A.toInt(), 0xFF3F7135.toInt(), 0xFF8A8F84.toInt(), 0xFFA6AA9F.toInt(), 0xFF16200F.toInt()),
    Biome(0xFF46643A.toInt(), 0xFF3D5731.toInt(), 0xFF415F36.toInt(), 0xFF24422A.toInt(), 0xFF2F5436.toInt(), 0xFF707A6D.toInt(), 0xFF8B9487.toInt(), 0xFF11190F.toInt()),
    Biome(0xFF6F7446.toInt(), 0xFF62673D.toInt(), 0xFF696E42.toInt(), 0xFF3E5231.toInt(), 0xFF4C6339.toInt(), 0xFF8D8578.toInt(), 0xFFA9A193.toInt(), 0xFF1A1A10.toInt()),
    Biome(0xFF4B3A4E.toInt(), 0xFF403044.toInt(), 0xFF513E54.toInt(), 0xFF5A2A32.toInt(), 0xFF7A3A2A.toInt(), 0xFF6E6468.toInt(), 0xFF8A7E82.toInt(), 0xFF160D18.toInt()),
    Biome(0xFF9FB5A9.toInt(), 0xFF93A99D.toInt(), 0xFF9AB0A4.toInt(), 0xFF254632.toInt(), 0xFF315A3F.toInt(), 0xFFA7B1B2.toInt(), 0xFFC1CBCC.toInt(), 0xFF131B16.toInt())
)

fun darken(color: Int, f: Float): Int {
    val a = (color ushr 24) and 0xFF
    val r = ((color shr 16) and 0xFF) * f
    val g = ((color shr 8) and 0xFF) * f
    val b = (color and 0xFF) * f
    return (a shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or
        (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
}

fun lighten(color: Int, f: Float): Int {
    val a = (color ushr 24) and 0xFF
    val r = ((color shr 16) and 0xFF) * f
    val g = ((color shr 8) and 0xFF) * f
    val b = (color and 0xFF) * f
    return (a shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or
        (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
}

object Art {

    fun character(
        c: Canvas,
        skin: Skin,
        x: Float,
        y: Float,
        r: Float,
        angle: Float,
        weapon: Int,
        paint: Paint,
        weaponSkin: Int = 0
    ) {
        paint.style = Paint.Style.FILL
        paint.color = 0x33000000
        c.drawOval(x - r * 1.05f, y + r * 0.35f, x + r * 1.05f, y + r * 1.05f, paint)
        c.save()
        c.rotate(Math.toDegrees(angle.toDouble()).toFloat(), x, y)

        paint.color = skin.pants
        c.drawCircle(x - r * 0.30f, y + r * 0.48f, r * 0.27f, paint)
        c.drawCircle(x + r * 0.30f, y + r * 0.48f, r * 0.27f, paint)

        paint.color = skin.tone
        c.drawCircle(x + r * 0.50f, y - r * 0.10f, r * 0.23f, paint)
        c.drawCircle(x + r * 0.50f, y + r * 0.30f, r * 0.20f, paint)

        paint.color = skin.shirt
        c.drawRoundRect(x - r * 0.58f, y - r * 0.32f, x + r * 0.60f, y + r * 0.56f, r * 0.36f, r * 0.36f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.07f
        paint.color = darken(skin.shirt, 0.72f)
        c.drawRoundRect(x - r * 0.58f, y - r * 0.32f, x + r * 0.60f, y + r * 0.56f, r * 0.36f, r * 0.36f, paint)
        paint.style = Paint.Style.FILL

        val hx = x + r * 0.12f
        val hy = y - r * 0.30f
        val hr = r * 0.53f

        if (skin.hat == 7 || skin.hat == 11) {
            paint.color = if (skin.hat == 11) skin.hatColor else skin.hatColor
            c.drawCircle(hx - r * 0.03f, hy, hr * 1.22f, paint)
        }
        if (skin.hat == 0 || skin.hat == 4) {
            paint.color = skin.hair
            c.drawCircle(hx - r * 0.10f, hy - r * 0.02f, hr * 1.04f, paint)
        }

        paint.color = skin.tone
        c.drawCircle(hx, hy, hr, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.055f
        paint.color = darken(skin.tone, 0.7f)
        c.drawCircle(hx, hy, hr, paint)
        paint.style = Paint.Style.FILL

        paint.color = 0xFF2A211A.toInt()
        c.drawCircle(hx + hr * 0.42f, hy - hr * 0.28f, r * 0.055f, paint)
        c.drawCircle(hx + hr * 0.42f, hy + hr * 0.28f, r * 0.055f, paint)

        hat(c, skin, hx, hy, hr, r, paint)
        Art.weapon(c, weapon, x + r * 0.92f, y + r * 0.12f, r * 1.02f, paint, weaponSkin)

        c.restore()
    }

    private fun hat(c: Canvas, skin: Skin, hx: Float, hy: Float, hr: Float, r: Float, p: Paint) {
        val hc = skin.hatColor
        when (skin.hat) {
            0 -> {
                p.color = skin.hair
                c.drawArc(hx - hr, hy - hr * 1.02f, hx + hr, hy + hr * 0.8f, 155f, 230f, true, p)
            }
            1 -> {
                p.color = hc
                c.drawCircle(hx - hr * 0.08f, hy - hr * 0.10f, hr * 0.82f, p)
                c.drawRoundRect(hx + hr * 0.20f, hy - hr * 0.20f, hx + hr * 1.32f, hy + hr * 0.12f, hr * 0.14f, hr * 0.14f, p)
                p.color = darken(hc, 0.8f)
                c.drawRoundRect(hx + hr * 0.20f, hy + hr * 0.02f, hx + hr * 1.32f, hy + hr * 0.16f, hr * 0.08f, hr * 0.08f, p)
            }
            2 -> {
                p.color = hc
                c.drawCircle(hx, hy - hr * 0.02f, hr * 1.02f, p)
                p.color = darken(hc, 0.72f)
                c.drawRect(hx - hr, hy + hr * 0.18f, hx + hr, hy + hr * 0.38f, p)
                p.color = lighten(hc, 1.25f)
                c.drawCircle(hx - hr * 0.25f, hy - hr * 0.35f, hr * 0.22f, p)
            }
            3 -> {
                p.color = hc
                val path = Path()
                path.moveTo(hx - hr * 0.55f, hy)
                path.lineTo(hx - hr * 0.45f, hy - hr * 1.15f)
                path.lineTo(hx - hr * 0.15f, hy - hr * 0.55f)
                path.lineTo(hx, hy - hr * 1.35f)
                path.lineTo(hx + hr * 0.16f, hy - hr * 0.55f)
                path.lineTo(hx + hr * 0.45f, hy - hr * 1.1f)
                path.lineTo(hx + hr * 0.55f, hy)
                path.close()
                c.drawPath(path, p)
            }
            4 -> {
                p.color = hc
                c.drawArc(hx - hr * 1.02f, hy - hr * 1.25f, hx + hr * 1.02f, hy + hr * 0.55f, 180f, 180f, true, p)
                p.color = lighten(hc, 1.3f)
                c.drawCircle(hx, hy - hr * 1.2f, hr * 0.24f, p)
                p.color = skin.hair
                c.drawRoundRect(hx - hr * 0.55f, hy + hr * 0.30f, hx + hr * 0.65f, hy + hr * 1.10f, hr * 0.3f, hr * 0.3f, p)
                p.color = skin.tone
                c.drawRect(hx + hr * 0.05f, hy + hr * 0.42f, hx + hr * 0.55f, hy + hr * 0.62f, p)
            }
            5 -> {
                p.color = hc
                c.drawCircle(hx, hy, hr * 1.04f, p)
                p.color = 0xFFE9F1EA.toInt()
                c.drawCircle(hx + hr * 0.34f, hy - hr * 0.26f, r * 0.075f, p)
                c.drawCircle(hx + hr * 0.34f, hy + hr * 0.26f, r * 0.075f, p)
                p.color = 0xFF2A211A.toInt()
                c.drawCircle(hx + hr * 0.40f, hy - hr * 0.26f, r * 0.038f, p)
                c.drawCircle(hx + hr * 0.40f, hy + hr * 0.26f, r * 0.038f, p)
                p.color = skin.hatColor
                c.drawRect(hx - hr * 0.9f, hy - hr * 0.22f, hx - hr * 0.6f, hy + hr * 0.22f, p)
            }
            6 -> {
                p.color = hc
                val path = Path()
                path.moveTo(hx - hr * 0.85f, hy - hr * 0.45f)
                path.lineTo(hx - hr * 0.80f, hy - hr * 1.15f)
                path.lineTo(hx - hr * 0.35f, hy - hr * 0.70f)
                path.lineTo(hx, hy - hr * 1.35f)
                path.lineTo(hx + hr * 0.35f, hy - hr * 0.70f)
                path.lineTo(hx + hr * 0.80f, hy - hr * 1.15f)
                path.lineTo(hx + hr * 0.85f, hy - hr * 0.45f)
                path.close()
                c.drawPath(path, p)
                p.color = 0xFFE84D5B.toInt()
                c.drawCircle(hx, hy - hr * 0.66f, hr * 0.15f, p)
            }
            7 -> {
                p.color = 0x33000000
                c.drawArc(hx - hr * 0.9f, hy - hr * 0.9f, hx + hr * 0.9f, hy + hr * 0.9f, 120f, 90f, true, p)
            }
            8 -> {
                p.color = hc
                c.drawRoundRect(hx - hr * 0.95f, hy - hr * 0.72f, hx + hr * 0.95f, hy - hr * 0.12f, hr * 0.3f, hr * 0.3f, p)
                val path = Path()
                path.moveTo(hx - hr * 0.8f, hy - hr * 0.45f)
                path.lineTo(hx - hr * 1.5f, hy - hr * 0.70f)
                path.lineTo(hx - hr * 1.2f, hy - hr * 0.15f)
                path.close()
                c.drawPath(path, p)
            }
            9 -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = r * 0.09f
                p.color = 0xCCDFF5FF.toInt()
                c.drawCircle(hx, hy, hr * 1.28f, p)
                p.style = Paint.Style.FILL
                p.color = 0x551E4A63
                c.drawArc(hx - hr * 1.1f, hy - hr * 1.1f, hx + hr * 1.1f, hy + hr * 1.1f, -55f, 110f, true, p)
                p.color = 0xFFB9CAD8.toInt()
                c.drawCircle(hx - hr * 0.9f, hy - hr * 0.9f, hr * 0.16f, p)
            }
            10 -> {
                // тыква на голове
                p.color = darken(hc, 0.72f)
                c.drawCircle(hx, hy - hr * 0.10f, hr * 1.30f, p)
                p.color = hc
                c.drawCircle(hx, hy - hr * 0.10f, hr * 1.20f, p)
                p.color = lighten(hc, 1.22f)
                c.drawOval(hx - hr * 0.70f, hy - hr * 1.10f, hx + hr * 0.10f, hy + hr * 0.70f, p)
                p.color = darken(hc, 0.82f)
                c.drawOval(hx + hr * 0.10f, hy - hr * 1.12f, hx + hr * 0.78f, hy + hr * 0.70f, p)
                p.color = 0xFF2A1408.toInt()
                val e1 = Path()
                e1.moveTo(hx - hr * 0.62f, hy - hr * 0.30f)
                e1.lineTo(hx - hr * 0.22f, hy - hr * 0.30f)
                e1.lineTo(hx - hr * 0.42f, hy + hr * 0.05f)
                e1.close()
                c.drawPath(e1, p)
                val e2 = Path()
                e2.moveTo(hx + hr * 0.22f, hy - hr * 0.30f)
                e2.lineTo(hx + hr * 0.62f, hy - hr * 0.30f)
                e2.lineTo(hx + hr * 0.42f, hy + hr * 0.05f)
                e2.close()
                c.drawPath(e2, p)
                p.color = 0xFF4A7A28.toInt()
                c.drawRoundRect(hx - hr * 0.10f, hy - hr * 1.48f, hx + hr * 0.12f, hy - hr * 1.05f, hr * 0.08f, hr * 0.08f, p)
            }
            11 -> {
                // шляпа ведьмы
                p.color = hc
                c.drawOval(hx - hr * 1.45f, hy - hr * 0.55f, hx + hr * 1.45f, hy - hr * 0.05f, p)
                val path = Path()
                path.moveTo(hx - hr * 0.72f, hy - hr * 0.30f)
                path.lineTo(hx - hr * 0.18f, hy - hr * 2.05f)
                path.lineTo(hx + hr * 0.08f, hy - hr * 0.85f)
                path.lineTo(hx + hr * 0.72f, hy - hr * 0.30f)
                path.close()
                c.drawPath(path, p)
                p.color = 0xFFFF8A2A.toInt()
                c.drawRoundRect(hx - hr * 0.78f, hy - hr * 0.46f, hx + hr * 0.78f, hy - hr * 0.28f, hr * 0.08f, hr * 0.08f, p)
                p.color = 0xFFFFD95C.toInt()
                c.drawCircle(hx + hr * 0.10f, hy - hr * 0.90f, hr * 0.12f, p)
            }
        }
    }

    /** Оружие/инструмент в локальных координатах 100x100, «дулом» вправо. */
    fun weapon(c: Canvas, type: Int, cx: Float, cy: Float, size: Float, p: Paint, style: Int = 0) {
        val ws = WEAPON_SKINS[style.coerceIn(0, WEAPON_SKINS.size - 1)]
        val metal = ws.metal
        val wood = ws.wood
        val accent = ws.accent
        c.save()
        c.translate(cx, cy)
        c.scale(size / 100f, size / 100f)
        p.style = Paint.Style.FILL
        when (type) {
            0 -> {
                p.color = 0xFFE8B98A.toInt()
                c.drawRoundRect(8f, -20f, 48f, 20f, 16f, 16f, p)
                p.color = 0xFFD6A578.toInt()
                c.drawRoundRect(14f, -14f, 28f, 14f, 10f, 10f, p)
            }
            1 -> {
                c.rotate(-28f)
                p.color = wood
                c.drawRoundRect(-48f, -5f, 30f, 5f, 5f, 5f, p)
                p.color = metal
                val tip = Path()
                tip.moveTo(30f, -8f)
                tip.lineTo(58f, 0f)
                tip.lineTo(30f, 8f)
                tip.close()
                c.drawPath(tip, p)
                p.color = accent
                c.drawRect(24f, -7f, 30f, 7f, p)
            }
            2 -> {
                c.rotate(-32f)
                p.color = wood
                c.drawRoundRect(-42f, -6f, 32f, 6f, 6f, 6f, p)
                p.color = metal
                val path = Path()
                path.moveTo(24f, -8f)
                path.lineTo(50f, -26f)
                path.lineTo(44f, 0f)
                path.lineTo(50f, 26f)
                path.lineTo(24f, 8f)
                path.close()
                c.drawPath(path, p)
                p.color = accent
                c.drawRect(24f, -8f, 30f, 8f, p)
            }
            3 -> {
                c.rotate(-32f)
                p.color = wood
                c.drawRoundRect(-42f, -6f, 34f, 6f, 6f, 6f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 10f
                p.color = metal
                c.drawArc(-2f, -30f, 58f, 30f, 160f, 220f, false, p)
                p.style = Paint.Style.FILL
                p.color = accent
                c.drawCircle(44f, 0f, 4f, p)
            }
            4 -> {
                p.color = metal
                c.drawRoundRect(-40f, -11f, 16f, 11f, 4f, 4f, p)
                c.drawRect(16f, -6f, 50f, 2f, p)
                p.color = darken(metal, 0.75f)
                c.drawRoundRect(8f, 4f, 24f, 34f, 4f, 4f, p)
                c.drawRoundRect(-52f, -8f, -34f, 8f, 3f, 3f, p)
                p.color = 0xFF1C2024.toInt()
                c.drawCircle(-4f, 0f, 3.5f, p)
                p.color = wood
                c.drawRoundRect(-44f, 10f, -24f, 20f, 4f, 4f, p)
            }
            5 -> {
                p.color = metal
                c.drawRoundRect(-30f, -10f, 34f, 10f, 4f, 4f, p)
                c.drawRect(34f, -5f, 52f, 3f, p)
                p.color = darken(metal, 0.7f)
                c.drawCircle(-2f, 0f, 12f, p)
                p.color = accent
                c.drawCircle(-2f, 0f, 6f, p)
                p.color = wood
                val grip = Path()
                grip.moveTo(-18f, 8f)
                grip.lineTo(-4f, 8f)
                grip.lineTo(-12f, 34f)
                grip.lineTo(-26f, 30f)
                grip.close()
                c.drawPath(grip, p)
                p.color = 0xFF1C2024.toInt()
                c.drawCircle(4f, -2f, 2.5f, p)
            }
            6 -> {
                p.color = darken(metal, 0.7f)
                c.drawRoundRect(-48f, -12f, 38f, 12f, 8f, 8f, p)
                p.color = metal
                c.drawRect(38f, -16f, 50f, 16f, p)
                p.color = accent
                c.drawRect(-36f, -8f, -26f, 8f, p)
                p.color = 0xFF3A3324.toInt()
                c.drawRoundRect(-14f, 10f, 0f, 26f, 4f, 4f, p)
                c.drawRoundRect(6f, -18f, 20f, -10f, 3f, 3f, p)
            }
            7 -> {
                p.color = 0xFFB23A2A.toInt()
                c.drawRoundRect(-34f, -22f, -12f, 22f, 5f, 5f, p)
                c.drawRoundRect(-10f, -24f, 12f, 22f, 5f, 5f, p)
                c.drawRoundRect(14f, -21f, 34f, 22f, 5f, 5f, p)
                p.color = 0xFF7A241A.toInt()
                c.drawRect(-30f, -16f, 30f, -10f, p)
                p.color = accent
                c.drawRect(-4f, -12f, 4f, 12f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 4f
                p.color = 0xFFE4E05B.toInt()
                c.drawArc(-10f, -42f, 18f, -18f, 200f, 200f, false, p)
                p.style = Paint.Style.FILL
                p.color = 0xFFFFB347.toInt()
                c.drawCircle(6f, -38f, 4f, p)
            }
            8 -> {
                p.color = 0xFFF2F5F0.toInt()
                c.drawRoundRect(-30f, -24f, 30f, 24f, 8f, 8f, p)
                p.color = 0xFFD84444.toInt()
                c.drawRect(-6f, -16f, 6f, 16f, p)
                c.drawRect(-18f, -6f, 18f, 6f, p)
            }
        }
        c.restore()
    }

    fun pumpkin(c: Canvas, cx: Float, cy: Float, r: Float, p: Paint, face: Boolean = true) {
        p.style = Paint.Style.FILL
        p.color = 0x33000000
        c.drawOval(cx - r, cy + r * 0.45f, cx + r, cy + r * 1.25f, p)
        p.color = 0xFFB8401A.toInt()
        c.drawCircle(cx, cy, r, p)
        p.color = 0xFFFF7A1A.toInt()
        c.drawCircle(cx, cy, r * 0.94f, p)
        p.color = 0xFFD9541A.toInt()
        c.drawOval(cx - r * 0.62f, cy - r * 0.92f, cx + r * 0.10f, cy + r * 0.92f, p)
        c.drawOval(cx + r * 0.10f, cy - r * 0.92f, cx + r * 0.62f, cy + r * 0.92f, p)
        p.color = 0xFFFF9A3A.toInt()
        c.drawOval(cx - r * 0.40f, cy - r * 0.86f, cx + r * 0.22f, cy + r * 0.22f, p)
        p.color = 0xFF4A7A28.toInt()
        c.drawRoundRect(cx - r * 0.10f, cy - r * 1.24f, cx + r * 0.10f, cy - r * 0.88f, r * 0.08f, r * 0.08f, p)
        if (face) {
            p.color = 0xFF2A1408.toInt()
            val e1 = Path()
            e1.moveTo(cx - r * 0.55f, cy - r * 0.28f)
            e1.lineTo(cx - r * 0.18f, cy - r * 0.28f)
            e1.lineTo(cx - r * 0.36f, cy + r * 0.08f)
            e1.close()
            c.drawPath(e1, p)
            val e2 = Path()
            e2.moveTo(cx + r * 0.18f, cy - r * 0.28f)
            e2.lineTo(cx + r * 0.55f, cy - r * 0.28f)
            e2.lineTo(cx + r * 0.36f, cy + r * 0.08f)
            e2.close()
            c.drawPath(e2, p)
            val m = Path()
            m.moveTo(cx - r * 0.42f, cy + r * 0.36f)
            m.lineTo(cx - r * 0.22f, cy + r * 0.52f)
            m.lineTo(cx - r * 0.06f, cy + r * 0.38f)
            m.lineTo(cx + r * 0.10f, cy + r * 0.54f)
            m.lineTo(cx + r * 0.26f, cy + r * 0.40f)
            m.lineTo(cx + r * 0.42f, cy + r * 0.54f)
            m.lineTo(cx + r * 0.36f, cy + r * 0.68f)
            m.lineTo(cx - r * 0.36f, cy + r * 0.68f)
            m.close()
            c.drawPath(m, p)
        }
    }

    fun coin(c: Canvas, cx: Float, cy: Float, radius: Float, p: Paint) {
        val r = radius
        p.style = Paint.Style.FILL
        p.color = 0x33000000
        c.drawCircle(cx, cy + r * 0.13f, r * 0.98f, p)
        p.color = 0xFF9A5D05.toInt()
        c.drawCircle(cx, cy, r, p)
        p.color = 0xFFE0A31A.toInt()
        c.drawCircle(cx, cy, r * 0.94f, p)
        p.color = 0xFFF6C23C.toInt()
        c.drawCircle(cx, cy, r * 0.80f, p)
        p.color = 0xFFFFDC6B.toInt()
        c.drawCircle(cx, cy, r * 0.62f, p)
        p.color = 0xFFB87808.toInt()
        starPath(c, cx, cy, r * 0.48f, r * 0.21f, p)
        p.color = 0x88FFFFFF.toInt()
        c.drawCircle(cx - r * 0.30f, cy - r * 0.32f, r * 0.17f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.07f
        p.color = 0xFFC88911.toInt()
        c.drawCircle(cx, cy, r * 0.90f, p)
        p.style = Paint.Style.FILL
    }

    private fun starPath(c: Canvas, cx: Float, cy: Float, outer: Float, inner: Float, p: Paint) {
        val path = Path()
        for (i in 0 until 10) {
            val ang = -Math.PI / 2 + i * Math.PI / 5
            val rr = if (i % 2 == 0) outer else inner
            val px = cx + (cos(ang) * rr).toFloat()
            val py = cy + (sin(ang) * rr).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        c.drawPath(path, p)
    }

    fun woodIcon(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = 0xFF7A5230.toInt()
        c.drawRoundRect(cx - s * 0.9f, cy - s * 0.35f, cx + s * 0.9f, cy + s * 0.35f, s * 0.3f, s * 0.3f, p)
        p.color = 0xFFB98A55.toInt()
        c.drawOval(cx - s * 0.95f, cy - s * 0.32f, cx - s * 0.55f, cy + s * 0.32f, p)
        p.color = 0xFF8A6238.toInt()
        c.drawCircle(cx - s * 0.75f, cy, s * 0.12f, p)
    }

    fun stoneIcon(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = 0xFF8D9188.toInt()
        val path = Path()
        path.moveTo(cx - s * 0.8f, cy + s * 0.35f)
        path.lineTo(cx - s * 0.55f, cy - s * 0.35f)
        path.lineTo(cx + s * 0.1f, cy - s * 0.55f)
        path.lineTo(cx + s * 0.75f, cy - s * 0.1f)
        path.lineTo(cx + s * 0.6f, cy + s * 0.45f)
        path.close()
        c.drawPath(path, p)
        p.color = 0xFFB6BAAE.toInt()
        c.drawCircle(cx - s * 0.15f, cy - s * 0.15f, s * 0.22f, p)
    }

    fun ironIcon(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = 0xFF7E8A92.toInt()
        c.drawRoundRect(cx - s * 0.85f, cy - s * 0.40f, cx + s * 0.55f, cy + s * 0.40f, s * 0.18f, s * 0.18f, p)
        p.color = 0xFFC6D0D6.toInt()
        c.drawRoundRect(cx - s * 0.70f, cy - s * 0.28f, cx + s * 0.40f, cy - s * 0.02f, s * 0.1f, s * 0.1f, p)
        p.color = 0xFF5C666C.toInt()
        c.drawCircle(cx + s * 0.55f, cy, s * 0.22f, p)
    }

    fun sulfurIcon(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = 0xFFB7A92A.toInt()
        c.drawCircle(cx - s * 0.35f, cy + s * 0.15f, s * 0.48f, p)
        c.drawCircle(cx + s * 0.38f, cy + s * 0.05f, s * 0.40f, p)
        p.color = 0xFFE4E05B.toInt()
        c.drawCircle(cx - s * 0.12f, cy - s * 0.20f, s * 0.36f, p)
        p.color = 0xFFFFF7A8.toInt()
        c.drawCircle(cx - s * 0.22f, cy - s * 0.32f, s * 0.12f, p)
    }

    fun armorIcon(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = 0xFF8FB6D6.toInt()
        val path = Path()
        path.moveTo(cx, cy - s * 0.75f)
        path.lineTo(cx + s * 0.70f, cy - s * 0.45f)
        path.lineTo(cx + s * 0.60f, cy + s * 0.30f)
        path.lineTo(cx, cy + s * 0.85f)
        path.lineTo(cx - s * 0.60f, cy + s * 0.30f)
        path.lineTo(cx - s * 0.70f, cy - s * 0.45f)
        path.close()
        c.drawPath(path, p)
        p.color = 0xFFD6EAFA.toInt()
        c.drawCircle(cx, cy, s * 0.22f, p)
    }

    fun rocketIcon(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = 0xFF5C6B3E.toInt()
        c.drawRoundRect(cx - s * 0.7f, cy - s * 0.25f, cx + s * 0.55f, cy + s * 0.25f, s * 0.2f, s * 0.2f, p)
        p.color = 0xFFD84444.toInt()
        val path = Path()
        path.moveTo(cx + s * 0.55f, cy - s * 0.25f)
        path.lineTo(cx + s * 0.85f, cy)
        path.lineTo(cx + s * 0.55f, cy + s * 0.25f)
        path.close()
        c.drawPath(path, p)
    }
}
