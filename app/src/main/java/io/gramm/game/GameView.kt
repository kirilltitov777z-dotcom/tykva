package io.gramm.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class GameView(context: Context, private val store: Store) : View(context) {

    companion object {
        private const val BG = 0xFF0B1410.toInt()
        private const val PANEL = 0xFF14241B.toInt()
        private const val PANEL2 = 0xFF1B3123.toInt()
        private const val STROKE = 0xFF2E4C36.toInt()
        private const val ACCENT = 0xFFC8FF4D.toInt()
        private const val GOLD = 0xFFFFD95C.toInt()
        private const val TEXT = 0xFFF0F8E9.toInt()
        private const val DIM = 0xFF91A894.toInt()
        private const val RED = 0xFFFF5D5D.toInt()
        private const val GREEN = 0xFF7BE06B.toInt()
    }

    private enum class Scr { MENU, SERVERS, SKINS, GAME }

    private class Btn(val id: String, val r: RectF)

    private class Feed(val text: String, val color: Int, val at: Long)

    private var screen = Scr.MENU
    private var world: World? = null
    private var server = 0
    private var paused = false
    private var dead = false
    private var deathKiller = ""
    private var firedFlag = false
    private var stateTimer = 0f
    private var time = 0f
    private var lastFrame = 0L

    private val net = Net { s, j -> post { onNet(s, j) } }
    private val presence = HashMap<String, Long>()
    private val feed = ArrayList<Feed>()

    private var menuButtons = ArrayList<Btn>()
    private var hudButtons = ArrayList<Btn>()
    private var slotRects = ArrayList<RectF>()

    private var leftPointer = -1
    private var rightPointer = -1
    private var moveX = 0f
    private var moveY = 0f
    private var aimAngle = 0f
    private var firing = false
    private var leftKnob = PointF()
    private var rightKnob = PointF()

    private var lsX = 0f
    private var lsY = 0f
    private var rsX = 0f
    private var rsY = 0f
    private var stickR = 0f
    private var hpY = 0f

    private var downX = 0f
    private var downY = 0f
    private var tapMoved = false

    private var toastMsg = ""
    private var toastAt = 0L

    private var bgShader: LinearGradient? = null
    private var insTop = 0f

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pb = Paint(Paint.ANTI_ALIAS_FLAG)

    var onLogout: (() -> Unit)? = null

    init {
        isFocusable = true
        p.style = Paint.Style.FILL
        pt.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        pb.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        pt.textAlign = Paint.Align.LEFT
        pb.textAlign = Paint.Align.LEFT
        setOnApplyWindowInsetsListener { _, insets ->
            insTop = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.displayCutout()).top.toFloat()
            } else 0f
            invalidate()
            insets
        }
        net.start()
    }

    fun destroy() {
        net.stop()
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bgShader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0xFF0A1511.toInt(), 0xFF10271B.toInt(), 0xFF08110D.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    // ------------------------------------------------------------- lifecycle

    override fun onDraw(canvas: Canvas) {
        currentCanvas = canvas
        val now = System.nanoTime()
        val dt = if (lastFrame == 0L) 0.016f else ((now - lastFrame) / 1_000_000_000.0).toFloat()
        lastFrame = now
        val d = dt.coerceIn(0f, 0.05f)
        time += d

        when (screen) {
            Scr.MENU -> {
                drawBackdrop(canvas)
                drawMenu(canvas)
            }
            Scr.SERVERS -> {
                drawBackdrop(canvas)
                drawServers(canvas)
            }
            Scr.SKINS -> {
                drawBackdrop(canvas)
                drawSkins(canvas)
            }
            Scr.GAME -> {
                drawGame(canvas, d)
            }
        }
        postInvalidateOnAnimation()
    }

    fun handleBack(): Boolean {
        return when (screen) {
            Scr.MENU -> false
            Scr.SERVERS, Scr.SKINS -> {
                screen = Scr.MENU
                true
            }
            Scr.GAME -> {
                if (dead) exitGame() else paused = !paused
                true
            }
        }
    }

    // ------------------------------------------------------------------- сеть

    private fun onNet(s: Int, j: JSONObject) {
        val type = j.optString("t")
        val id = j.optString("id")
        if (id.isNotEmpty()) presence["$s:$id"] = System.currentTimeMillis()

        if (screen != Scr.GAME || s != server) return
        val w = world ?: return

        when (type) {
            "p" -> {
                if (id == w.me.id || id.isEmpty()) return
                val n = j.optString("n")
                if (n.isEmpty()) return
                w.updateRemote(
                    id, n, j.optInt("k"),
                    j.optDouble("x", 0.0).toFloat(),
                    j.optDouble("y", 0.0).toFloat(),
                    j.optDouble("h", 100.0).toFloat(),
                    j.optDouble("a", 0.0).toFloat(),
                    j.optInt("w", 3), j.optInt("kl", 0), j.optInt("c", 0)
                )
                if (j.optInt("f", 0) == 1) {
                    w.players[id]?.let { w.remoteShotFx(it) }
                }
            }
            "dmg" -> {
                if (j.optString("to") == w.me.id && w.me.alive) {
                    remoteHurt(
                        j.optDouble("v", 0.0).toFloat(),
                        j.optString("bid"),
                        j.optString("by")
                    )
                }
            }
            "obj" -> w.removeObject(j.optInt("id", -1))
            "build" -> w.addRemoteWall(
                j.optInt("id", -1),
                j.optDouble("x", 0.0).toFloat(),
                j.optDouble("y", 0.0).toFloat(),
                j.optDouble("h", 240.0).toFloat()
            )
            "kill" -> {
                if (j.optString("sid") == w.me.id) return
                val bid = j.optString("bid")
                val by = j.optString("by")
                val vic = j.optString("vic")
                if (by.isNotEmpty() && vic.isNotEmpty()) {
                    feedAdd("$by убил $vic", RED)
                }
                if (bid == w.me.id) {
                    w.me.kills++
                    w.me.coins += 40
                    store.addCoins(40)
                    store.addKill(server)
                }
            }
        }
    }

    private fun remoteHurt(v: Float, byId: String, byName: String) {
        val w = world ?: return
        if (!w.me.alive) return
        w.me.hp -= v
        w.me.hurt = 0.25f
        w.me.flash = 0.2f
        if (byId.isNotEmpty()) {
            w.me.attackerId = byId
            w.me.attackerName = byName
        }
        if (w.me.hp <= 0f) {
            w.me.hp = 0f
            w.me.alive = false
            w.me.deadT = 0f
            die(byName.ifEmpty { "лес" }, byId)
        }
    }

    private fun die(killerName: String, killerId: String) {
        val w = world ?: return
        dead = true
        paused = false
        firing = false
        deathKiller = killerName.ifEmpty { "лес" }
        feedAdd("Тебя убил $deathKiller", RED)
        val j = JSONObject()
            .put("t", "kill")
            .put("by", deathKiller)
            .put("bid", killerId)
            .put("sid", w.me.id)
            .put("vic", w.me.name)
            .put("vid", w.me.id)
        net.send(server, j)
        sendState()
    }

    private inner class Ev : World.Events {
        override fun remoteDamage(targetId: String, dmg: Float, byName: String) {
            val j = JSONObject()
                .put("t", "dmg")
                .put("to", targetId)
                .put("v", dmg.toDouble())
                .put("by", byName)
                .put("bid", world?.me?.id ?: "")
            net.send(server, j)
        }

        override fun objectGone(id: Int) {
            net.send(server, JSONObject().put("t", "obj").put("id", id))
        }

        override fun wallBuilt(e: Ent) {
            val j = JSONObject()
                .put("t", "build")
                .put("id", e.id)
                .put("x", e.x.toDouble())
                .put("y", e.y.toDouble())
                .put("h", e.hp.toDouble())
            net.send(server, j)
        }

        override fun playerDied(victim: P, killer: P?, killerId: String, killerName: String) {
            if (victim.isMe) {
                die(killerName, killerId)
                return
            }
            if (killer?.isMe == true) {
                rewardKill(victim.name, true)
            } else {
                feedAdd("${killer?.name ?: killerName} убил ${victim.name}", RED)
            }
        }

        override fun shot(owner: P) {
            if (owner.isMe) firedFlag = true
        }

        override fun coinsEarned(p: P, n: Int) {
            if (p.isMe) store.addCoins(n)
        }
    }

    private fun rewardKill(victimName: String, withFeed: Boolean) {
        val w = world ?: return
        w.me.kills++
        w.me.coins += 40
        store.addCoins(40)
        store.addKill(server)
        if (withFeed) feedAdd("Ты убил $victimName", ACCENT)
    }

    private fun feedAdd(text: String, color: Int) {
        feed.add(Feed(text, color, System.currentTimeMillis()))
        if (feed.size > 6) feed.removeAt(0)
    }

    private fun sendState() {
        val w = world ?: return
        val m = w.me
        val j = JSONObject()
            .put("t", "p")
            .put("id", m.id)
            .put("n", m.name)
            .put("k", m.skin)
            .put("x", m.x.toDouble())
            .put("y", m.y.toDouble())
            .put("h", m.hp.toDouble())
            .put("a", m.aim.toDouble())
            .put("w", m.weapon)
            .put("kl", m.kills)
            .put("c", m.coins)
            .put("f", if (firedFlag && m.alive) 1 else 0)
        firedFlag = false
        net.send(server, j)
    }

    private fun online(s: Int): Int {
        val now = System.currentTimeMillis()
        var n = 0
        val it = presence.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (now - e.value > 20_000L) {
                it.remove()
            } else if (e.key.startsWith("$s:")) {
                n++
            }
        }
        return n
    }

    // ------------------------------------------------------------------- игра

    private fun play(s: Int) {
        server = s
        store.resetIfWiped(s, System.currentTimeMillis())
        val w = World(s, Ev(), store.user ?: "Игрок", store.selectedSkin)
        w.me.coins = store.coins
        world = w
        screen = Scr.GAME
        paused = false
        dead = false
        deathKiller = ""
        firedFlag = false
        stateTimer = 10f
        leftPointer = -1
        rightPointer = -1
        moveX = 0f
        moveY = 0f
        firing = false
        aimAngle = 0f
        feed.clear()
        sendState()
    }

    private fun exitGame() {
        screen = Scr.MENU
        world = null
        paused = false
        dead = false
        leftPointer = -1
        rightPointer = -1
        moveX = 0f
        moveY = 0f
        firing = false
    }

    private fun drawGame(c: Canvas, dt: Float) {
        val w = world ?: run {
            screen = Scr.MENU
            invalidate()
            return
        }
        val widthF = width.toFloat()
        val heightF = height.toFloat()
        val biome = BIOMES[server % BIOMES.size]

        if (w.me.alive) {
            aimAngle = if (rightPointer >= 0) aimAngle else if (moveX != 0f || moveY != 0f) atan2(moveY, moveX) else aimAngle
            w.me.aim = aimAngle
            w.me.weapon = w.me.weapon.coerceIn(0, 5)
        }
        if (!paused) {
            w.update(dt, if (w.me.alive) moveX else 0f, if (w.me.alive) moveY else 0f, firing && w.me.alive)
            stateTimer += dt
            if (stateTimer > 0.12f) {
                stateTimer = 0f
                sendState()
            }
            if (dead && w.me.deadT == 0f && w.me.hp <= 0f) {
                // смерть уже обработана
            }
        }

        val scale = min(widthF, heightF) / 760f
        val shx = sin(time * 61f) * w.shake * 0.4f
        val shy = cos(time * 53f) * w.shake * 0.4f

        c.drawColor(biome.grass)
        c.save()
        c.translate(widthF / 2f + shx, heightF / 2f + shy)
        c.scale(scale, scale)
        c.translate(-w.me.x, -w.me.y)
        drawGround(c, biome, w.me.x, w.me.y, widthF, heightF, scale)
        drawObjects(c, w, biome, widthF, heightF, scale)
        drawBullets(c, w)
        drawParticles(c, w)
        drawPlayers(c, w)
        drawTexts(c, w)
        c.restore()

        drawHud(c, w)
    }

    private fun drawGround(c: Canvas, b: Biome, camX: Float, camY: Float, sw: Float, sh: Float, scale: Float) {
        val vw = sw / scale
        val vh = sh / scale
        val left = camX - vw / 2f - 80f
        val top = camY - vh / 2f - 80f
        val right = camX + vw / 2f + 80f
        val bottom = camY + vh / 2f + 80f

        p.style = Paint.Style.FILL
        p.color = b.grass
        c.drawRect(left, top, right, bottom, p)

        var gx = (Math.floor((left / 200f).toDouble()) * 200f).toFloat()
        while (gx < right) {
            var gy = (Math.floor((top / 200f).toDouble()) * 200f).toFloat()
            while (gy < bottom) {
                val h = ((gx / 200f).toInt() * 73856093) xor ((gy / 200f).toInt() * 19349663) xor (server * 83492791)
                if (abs(h) % 9 == 2) {
                    p.color = b.patch
                    c.drawRect(gx, gy, gx + 200f, gy + 200f, p)
                }
                gy += 200f
            }
            gx += 200f
        }

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.6f
        p.color = b.grid
        var x = (Math.floor((left / 100f).toDouble()) * 100f).toFloat()
        while (x < right) {
            c.drawLine(x, top, x, bottom, p)
            x += 100f
        }
        var y = (Math.floor((top / 100f).toDouble()) * 100f).toFloat()
        while (y < bottom) {
            c.drawLine(left, y, right, y, p)
            y += 100f
        }
        p.style = Paint.Style.FILL

        p.color = b.border
        c.drawRect(-600f, -600f, World.W + 600f, 0f, p)
        c.drawRect(-600f, World.H, World.W + 600f, World.H + 600f, p)
        c.drawRect(-600f, 0f, 0f, World.H, p)
        c.drawRect(World.W, 0f, World.W + 600f, World.H, p)
    }

    private fun drawObjects(c: Canvas, w: World, b: Biome, sw: Float, sh: Float, scale: Float) {
        val rad = (sw * sw + sh * sh) / (4f * scale * scale) + 150f
        for (e in w.objects) {
            if (e.dead) continue
            val dx = e.x - w.me.x
            val dy = e.y - w.me.y
            if (dx * dx + dy * dy > rad * rad) continue
            drawEntity(c, e, b, w)
        }
    }

    private fun drawEntity(c: Canvas, e: Ent, b: Biome, w: World) {
        p.style = Paint.Style.FILL
        when (e.type) {
            World.T_TREE -> {
                p.color = 0x33000000
                c.drawOval(e.x - e.r, e.y + e.r * 0.2f, e.x + e.r, e.y + e.r * 1.0f, p)
                p.color = 0xFF6B4A2A.toInt()
                c.drawRoundRect(e.x - e.r * 0.2f, e.y, e.x + e.r * 0.2f, e.y + e.r * 0.65f, 4f, 4f, p)
                p.color = b.treeDark
                c.drawCircle(e.x - e.r * 0.32f, e.y - e.r * 0.18f, e.r * 0.78f, p)
                c.drawCircle(e.x + e.r * 0.34f, e.y - e.r * 0.05f, e.r * 0.70f, p)
                p.color = b.treeLight
                c.drawCircle(e.x, e.y - e.r * 0.48f, e.r * 0.68f, p)
                c.drawCircle(e.x - e.r * 0.35f, e.y - e.r * 0.05f, e.r * 0.34f, p)
                if (e.flash > 0f) {
                    p.color = (0x66FFFFFF * e.flash / 0.12f).toInt()
                    c.drawCircle(e.x, e.y - e.r * 0.2f, e.r, p)
                }
            }
            World.T_ROCK -> {
                p.color = 0x33000000
                c.drawOval(e.x - e.r, e.y + e.r * 0.15f, e.x + e.r, e.y + e.r * 0.9f, p)
                p.color = b.rock
                c.drawCircle(e.x, e.y, e.r, p)
                p.color = b.rockLight
                c.drawCircle(e.x - e.r * 0.25f, e.y - e.r * 0.28f, e.r * 0.55f, p)
                c.drawCircle(e.x + e.r * 0.35f, e.y + e.r * 0.2f, e.r * 0.34f, p)
                if (e.flash > 0f) {
                    p.color = 0x88FFFFFF.toInt()
                    c.drawCircle(e.x, e.y, e.r, p)
                }
            }
            World.T_BUSH -> {
                p.color = 0x33000000
                c.drawOval(e.x - e.r, e.y + e.r * 0.2f, e.x + e.r, e.y + e.r * 0.8f, p)
                p.color = darken(b.treeLight, 0.8f)
                c.drawCircle(e.x - e.r * 0.35f, e.y, e.r * 0.75f, p)
                c.drawCircle(e.x + e.r * 0.35f, e.y + e.r * 0.08f, e.r * 0.68f, p)
                p.color = b.treeLight
                c.drawCircle(e.x, e.y - e.r * 0.26f, e.r * 0.6f, p)
            }
            World.T_CRATE -> {
                p.color = 0x33000000
                c.drawRoundRect(e.x - e.r * 1.1f, e.y - e.r * 0.7f, e.x + e.r * 1.25f, e.y + e.r * 1.25f, 6f, 6f, p)
                p.color = 0xFF9C6A35.toInt()
                c.drawRoundRect(e.x - e.r, e.y - e.r, e.x + e.r, e.y + e.r, 6f, 6f, p)
                p.color = 0xFFB98A55.toInt()
                c.drawRoundRect(e.x - e.r * 0.8f, e.y - e.r * 0.8f, e.x + e.r * 0.8f, e.y + e.r * 0.8f, 4f, 4f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 5f
                p.color = 0xFF7A4E23.toInt()
                c.drawLine(e.x - e.r * 0.8f, e.y - e.r * 0.8f, e.x + e.r * 0.8f, e.y + e.r * 0.8f, p)
                c.drawLine(e.x + e.r * 0.8f, e.y - e.r * 0.8f, e.x - e.r * 0.8f, e.y + e.r * 0.8f, p)
                p.style = Paint.Style.FILL
                if (e.flash > 0f) {
                    p.color = 0x66FFFFFF
                    c.drawRoundRect(e.x - e.r, e.y - e.r, e.x + e.r, e.y + e.r, 6f, 6f, p)
                }
            }
            World.T_BARREL -> {
                p.color = 0x33000000
                c.drawCircle(e.x, e.y + 6f, e.r, p)
                p.color = 0xFFA33A2E.toInt()
                c.drawCircle(e.x, e.y, e.r, p)
                p.color = 0xFFC4553F.toInt()
                c.drawCircle(e.x, e.y, e.r * 0.82f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3f
                p.color = 0xFF76261E.toInt()
                c.drawCircle(e.x, e.y, e.r * 0.62f, p)
                p.style = Paint.Style.FILL
                p.color = 0xFFFFD95C.toInt()
                c.drawCircle(e.x, e.y, e.r * 0.13f, p)
            }
            World.T_WALL -> {
                p.color = 0x33000000
                c.drawRoundRect(e.x - e.r, e.y - e.r + 5f, e.x + e.r, e.y + e.r + 5f, 7f, 7f, p)
                p.color = 0xFF8E6335.toInt()
                c.drawRoundRect(e.x - e.r, e.y - e.r, e.x + e.r, e.y + e.r, 7f, 7f, p)
                p.color = 0xFFA97A45.toInt()
                c.drawRoundRect(e.x - e.r * 0.85f, e.y - e.r * 0.85f, e.x + e.r * 0.85f, e.y + e.r * 0.85f, 5f, 5f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3f
                p.color = 0xFF75502A.toInt()
                c.drawLine(e.x - e.r * 0.8f, e.y - e.r * 0.2f, e.x + e.r * 0.8f, e.y - e.r * 0.2f, p)
                c.drawLine(e.x - e.r * 0.8f, e.y + e.r * 0.35f, e.x + e.r * 0.8f, e.y + e.r * 0.35f, p)
                p.style = Paint.Style.FILL
                if (e.hp < e.maxHp) bar(c, e.x - e.r * 0.8f, e.y - e.r - 13f, 52f, e.hp / e.maxHp)
            }
            World.T_BENCH -> {
                p.color = 0x33000000
                c.drawRoundRect(e.x - e.r, e.y - e.r + 5f, e.x + e.r, e.y + e.r + 5f, 6f, 6f, p)
                p.color = 0xFF6E4C2B.toInt()
                c.drawRoundRect(e.x - e.r * 0.9f, e.y - e.r * 0.6f, e.x + e.r * 0.9f, e.y + e.r * 0.7f, 6f, 6f, p)
                p.color = 0xFFB98A55.toInt()
                c.drawRoundRect(e.x - e.r, e.y - e.r * 0.8f, e.x + e.r, e.y + e.r * 0.25f, 6f, 6f, p)
                p.color = 0xFF8D979C.toInt()
                c.drawRoundRect(e.x - e.r * 0.55f, e.y - e.r * 0.55f, e.x + e.r * 0.05f, e.y - e.r * 0.2f, 3f, 3f, p)
                p.color = 0xFF6B4A2A.toInt()
                c.drawRect(e.x - e.r * 0.05f, e.y - e.r * 0.5f, e.x + e.r * 0.1f, e.y - e.r * 0.05f, p)
                if (e.hp < e.maxHp) bar(c, e.x - e.r * 0.8f, e.y - e.r - 13f, 52f, e.hp / e.maxHp)
                if (w.nearestBench() === e) {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 2f
                    p.color = 0x88C8FF4D.toInt()
                    c.drawCircle(e.x, e.y, e.r + 10f + sin(time * 3f) * 3f, p)
                    p.style = Paint.Style.FILL
                }
            }
        }
    }

    private fun bar(c: Canvas, x: Float, y: Float, width: Float, frac: Float) {
        p.color = 0x99000000.toInt()
        c.drawRoundRect(x, y, x + width, y + 8f, 4f, 4f, p)
        p.color = if (frac > 0.5f) 0xFF6FCF5A.toInt() else 0xFFE0A33A.toInt()
        c.drawRoundRect(x, y, x + width * frac.coerceIn(0f, 1f), y + 8f, 4f, 4f, p)
    }

    private fun drawBullets(c: Canvas, w: World) {
        p.style = Paint.Style.STROKE
        for (b in w.bullets) {
            if (b.rocket) {
                p.strokeWidth = 6f
                p.color = 0xFFFFA347.toInt()
                c.drawLine(b.x - b.vx * 0.025f, b.y - b.vy * 0.025f, b.x, b.y, p)
                p.color = 0xFFFFE29A.toInt()
                c.drawCircle(b.x, b.y, 4f, p)
            } else {
                p.strokeWidth = 3f
                p.color = 0xFFFFE08A.toInt()
                c.drawLine(b.x - b.vx * 0.018f, b.y - b.vy * 0.018f, b.x, b.y, p)
            }
        }
        p.style = Paint.Style.FILL
    }

    private fun drawParticles(c: Canvas, w: World) {
        for (f in w.particles) {
            val a = (f.life / f.maxLife).coerceIn(0f, 1f)
            p.color = f.color
            p.alpha = (a * 255).toInt()
            c.drawCircle(f.x, f.y, f.size, p)
        }
        p.alpha = 255
    }

    private fun drawPlayers(c: Canvas, w: World) {
        val list = w.players.values.sortedBy { it.y }
        for (pl in list) {
            if (!pl.alive) continue
            if (!pl.isMe && !pl.isBot && pl.hp <= 0f) continue
            val r = 22f
            val skin = SKINS[pl.skin.coerceIn(0, SKINS.size - 1)]
            Art.character(c, skin, pl.x, pl.y, r, pl.angle, pl.weapon, p)
            if (pl.hurt > 0f) {
                p.color = 0x66FF4444
                c.drawCircle(pl.x, pl.y, r * 1.1f, p)
            }
            if (!pl.isMe || pl.hp < 100f) {
                bar(c, pl.x - 30f, pl.y - 54f, 60f, pl.hp / 100f)
            }
            pt.textAlign = Paint.Align.CENTER
            pt.textSize = 20f
            pt.color = if (pl.isMe) ACCENT else 0xFFF2F8EE.toInt()
            pt.style = Paint.Style.STROKE
            pt.strokeWidth = 4f
            pt.color = 0xCC000000.toInt()
            c.drawText(shortName(pl.name), pl.x, pl.y - 62f, pt)
            pt.style = Paint.Style.FILL
            pt.color = if (pl.isMe) ACCENT else 0xFFF2F8EE.toInt()
            c.drawText(shortName(pl.name), pl.x, pl.y - 62f, pt)
            pt.textAlign = Paint.Align.LEFT
        }
        // прицел
        if (w.me.alive) {
            val ax = w.me.x + cos(w.me.aim) * 76f
            val ay = w.me.y + sin(w.me.aim) * 76f
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f
            p.color = 0x66FFFFFF
            c.drawLine(w.me.x + cos(w.me.aim) * 34f, w.me.y + sin(w.me.aim) * 34f, ax, ay, p)
            p.color = 0x99C8FF4D.toInt()
            c.drawCircle(ax, ay, 4f, p)
            p.style = Paint.Style.FILL
        }
    }

    private fun drawTexts(c: Canvas, w: World) {
        pt.textAlign = Paint.Align.CENTER
        pt.textSize = 21f
        for (t in w.texts) {
            pt.color = t.color
            pt.alpha = (t.life / 0.95f * 255).toInt().coerceIn(0, 255)
            pt.style = Paint.Style.STROKE
            pt.strokeWidth = 4f
            pt.color = 0xCC000000.toInt()
            c.drawText(t.text, t.x, t.y, pt)
            pt.style = Paint.Style.FILL
            pt.color = t.color
            pt.alpha = (t.life / 0.95f * 255).toInt().coerceIn(0, 255)
            c.drawText(t.text, t.x, t.y, pt)
        }
        pt.alpha = 255
        pt.textAlign = Paint.Align.LEFT
    }

    private fun shortName(name: String): String = if (name.length > 11) name.substring(0, 11) else name

    // -------------------------------------------------------------------- HUD

    private fun addHud(id: String, l: Float, t: Float, r: Float, b: Float) {
        hudButtons.add(Btn(id, RectF(l, t, r, b)))
    }

    private fun hitHud(x: Float, y: Float): String? {
        for (b in hudButtons) if (b.r.contains(x, y)) return b.id
        return null
    }

    private fun layoutHud() {
        hudButtons.clear()
        slotRects.clear()
        val w = width.toFloat()
        val h = height.toFloat()
        stickR = dp(62f)
        lsX = dp(16f) + stickR
        rsX = w - dp(16f) - stickR
        lsY = h - dp(14f) - dp(52f) - dp(18f) - stickR
        rsY = lsY
        hpY = lsY - stickR - dp(28f)

        addHud("menu", dp(8f), dp(8f) + insTop, dp(56f), dp(56f) + insTop)

        val slot = dp(44f)
        val gap = dp(5f)
        val total = slot * 6 + gap * 5
        val left = (w - total) / 2f
        val top = h - dp(12f) - slot
        for (i in 0 until 6) {
            val l = left + i * (slot + gap)
            slotRects.add(RectF(l, top, l + slot, top + slot))
            addHud("slot$i", l, top, l + slot, top + slot)
        }

        addHud("wall", w - dp(60f), hpY - dp(24f), w - dp(12f), hpY + dp(24f))
        addHud("bench", w - dp(60f), hpY - dp(82f), w - dp(12f), hpY - dp(34f))

        if (paused) {
            val bw = min(w - dp(56f), dp(290f))
            val cx = w / 2f
            val by = h * 0.46f
            addHud("resume", cx - bw / 2f, by, cx + bw / 2f, by + dp(52f))
            addHud("exit", cx - bw / 2f, by + dp(64f), cx + bw / 2f, by + dp(116f))
        }
        if (dead) {
            val bw = min(w - dp(56f), dp(290f))
            val cx = w / 2f
            val by = h * 0.52f
            addHud("respawn", cx - bw / 2f, by, cx + bw / 2f, by + dp(52f))
            addHud("dead_exit", cx - bw / 2f, by + dp(64f), cx + bw / 2f, by + dp(116f))
        }
    }

    private fun drawHud(c: Canvas, w: World) {
        layoutHud()
        val widthF = width.toFloat()
        val heightF = height.toFloat()

        // кнопка меню
        p.color = 0x990B1410.toInt()
        c.drawCircle(dp(32f), dp(32f) + insTop, dp(21f), p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(2f)
        p.color = 0x55FFFFFF
        c.drawCircle(dp(32f), dp(32f) + insTop, dp(21f), p)
        p.style = Paint.Style.FILL
        p.color = TEXT
        c.drawRoundRect(dp(24f), dp(25f) + insTop, dp(40f), dp(29f) + insTop, 2f, 2f, p)
        c.drawRoundRect(dp(24f), dp(32f) + insTop, dp(40f), dp(36f) + insTop, 2f, 2f, p)
        c.drawRoundRect(dp(24f), dp(39f) + insTop, dp(40f), dp(43f) + insTop, 2f, 2f, p)

        // монеты сверху
        val pillW = dp(122f)
        val pillTop = dp(12f) + insTop
        panel(c, widthF / 2f - pillW / 2f, pillTop, widthF / 2f + pillW / 2f, pillTop + dp(34f), dp(17f), 0xCC14241B.toInt(), STROKE)
        Art.coin(c, widthF / 2f - pillW / 2f + dp(20f), pillTop + dp(17f), dp(11f), p)
        txt(fmt(store.coins), widthF / 2f + pillW / 2f - dp(14f), pillTop + dp(22f), dp(15f), GOLD, true, Paint.Align.RIGHT)

        // лидерборд
        drawLeaderboard(c, w)

        // килл-фид
        var fy = dp(58f) + insTop
        val now = System.currentTimeMillis()
        val fi = feed.iterator()
        while (fi.hasNext()) if (now - fi.next().at > 6500L) fi.remove()
        for (m in feed) {
            val age = (now - m.at) / 1000f
            val alpha = (255 * (1f - (age - 4.5f).coerceIn(0f, 2f) / 2f)).toInt().coerceIn(0, 255)
            pt.textSize = dp(11.5f)
            pt.textAlign = Paint.Align.LEFT
            pt.color = 0xAA000000.toInt()
            pt.alpha = alpha
            c.drawText(m.text, dp(15f) + 1f, fy + 1f, pt)
            pt.color = m.color
            pt.alpha = alpha
            c.drawText(m.text, dp(15f), fy, pt)
            fy += dp(18f)
        }
        pt.alpha = 255

        // хп и ресурсы
        val hpW = dp(180f)
        val hx = widthF / 2f - hpW / 2f
        panel(c, hx, hpY, hx + hpW, hpY + dp(12f), dp(6f), 0xAA000000.toInt(), STROKE)
        val frac = (w.me.hp / 100f).coerceIn(0f, 1f)
        p.color = if (frac > 0.5f) GREEN else if (frac > 0.25f) 0xFFE0A33A.toInt() else RED
        c.drawRoundRect(hx + dp(2f), hpY + dp(2f), hx + (hpW - dp(2f)) * frac, hpY + dp(10f), dp(4f), dp(4f), p)
        txt("${w.me.hp.toInt()}", widthF / 2f, hpY + dp(10f), dp(10f), 0xFFFFFFFF.toInt(), true, Paint.Align.CENTER)

        val resY = hpY - dp(22f)
        val rw = dp(72f)
        panel(c, widthF / 2f - rw - dp(4f), resY - dp(14f), widthF / 2f - dp(4f), resY + dp(14f), dp(12f), 0x9914241B.toInt(), 0)
        Art.woodIcon(c, widthF / 2f - rw + dp(12f), resY, dp(9f), p)
        txt("${w.me.wood}", widthF / 2f - dp(12f), resY + dp(5f), dp(12f), TEXT, true, Paint.Align.RIGHT)
        panel(c, widthF / 2f + dp(4f), resY - dp(14f), widthF / 2f + rw + dp(4f), resY + dp(14f), dp(12f), 0x9914241B.toInt(), 0)
        Art.stoneIcon(c, widthF / 2f + dp(16f), resY, dp(9f), p)
        txt("${w.me.stone}", widthF / 2f + rw - dp(10f), resY + dp(5f), dp(12f), TEXT, true, Paint.Align.RIGHT)

        // хотбар
        for (i in 0 until 6) {
            val r = slotRects[i]
            val selected = w.me.weapon == i
            panel(c, r.left, r.top, r.right, r.bottom, dp(9f), if (selected) 0xEE1F3526.toInt() else 0xCC101C14.toInt(), if (selected) ACCENT else 0x66466B4D)
            Art.weapon(c, i, r.centerX(), r.centerY() - dp(2f), dp(30f), p)
            if (i == 3) {
                txt("${w.me.ammoT}", r.right - dp(4f), r.bottom - dp(4f), dp(9f), if (w.me.ammoT > 0) TEXT else RED, true, Paint.Align.RIGHT)
            } else if (i == 4) {
                txt("${w.me.ammoR}", r.right - dp(4f), r.bottom - dp(4f), dp(9f), if (w.me.ammoR > 0) TEXT else RED, true, Paint.Align.RIGHT)
            } else if (i == 5) {
                txt("x${w.me.meds}", r.right - dp(4f), r.bottom - dp(4f), dp(9f), TEXT, true, Paint.Align.RIGHT)
            }
            if (selected && w.me.fireCd > 0f) {
                val cd = (w.me.fireCd / World.WEAPONS[i].rate).coerceIn(0f, 1f)
                p.color = 0x66000000
                c.drawRect(r.left + dp(2f), r.top + dp(2f), r.right - dp(2f), r.top + dp(2f) + (r.height() - dp(4f)) * cd, p)
            }
        }

        // джойстики
        drawStick(c, lsX, lsY, leftKnob, leftPointer >= 0, ACCENT)
        drawStick(c, rsX, rsY, rightKnob, rightPointer >= 0, GOLD)

        // постройки
        drawBuildButtons(c, w)

        if (paused) drawPause(c)
        if (dead) drawDeath(c, w)
        drawToast(c)
    }

    private fun drawLeaderboard(c: Canvas, w: World) {
        val right = width - dp(10f)
        val bw = dp(146f)
        val left = right - bw
        val top = dp(54f) + insTop
        val rows = w.players.values
            .filter { it.alive || it.isMe }
            .sortedWith(compareByDescending<P> { it.kills }.thenByDescending { it.coins })
            .take(7)
        val bh = dp(24f) + rows.size * dp(17f)
        panel(c, left, top, right, top + bh, dp(10f), 0x99101C14.toInt(), 0x55466B4D)
        txt("ЛИДЕРЫ", left + dp(9f), top + dp(15f), dp(9.5f), DIM, true)
        var y = top + dp(30f)
        for ((i, p1) in rows.withIndex()) {
            val col = if (p1.isMe) ACCENT else TEXT
            txt("${i + 1}", left + dp(9f), y, dp(10.5f), if (i == 0) GOLD else DIM, true)
            txt(shortName(p1.name), left + dp(25f), y, dp(10.5f), col, p1.isMe)
            txt("${p1.kills}", right - dp(9f), y, dp(10.5f), col, true, Paint.Align.RIGHT)
            y += dp(17f)
        }
    }

    private fun drawStick(c: Canvas, cx: Float, cy: Float, knob: PointF, active: Boolean, color: Int) {
        p.style = Paint.Style.FILL
        p.color = if (active) 0x22FFFFFF else 0x14FFFFFF
        c.drawCircle(cx, cy, stickR, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(1.5f)
        p.color = if (active) 0x88FFFFFF.toInt().toInt() else 0x44FFFFFF
        c.drawCircle(cx, cy, stickR, p)
        p.style = Paint.Style.FILL
        p.color = if (active) 0x77FFFFFF else 0x55FFFFFF
        c.drawCircle(cx + knob.x, cy + knob.y, stickR * 0.38f, p)
        p.color = color
        p.alpha = if (active) 180 else 90
        c.drawCircle(cx + knob.x, cy + knob.y, stickR * 0.18f, p)
        p.alpha = 255
    }

    private fun drawBuildButtons(c: Canvas, w: World) {
        val nearBench = w.nearestBench() != null
        val wallOk = w.me.wood >= 12
        val benchOk = if (nearBench) w.me.wood >= 15 && w.me.stone >= 10 else w.me.wood >= 25 && w.me.stone >= 15
        val cx = width - dp(36f)
        val yWall = hpY
        val yBench = hpY - dp(58f)
        buildBtn(c, cx, yWall, wallOk, "12")
        p.color = 0xFFA97A45.toInt()
        c.drawRoundRect(cx - dp(13f), yWall - dp(9f), cx + dp(13f), yWall + dp(11f), dp(3f), dp(3f), p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(1.3f)
        p.color = 0xFF6E4C2B.toInt()
        c.drawLine(cx - dp(10f), yWall - dp(2f), cx + dp(10f), yWall - dp(2f), p)
        c.drawLine(cx - dp(10f), yWall + dp(5f), cx + dp(10f), yWall + dp(5f), p)
        p.style = Paint.Style.FILL

        buildBtn(c, cx, yBench, benchOk, if (nearBench) "+2" else "25")
        if (nearBench) {
            Art.rocketIcon(c, cx, yBench, dp(20f), p)
        } else {
            p.color = 0xFFB98A55.toInt()
            c.drawRoundRect(cx - dp(13f), yBench - dp(7f), cx + dp(13f), yBench + dp(3f), dp(3f), dp(3f), p)
            p.color = 0xFF6E4C2B.toInt()
            c.drawRect(cx - dp(10f), yBench + dp(3f), cx - dp(6f), yBench + dp(12f), p)
            c.drawRect(cx + dp(7f), yBench + dp(3f), cx + dp(11f), yBench + dp(12f), p)
        }
    }

    private fun buildBtn(c: Canvas, cx: Float, cy: Float, ok: Boolean, cost: String) {
        p.color = if (ok) 0xCC1A2C20.toInt() else 0x88101812.toInt()
        c.drawCircle(cx, cy - dp(2f), dp(23f), p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(1.5f)
        p.color = if (ok) 0x88466B4D.toInt() else 0x33333333.toInt()
        c.drawCircle(cx, cy - dp(2f), dp(23f), p)
        p.style = Paint.Style.FILL
        txt(cost, cx, cy + dp(15f), dp(8f), if (ok) GOLD else DIM, true, Paint.Align.CENTER)
    }

    private fun drawPause(c: Canvas) {
        p.color = 0xBB050A07.toInt()
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        val cx = width / 2f
        txt("ПАУЗА", cx, height * 0.34f, dp(30f), TEXT, true, Paint.Align.CENTER)
        txt("лес подождёт", cx, height * 0.38f, dp(12f), DIM, false, Paint.Align.CENTER)
        val bw = min(width - dp(56f), dp(290f))
        val by = height * 0.46f
        primaryButton(c, RectF(cx - bw / 2f, by, cx + bw / 2f, by + dp(52f)), "ПРОДОЛЖИТЬ")
        secondaryButton(c, RectF(cx - bw / 2f, by + dp(64f), cx + bw / 2f, by + dp(116f)), "В МЕНЮ")
        txt("Выйти из боя можно в любой момент", cx, by + dp(144f), dp(10f), DIM, false, Paint.Align.CENTER)
    }

    private fun drawDeath(c: Canvas, w: World) {
        p.color = 0xCC1A0606.toInt()
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        val cx = width / 2f
        txt("ТЫ ПОГИБ", cx, height * 0.30f, dp(38f), RED, true, Paint.Align.CENTER)
        txt("убил: $deathKiller", cx, height * 0.35f, dp(14f), TEXT, false, Paint.Align.CENTER)
        txt("убийств в бою: ${w.me.kills}", cx, height * 0.40f, dp(13f), DIM, false, Paint.Align.CENTER)
        txt("монет всего: ${fmt(store.coins)}", cx, height * 0.44f, dp(13f), GOLD, true, Paint.Align.CENTER)
        val bw = min(width - dp(56f), dp(290f))
        val by = height * 0.52f
        primaryButton(c, RectF(cx - bw / 2f, by, cx + bw / 2f, by + dp(52f)), "ВОЗРОДИТЬСЯ")
        secondaryButton(c, RectF(cx - bw / 2f, by + dp(64f), cx + bw / 2f, by + dp(116f)), "В МЕНЮ")
    }

    private fun drawToast(c: Canvas) {
        if (toastMsg.isEmpty()) return
        val age = System.currentTimeMillis() - toastAt
        if (age > 2200L) return
        val a = if (age > 1700L) ((2200L - age) / 500f * 255).toInt() else 255
        pt.textSize = dp(12f)
        pt.textAlign = Paint.Align.CENTER
        val tw = pt.measureText(toastMsg)
        val x = width / 2f
        val y = height * 0.70f
        p.color = 0xE6101C14.toInt()
        p.alpha = a
        c.drawRoundRect(x - tw / 2f - dp(16f), y - dp(20f), x + tw / 2f + dp(16f), y + dp(10f), dp(15f), dp(15f), p)
        pt.color = TEXT
        pt.alpha = a
        c.drawText(toastMsg, x, y, pt)
        p.alpha = 255
        pt.alpha = 255
        pt.textAlign = Paint.Align.LEFT
    }

    // --------------------------------------------------------------- розыгрыш

    private fun drawBackdrop(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.shader = bgShader
        p.color = 0xFFFFFFFF.toInt()
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(1f)
        p.color = 0x08FFFFFF
        var x = 0f
        while (x < w) {
            c.drawLine(x, 0f, x, h, p)
            x += dp(44f)
        }
        var y = 0f
        while (y < h) {
            c.drawLine(0f, y, w, y, p)
            y += dp(44f)
        }
        p.style = Paint.Style.FILL
        p.color = 0x10C8FF4D
        c.drawCircle(w * 0.14f, h * 0.24f, dp(86f), p)
        c.drawCircle(w * 0.88f, h * 0.40f, dp(110f), p)

        p.color = 0x22000000
        val path = Path()
        val base = h * 0.92f
        var i = 0
        while (i < 9) {
            val bx = w * (i + 0.5f) / 9f
            val bw = dp(34f) + (i % 3) * dp(6f)
            path.moveTo(bx - bw, base)
            path.lineTo(bx, base - dp(72f) - (i % 4) * dp(14f))
            path.lineTo(bx + bw, base)
            path.close()
            i++
        }
        c.drawPath(path, p)

        // парящие монеты
        for (k in 0 until 5) {
            val px = w * (0.12f + k * 0.19f) + sin(time * 0.5f + k) * dp(10f)
            val py = h * 0.42f + cos(time * 0.7f + k * 1.7f) * dp(16f)
            p.alpha = 40
            Art.coin(c, px, py, dp(7f) + (k % 2) * dp(3f), p)
            p.alpha = 255
        }
        p.style = Paint.Style.FILL
    }

    private fun drawMenu(c: Canvas) {
        menuButtons.clear()
        val w = width.toFloat()
        val h = height.toFloat()

        txt("@${store.user ?: "игрок"}", dp(18f), dp(30f) + insTop, dp(12f), DIM, false)
        coinPill(c, w - dp(16f), dp(12f) + insTop, store.coins)

        val tSize = min(dp(52f), w * 0.135f)
        val t1 = "ГРАММ"
        val t2 = ".io"
        pb.textSize = tSize
        val tw = pb.measureText(t1) + pb.measureText(t2)
        val tx = w / 2f - tw / 2f
        val ty = h * 0.155f
        txt(t1, tx, ty, tSize, TEXT, true)
        txt(t2, tx + pb.measureText(t1), ty, tSize, ACCENT, true)
        txt("выживание · онлайн", w / 2f, ty + dp(24f), dp(12f), DIM, false, Paint.Align.CENTER)

        val charY = h * 0.40f
        p.color = 0x33000000
        c.drawOval(w / 2f - dp(76f), charY + dp(44f), w / 2f + dp(76f), charY + dp(66f), p)
        val bob = sin(time * 1.8f) * dp(3f)
        val selected = store.selectedSkin.coerceIn(0, SKINS.size - 1)
        Art.character(c, SKINS[selected], w / 2f, charY + bob, dp(38f), -0.18f + sin(time * 0.9f) * 0.12f, 3, p)
        txt(SKINS[selected].name, w / 2f, charY + dp(82f), dp(13f), TEXT, true, Paint.Align.CENTER)
        txt("скин", w / 2f, charY + dp(99f), dp(9f), DIM, false, Paint.Align.CENTER)

        arrow(c, w / 2f - dp(104f), charY, "‹", "prevskin")
        arrow(c, w / 2f + dp(104f), charY, "›", "nextskin")

        val bw = min(w - dp(56f), dp(300f))
        val bh = dp(58f)
        val playR = RectF((w - bw) / 2f, h * 0.60f, (w + bw) / 2f, h * 0.60f + bh)
        primaryButton(c, playR, "ИГРАТЬ")
        menuButtons.add(Btn("play", playR))

        val gap = dp(10f)
        val sw = (bw - gap) / 2f
        val sy = playR.bottom + dp(12f)
        val skinR = RectF((w - bw) / 2f, sy, (w - bw) / 2f + sw, sy + dp(44f))
        val logoutR = RectF((w + bw) / 2f - sw, sy, (w + bw) / 2f, sy + dp(44f))
        secondaryButton(c, skinR, "СКИНЫ")
        secondaryButton(c, logoutR, "ВЫЙТИ")
        menuButtons.add(Btn("skins", skinR))
        menuButtons.add(Btn("logout", logoutR))

        val kills = store.totalKills()
        txt("сезон: $kills убийств", w / 2f, h - dp(26f), dp(10.5f), DIM, false, Paint.Align.CENTER)
        p.color = if (net.connected) GREEN else 0xFFFF8A5C.toInt()
        c.drawCircle(dp(18f), h - dp(22f), dp(3.5f), p)
        txt(if (net.connected) "онлайн" else "офлайн", dp(26f), h - dp(18f), dp(9.5f), DIM, false)
    }

    private fun drawServers(c: Canvas) {
        menuButtons.clear()
        val w = width.toFloat()
        val h = height.toFloat()
        backButton(c, "back")
        txt("СЕРВЕРА", w / 2f, dp(36f) + insTop, dp(22f), TEXT, true, Paint.Align.CENTER)
        txt("5 миров · общий онлайн", w / 2f, dp(53f) + insTop, dp(10f), DIM, false, Paint.Align.CENTER)
        coinPill(c, w - dp(12f), dp(10f) + insTop, store.coins)

        val top = dp(70f) + insTop
        val gap = dp(8f)
        val avail = h - top - dp(18f)
        val cardH = min(dp(96f), (avail - gap * 4) / 5f)
        val left = dp(14f)
        val right = w - dp(14f)

        for (i in 0 until 5) {
            val t = top + i * (cardH + gap)
            val r = RectF(left, t, right, t + cardH)
            panel(c, r.left, r.top, r.right, r.bottom, dp(14f), 0xE6112018.toInt(), 0x55466B4D)
            val cy = r.centerY()
            p.color = 0xFF1E3A29.toInt()
            c.drawCircle(left + dp(34f), cy, dp(24f), p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = dp(1.2f)
            p.color = 0x66466B4D
            c.drawCircle(left + dp(34f), cy, dp(24f), p)
            p.style = Paint.Style.FILL
            val info = SERVERS[i]
            txt("${i + 1}", left + dp(34f), cy + dp(6f), dp(18f), ACCENT, true, Paint.Align.CENTER)
            txt(info.name, left + dp(68f), cy - dp(12f), dp(16f), TEXT, true)
            txt("${info.region} · ${info.tag}", left + dp(68f), cy + dp(5f), dp(9.5f), DIM)
            val on = online(i)
            txt(
                "${info.region}  ·  ${Wipe.label(i)}  ·  ${Wipe.left(i, System.currentTimeMillis())}",
                left + dp(68f), cy + dp(22f), dp(9f), 0xFF8FAF91.toInt()
            )
            p.color = if (on > 0) GREEN else DIM
            c.drawCircle(right - dp(56f), cy - dp(12f), dp(3f), p)
            txt("$on", right - dp(48f), cy - dp(9f), dp(10f), TEXT, true)
            txt("онлайн", right - dp(26f), cy - dp(9f), dp(8.5f), DIM, false, Paint.Align.RIGHT)
            txt("ИГРАТЬ ›", right - dp(14f), cy + dp(18f), dp(11f), ACCENT, true, Paint.Align.RIGHT)
            txt("убийств: ${store.kills(i)}", right - dp(14f), cy + dp(34f), dp(8.5f), DIM, false, Paint.Align.RIGHT)
            menuButtons.add(Btn("srv$i", r))
        }
    }

    private fun drawSkins(c: Canvas) {
        menuButtons.clear()
        val w = width.toFloat()
        val h = height.toFloat()
        backButton(c, "back")
        txt("СКИНЫ", w / 2f, dp(36f) + insTop, dp(22f), TEXT, true, Paint.Align.CENTER)
        txt("монеты за бои и ящики", w / 2f, dp(53f) + insTop, dp(10f), DIM, false, Paint.Align.CENTER)
        coinPill(c, w - dp(12f), dp(10f) + insTop, store.coins)

        val top = dp(70f) + insTop
        val gap = dp(9f)
        val cols = 2
        val cellW = (w - dp(14f) * 2 - gap) / cols
        val cellH = ((h - top - dp(16f) - gap * 4) / 5f).coerceIn(dp(62f), dp(110f))
        for (i in SKINS.indices) {
            val col = i % 2
            val row = i / 2
            val l = dp(14f) + col * (cellW + gap)
            val t = top + row * (cellH + gap)
            val r = RectF(l, t, l + cellW, t + cellH)
            val owned = store.ownsSkin(i)
            val isSel = store.selectedSkin == i
            panel(
                c, r.left, r.top, r.right, r.bottom, dp(13f),
                if (isSel) 0xEE1D3223.toInt() else 0xDD112018.toInt(),
                if (isSel) ACCENT else 0x55466B4D
            )
            val prevR = min(dp(21f), cellH * 0.31f)
            Art.character(c, SKINS[i], l + dp(28f), r.centerY() - dp(2f), prevR, -0.2f, 3, p)
            txt(SKINS[i].name, l + dp(52f), r.centerY() - dp(6f), dp(12.5f), TEXT, true)
            val statusY = r.centerY() + dp(13f)
            when {
                isSel -> txt("НАДЕТ", l + dp(52f), statusY, dp(9.5f), ACCENT, true)
                owned -> txt("ОТКРЫТ", l + dp(52f), statusY, dp(9.5f), GREEN, true)
                else -> {
                    Art.coin(c, l + dp(57f), statusY - dp(3f), dp(5.5f), p)
                    txt(fmt(SKINS[i].price), l + dp(65f), statusY, dp(10f), GOLD, true)
                }
            }
            if (!owned && store.coins < SKINS[i].price) {
                p.color = 0x33000000
                c.drawRoundRect(r.left, r.top, r.right, r.bottom, dp(13f), dp(13f), p)
            }
            menuButtons.add(Btn("skin$i", r))
        }
        txt("скины открываются за монеты и остаются навсегда", w / 2f, h - dp(14f), dp(9f), DIM, false, Paint.Align.CENTER)
    }

    private fun coinPill(c: Canvas, right: Float, top: Float, coins: Int) {
        val pw = dp(108f)
        panel(c, right - pw, top, right, top + dp(34f), dp(17f), 0xCC14241B.toInt(), STROKE)
        Art.coin(c, right - pw + dp(19f), top + dp(17f), dp(10f), p)
        txt(fmt(coins), right - dp(13f), top + dp(22f), dp(14f), GOLD, true, Paint.Align.RIGHT)
    }

    private fun backButton(c: Canvas, id: String) {
        val cx = dp(30f)
        val cy = dp(30f) + insTop
        p.color = 0x99101C14.toInt()
        c.drawCircle(cx, cy, dp(19f), p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(2f)
        p.color = 0x55FFFFFF
        c.drawCircle(cx, cy, dp(19f), p)
        p.style = Paint.Style.FILL
        p.color = TEXT
        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(2.2f)
        c.drawLine(cx + dp(5f), cy - dp(7f), cx - dp(4f), cy, p)
        c.drawLine(cx - dp(4f), cy, cx + dp(5f), cy + dp(7f), p)
        p.style = Paint.Style.FILL
        menuButtons.add(Btn(id, RectF(cx - dp(22f), cy - dp(22f), cx + dp(22f), cy + dp(22f))))
    }

    private fun arrow(c: Canvas, x: Float, y: Float, label: String, id: String) {
        p.color = 0x88101C14.toInt()
        c.drawCircle(x, y, dp(19f), p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = dp(1.2f)
        p.color = 0x55FFFFFF
        c.drawCircle(x, y, dp(19f), p)
        p.style = Paint.Style.FILL
        txt(label, x, y + dp(7f), dp(22f), TEXT, true, Paint.Align.CENTER)
        menuButtons.add(Btn(id, RectF(x - dp(22f), y - dp(22f), x + dp(22f), y + dp(22f))))
    }

    private fun primaryButton(c: Canvas, r: RectF, label: String) {
        p.color = ACCENT
        c.drawRoundRect(r, dp(16f), dp(16f), p)
        p.color = 0x33000000
        c.drawRoundRect(r.left, r.top + dp(4f), r.right, r.bottom, dp(16f), dp(16f), p)
        p.color = ACCENT
        c.drawRoundRect(r, dp(16f), dp(16f), p)
        txt(label, r.centerX(), r.centerY() + dp(6f), dp(16f), 0xFF0C150F.toInt(), true, Paint.Align.CENTER)
    }

    private fun secondaryButton(c: Canvas, r: RectF, label: String) {
        panel(c, r.left, r.top, r.right, r.bottom, dp(13f), 0xDD15251C.toInt(), STROKE)
        txt(label, r.centerX(), r.centerY() + dp(5f), dp(13f), TEXT, true, Paint.Align.CENTER)
    }

    private fun panel(c: Canvas, l: Float, t: Float, r: Float, b: Float, rad: Float, fill: Int, stroke: Int) {
        p.style = Paint.Style.FILL
        p.color = fill
        c.drawRoundRect(l, t, r, b, rad, rad, p)
        if (stroke != 0) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = dp(1f)
            p.color = stroke
            c.drawRoundRect(l, t, r, b, rad, rad, p)
            p.style = Paint.Style.FILL
        }
    }

    private fun txt(s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) {
        val paint = if (bold) pb else pt
        paint.textSize = size
        paint.color = color
        paint.textAlign = align
        paint.style = Paint.Style.FILL
        canvasDraw(s, x, y, paint)
    }

    private var currentCanvas: Canvas? = null

    private fun canvasDraw(s: String, x: Float, y: Float, paint: Paint) {
        currentCanvas?.drawText(s, x, y, paint)
    }

    // ------------------------------------------------------------- управление

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (screen == Scr.GAME) gameTouch(e) else uiTouch(e)
        return true
    }

    private fun uiTouch(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                downY = e.y
                tapMoved = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(e.x - downX) > dp(12f) || abs(e.y - downY) > dp(12f)) tapMoved = true
            }
            MotionEvent.ACTION_UP -> {
                if (!tapMoved) {
                    var hit: Btn? = null
                    for (b in menuButtons) if (b.r.contains(downX, downY)) hit = b
                    hit?.let { onUi(it.id) }
                }
            }
        }
    }

    private fun onUi(id: String) {
        when {
            id == "play" -> screen = Scr.SERVERS
            id == "skins" -> screen = Scr.SKINS
            id == "back" -> screen = Scr.MENU
            id == "logout" -> onLogout?.invoke()
            id == "prevskin" || id == "nextskin" -> {
                val owned = store.ownedSkins().sorted()
                if (owned.size > 1) {
                    var idx = owned.indexOf(store.selectedSkin)
                    if (idx < 0) idx = 0
                    val next = if (id == "nextskin") (idx + 1) % owned.size else (idx - 1 + owned.size) % owned.size
                    store.selectSkin(owned[next])
                }
            }
            id.startsWith("srv") -> id.removePrefix("srv").toIntOrNull()?.let { play(it) }
            id.startsWith("skin") -> {
                val index = id.removePrefix("skin").toIntOrNull() ?: return
                if (store.ownsSkin(index)) {
                    store.selectSkin(index)
                    toast("Скин: ${SKINS[index].name}")
                } else if (store.buySkin(index)) {
                    toast("Куплено: ${SKINS[index].name}")
                } else {
                    toast("Не хватает ${fmt(SKINS[index].price - store.coins)} монет")
                }
            }
        }
    }

    private fun gameTouch(e: MotionEvent) {
        layoutHud()
        val w = world ?: return
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = e.actionIndex
                val x = e.getX(idx)
                val y = e.getY(idx)
                val pid = e.getPointerId(idx)

                if (paused || dead) {
                    val id = hitHud(x, y)
                    if (id != null) handleHud(id)
                    return
                }

                val btn = hitHud(x, y)
                if (btn != null) {
                    handleHud(btn)
                    return
                }

                val dl = dist2(x, y, lsX, lsY)
                val dr = dist2(x, y, rsX, rsY)
                if (dl < (stickR * 1.6f) * (stickR * 1.6f) && leftPointer < 0) {
                    leftPointer = pid
                    updateLeft(x, y)
                } else if (dr < (stickR * 1.6f) * (stickR * 1.6f) && rightPointer < 0) {
                    rightPointer = pid
                    updateRight(x, y, w)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val pid = e.getPointerId(i)
                    val x = e.getX(i)
                    val y = e.getY(i)
                    if (pid == leftPointer) updateLeft(x, y)
                    if (pid == rightPointer) updateRight(x, y, w)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val idx = e.actionIndex
                val pid = e.getPointerId(idx)
                if (pid == leftPointer) {
                    leftPointer = -1
                    moveX = 0f
                    moveY = 0f
                    leftKnob.set(0f, 0f)
                }
                if (pid == rightPointer) {
                    rightPointer = -1
                    firing = false
                    rightKnob.set(0f, 0f)
                }
            }
        }
    }

    private fun dist2(x: Float, y: Float, cx: Float, cy: Float): Float {
        val dx = x - cx
        val dy = y - cy
        return dx * dx + dy * dy
    }

    private fun updateLeft(x: Float, y: Float) {
        val dx = x - lsX
        val dy = y - lsY
        val r = stickR * 0.9f
        val d = sqrt(dx * dx + dy * dy)
        if (d > r) {
            leftKnob.set(dx / d * r, dy / d * r)
            moveX = (dx / d) * min(1f, d / r)
            moveY = (dy / d) * min(1f, d / r)
        } else {
            leftKnob.set(dx, dy)
            moveX = dx / r
            moveY = dy / r
        }
        val ml = sqrt(moveX * moveX + moveY * moveY)
        if (ml > 1f) {
            moveX /= ml
            moveY /= ml
        }
    }

    private fun updateRight(x: Float, y: Float, w: World) {
        val dx = x - rsX
        val dy = y - rsY
        val r = stickR * 0.9f
        val d = sqrt(dx * dx + dy * dy)
        val clamped = min(d, r)
        if (d > 0.001f) {
            rightKnob.set(dx / d * clamped, dy / d * clamped)
        }
        if (d > dp(8f)) {
            val raw = atan2(dy, dx)
            aimAngle = w.aimAssist(raw, 560f)
            firing = true
        } else {
            firing = true
        }
    }

    private fun handleHud(id: String) {
        val w = world ?: return
        when {
            id == "menu" -> paused = true
            id == "resume" -> paused = false
            id == "exit" || id == "dead_exit" -> exitGame()
            id == "respawn" -> {
                w.respawnMe()
                w.me.coins = store.coins
                dead = false
                paused = false
                aimAngle = 0f
                moveX = 0f
                moveY = 0f
                leftPointer = -1
                rightPointer = -1
                sendState()
            }
            id == "wall" -> {
                val msg = w.wallAction()
                if (msg.isNotEmpty()) toast(msg)
            }
            id == "bench" -> {
                val msg = w.benchAction()
                if (msg.isNotEmpty()) toast(msg)
            }
            id.startsWith("slot") -> {
                val i = id.removePrefix("slot").toIntOrNull() ?: return
                w.me.weapon = i
            }
        }
    }

    private fun toast(s: String) {
        toastMsg = s
        toastAt = System.currentTimeMillis()
    }
}
