package io.gramm.game

import java.util.Random
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class Ent(val id: Int, val type: Int, var x: Float, var y: Float, var hp: Float, val maxHp: Float, val r: Float) {
    var dead = false
    var flash = 0f
    var grow = 0f
}

class P(
    val id: String,
    var name: String,
    var skin: Int,
    var x: Float,
    var y: Float,
    val isBot: Boolean,
    val isMe: Boolean
) {
    var hp = 100f
    var alive = true
    var aim = 0f
    var angle = 0f
    var weapon = 0
    var kills = 0
    var coins = 0
    var wood = 0
    var stone = 0
    var ammoT = 45
    var ammoR = 3
    var meds = 1
    var fireCd = 0f
    var healCd = 0f
    var hurt = 0f
    var flash = 0f
    var deadT = 0f
    var walk = 0f
    var mx = 0f
    var my = 0f
    var tx = 0f
    var ty = 0f
    var lastSeen = 0L
    var attackerId = ""
    var attackerName = ""
    var think = 0f
    var targetX = 0f
    var targetY = 0f
    var botCd = 0f
    var lastX = 0f
    var lastY = 0f
    var stuck = 0f
}

class Bullet(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var dmg: Float,
    val owner: String,
    val rocket: Boolean,
    var life: Float
)

class Fx(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    var maxLife: Float,
    var size: Float,
    var color: Int
)

class Pop(var x: Float, var y: Float, var text: String, var color: Int, var life: Float)

class World(
    val server: Int,
    private val events: Events,
    private val playerName: String,
    private val playerSkin: Int
) {
    companion object {
        const val W = 2800f
        const val H = 2800f

        const val T_TREE = 0
        const val T_ROCK = 1
        const val T_BUSH = 2
        const val T_CRATE = 3
        const val T_BARREL = 4
        const val T_WALL = 5
        const val T_BENCH = 6

        const val SLOT_MED = 5

        data class Wpn(val name: String, val kind: Int, val dmg: Float, val rate: Float, val speed: Float, val range: Float)

        val WEAPONS = listOf(
            Wpn("Кулаки", 0, 7f, 0.38f, 0f, 68f),
            Wpn("Топор", 0, 16f, 0.50f, 0f, 82f),
            Wpn("Кирка", 0, 13f, 0.50f, 0f, 82f),
            Wpn("Томпсон", 1, 13f, 0.11f, 1180f, 720f),
            Wpn("Базука", 2, 55f, 1.55f, 660f, 900f),
            Wpn("Аптечка", 3, 0f, 1.10f, 0f, 0f)
        )
    }

    interface Events {
        fun remoteDamage(targetId: String, dmg: Float, byName: String)
        fun objectGone(id: Int)
        fun wallBuilt(e: Ent)
        fun playerDied(victim: P, killer: P?, killerId: String, killerName: String)
        fun shot(owner: P)
        fun coinsEarned(p: P, n: Int)
    }

    val objects = ArrayList<Ent>()
    val players = LinkedHashMap<String, P>()
    val bullets = ArrayList<Bullet>()
    val particles = ArrayList<Fx>()
    val texts = ArrayList<Pop>()

    lateinit var me: P
    var time = 0f
    var shake = 0f

    private val rnd = Random(server * 7919L + 1237L)
    private var nextObjId = 1
    private var builtId = 1_000_000

    init {
        generate()
        spawnLocal()
        repeat(6) { spawnBot() }
    }

    // ------------------------------------------------------------- генерация

    private fun treeHp() = 50f
    private fun rockHp() = 75f
    private fun crateHp() = 26f
    private fun barrelHp() = 20f

    private fun radiusOf(t: Int) = when (t) {
        T_TREE -> 34f
        T_ROCK -> 31f
        T_BUSH -> 25f
        T_CRATE -> 21f
        T_BARREL -> 23f
        T_WALL -> 34f
        T_BENCH -> 34f
        else -> 24f
    }

    private fun hpOf(t: Int) = when (t) {
        T_TREE -> treeHp()
        T_ROCK -> rockHp()
        T_CRATE -> crateHp()
        T_BARREL -> barrelHp()
        T_WALL -> 240f
        T_BENCH -> 200f
        else -> 10f
    }

    private fun add(t: Int, x: Float, y: Float, hp: Float = 0f): Ent {
        val realHp = if (hp > 0f) hp else hpOf(t)
        val e = Ent(nextObjId++, t, x, y, realHp, realHp, radiusOf(t))
        objects.add(e)
        return e
    }

    private fun overlaps(x: Float, y: Float, pad: Float): Boolean {
        for (e in objects) {
            val dx = e.x - x
            val dy = e.y - y
            val rr = e.r + pad + 6f
            if (dx * dx + dy * dy < rr * rr) return true
        }
        return false
    }

    private fun nearCount(x: Float, y: Float, radius: Float): Int {
        var n = 0
        for (e in objects) {
            val dx = e.x - x
            val dy = e.y - y
            if (dx * dx + dy * dy < radius * radius) n++
        }
        return n
    }

    private fun generate() {
        var made = 0
        var guard = 0
        while (made < 300 && guard < 4000) {
            guard++
            val x = 90f + rnd.nextFloat() * (W - 180f)
            val y = 90f + rnd.nextFloat() * (H - 180f)
            val dc = (x - W / 2) * (x - W / 2) + (y - H / 2) * (y - H / 2)
            if (dc < 290f * 290f) continue
            val roll = rnd.nextFloat()
            val t = when {
                roll < 0.54f -> T_TREE
                roll < 0.79f -> T_ROCK
                else -> T_BUSH
            }
            if (overlaps(x, y, radiusOf(t))) continue
            add(t, x, y)
            made++
        }

        var crates = 0
        guard = 0
        while (crates < 46 && guard < 3000) {
            guard++
            val x = 110f + rnd.nextFloat() * (W - 220f)
            val y = 110f + rnd.nextFloat() * (H - 220f)
            val dc = (x - W / 2) * (x - W / 2) + (y - H / 2) * (y - H / 2)
            if (dc < 240f * 240f) continue
            if (overlaps(x, y, 24f)) continue
            add(T_CRATE, x, y)
            crates++
        }

        var barrels = 0
        guard = 0
        while (barrels < 30 && guard < 3000) {
            guard++
            val x = 120f + rnd.nextFloat() * (W - 240f)
            val y = 120f + rnd.nextFloat() * (H - 240f)
            if (overlaps(x, y, 28f)) continue
            add(T_BARREL, x, y)
            barrels++
        }

        var bases = 0
        guard = 0
        while (bases < 6 && guard < 400) {
            guard++
            val bx = 430f + rnd.nextFloat() * (W - 860f)
            val by = 430f + rnd.nextFloat() * (H - 860f)
            val dc = (bx - W / 2) * (bx - W / 2) + (by - H / 2) * (by - H / 2)
            if (dc < 520f * 520f) continue
            if (nearCount(bx, by, 280f) > 10) continue
            bases++
            val xs = floatArrayOf(-140f, -70f, 0f, 70f, 140f)
            for (dx in xs) {
                if (dx != 0f) add(T_WALL, bx + dx, by - 105f)
                add(T_WALL, bx + dx, by + 105f)
            }
            add(T_WALL, bx - 140f, by)
            add(T_WALL, bx + 140f, by)
            add(T_BENCH, bx, by)
            add(T_CRATE, bx - 56f, by + 40f)
            add(T_CRATE, bx + 58f, by + 38f)
            add(T_BARREL, bx + 85f, by - 25f)
        }

        // стартовый ящик недалеко от центра
        add(T_CRATE, W / 2 + 210f, H / 2 + 60f)
        add(T_CRATE, W / 2 - 170f, H / 2 - 120f)
        add(T_TREE, W / 2 + 280f, H / 2 - 140f)
    }

    private fun solid(t: Int): Boolean = when (t) {
        T_TREE, T_ROCK, T_BARREL, T_WALL, T_BENCH -> true
        else -> false
    }

    private fun destructible(t: Int): Boolean = t != T_BUSH

    private fun blocksBullets(t: Int): Boolean = t != T_BUSH

    // -------------------------------------------------------------- игроки

    private fun spawnLocal() {
        me = P("u" + UUID.randomUUID().toString().replace("-", "").take(12), playerName, playerSkin, W / 2f, H / 2f, false, true)
        me.weapon = 3
        me.aim = 0f
        players[me.id] = me
        placeFree(me, W / 2f, H / 2f, 0f)
    }

    private fun botName(): String {
        val names = arrayOf("Егерь", "Тень", "Клещ", "Барсук", "Ворон", "Сойка", "Мох", "Пень", "Лось", "Филин")
        return names[rnd.nextInt(names.size)] + (100 + rnd.nextInt(899))
    }

    private fun spawnBot() {
        val b = P("bot_" + nextObjId++ + "_" + rnd.nextInt(9999), botName(), rnd.nextInt(SKINS.size), W / 2f, H / 2f, true, false)
        b.weapon = if (rnd.nextInt(100) < 22) 4 else 3
        b.ammoT = 99_999
        b.ammoR = 99_999
        b.meds = 2
        b.coins = rnd.nextInt(120)
        b.kills = rnd.nextInt(4)
        placeFree(b, W / 2f, H / 2f, 380f)
        b.targetX = b.x
        b.targetY = b.y
        b.lastX = b.x
        b.lastY = b.y
        players[b.id] = b
    }

    private fun placeFree(p: P, cx: Float, cy: Float, minDist: Float) {
        var tries = 0
        while (tries < 60) {
            tries++
            val a = rnd.nextFloat() * Math.PI.toFloat() * 2f
            val r = minDist + 120f + rnd.nextFloat() * 700f
            val x = (cx + cos(a) * r).coerceIn(120f, W - 120f)
            val y = (cy + sin(a) * r).coerceIn(120f, H - 120f)
            if (collides(x, y, 34f)) continue
            p.x = x
            p.y = y
            p.tx = x
            p.ty = y
            p.lastX = x
            p.lastY = y
            return
        }
        p.x = (cx + 170f).coerceIn(120f, W - 120f)
        p.y = cy
        p.tx = p.x
        p.ty = p.y
    }

    private fun collides(x: Float, y: Float, pad: Float): Boolean {
        for (e in objects) {
            if (e.dead || !solid(e.type)) continue
            val dx = e.x - x
            val dy = e.y - y
            val rr = e.r + pad
            if (dx * dx + dy * dy < rr * rr) return true
        }
        return false
    }

    fun respawnMe() {
        me.alive = true
        me.hp = 100f
        me.deadT = 0f
        me.kills = 0
        me.weapon = 3
        me.ammoT = 30
        me.ammoR = 1
        me.meds = 1
        me.wood = 0
        me.stone = 0
        me.angryReset()
        placeFree(me, W / 2f, H / 2f, 0f)
    }

    private fun P.angryReset() {
        attackerId = ""
        attackerName = ""
        healCd = 0f
        fireCd = 0f
        hurt = 0f
    }

    fun updateRemote(
        id: String,
        name: String,
        skin: Int,
        x: Float,
        y: Float,
        hp: Float,
        aim: Float,
        weapon: Int,
        kills: Int,
        coins: Int
    ) {
        if (id == me.id) return
        var p = players[id]
        if (p == null) {
            p = P(id, name, skin, x, y, false, false)
            p.tx = x
            p.ty = y
            players[id] = p
        }
        if (p.isMe) return
        p.name = name
        p.skin = skin
        p.tx = x
        p.ty = y
        p.hp = hp
        p.aim = aim
        p.weapon = weapon
        p.kills = kills
        p.coins = coins
        p.lastSeen = System.currentTimeMillis()
        p.alive = hp > 0f
        if (p.alive) p.deadT = 0f
    }

    fun removeObject(id: Int) {
        for (e in objects) {
            if (e.id == id) {
                e.dead = true
                e.flash = 0f
                break
            }
        }
    }

    fun addRemoteWall(id: Int, x: Float, y: Float, hp: Float) {
        for (e in objects) if (e.id == id) return
        val e = Ent(id, T_WALL, x, y, hp.coerceAtLeast(1f), 240f, 32f)
        objects.add(e)
    }

    fun remoteShotFx(p: P) {
        if (!p.alive) return
        val mx = p.x + cos(p.aim) * 28f
        val my = p.y + sin(p.aim) * 28f
        repeat(3) {
            particles.add(
                Fx(
                    mx, my,
                    cos(p.aim) * (60f + rnd.nextFloat() * 90f) + (rnd.nextFloat() - 0.5f) * 60f,
                    sin(p.aim) * (60f + rnd.nextFloat() * 90f) + (rnd.nextFloat() - 0.5f) * 60f,
                    0.14f, 0.14f, 2.5f, 0xFFFFC65A.toInt()
                )
            )
        }
    }

    // --------------------------------------------------------------- update

    fun update(dt: Float, moveX: Float, moveY: Float, firing: Boolean) {
        time += dt
        shake = max(0f, shake - dt * 36f)

        for (p in players.values) {
            p.fireCd = max(0f, p.fireCd - dt)
            p.healCd = max(0f, p.healCd - dt)
            p.hurt = max(0f, p.hurt - dt)
            p.flash = max(0f, p.flash - dt)
        }

        if (me.alive) {
            val len = sqrt(moveX * moveX + moveY * moveY)
            if (len > 0.08f) {
                val nx = moveX / len
                val ny = moveY / len
                moveP(me, nx, ny, dt * 250f)
                me.walk += dt * 9f
            }
            if (firing) fire(me)
        } else {
            me.deadT += dt
        }

        for (p in players.values.toList()) {
            if (p.isBot) updateBot(p, dt)
        }

        val pi = players.entries.iterator()
        while (pi.hasNext()) {
            val p = pi.next().value
            if (p.isMe || p.isBot) continue
            p.x += (p.tx - p.x) * min(1f, dt * 12f)
            p.y += (p.ty - p.y) * min(1f, dt * 12f)
            p.angle = lerpAngle(p.angle, p.aim, min(1f, dt * 14f))
            p.walk += dt * 3f
            if (!p.alive) p.deadT += dt
            if (!p.alive && p.deadT > 8f) {
                pi.remove()
            } else if (System.currentTimeMillis() - p.lastSeen > 15_000L) {
                pi.remove()
            }
        }

        updateBullets(dt)

        for (e in objects) {
            e.flash = max(0f, e.flash - dt)
            if (e.dead) e.grow = min(1f, e.grow + dt * 3f)
        }

        val pi = particles.iterator()
        while (pi.hasNext()) {
            val f = pi.next()
            f.life -= dt
            f.x += f.vx * dt
            f.y += f.vy * dt
            f.vx *= 0.94f
            f.vy *= 0.94f
            if (f.life <= 0f) pi.remove()
        }

        val ti = texts.iterator()
        while (ti.hasNext()) {
            val t = ti.next()
            t.life -= dt
            t.y -= dt * 34f
            if (t.life <= 0f) ti.remove()
        }
    }

    private fun lerpAngle(a: Float, b: Float, t: Float): Float {
        var d = (b - a) % (Math.PI.toFloat() * 2f)
        if (d > Math.PI) d -= (Math.PI * 2).toFloat()
        if (d < -Math.PI) d += (Math.PI * 2).toFloat()
        return a + d * t
    }

    private fun moveP(p: P, nx: Float, ny: Float, amount: Float) {
        p.x += nx * amount
        p.y += ny * amount
        resolve(p)
    }

    private fun resolve(p: P) {
        val pr = 22f
        for (e in objects) {
            if (e.dead || !solid(e.type)) continue
            val dx = p.x - e.x
            val dy = p.y - e.y
            val rr = pr + e.r
            val d2 = dx * dx + dy * dy
            if (d2 < rr * rr && d2 > 0.0001f) {
                val d = sqrt(d2)
                val push = rr - d
                p.x += dx / d * push
                p.y += dy / d * push
            }
        }
        p.x = p.x.coerceIn(60f, W - 60f)
        p.y = p.y.coerceIn(60f, H - 60f)
    }

    private fun updateBullets(dt: Float) {
        val it = bullets.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.life -= dt
            var remove = false
            if (b.life <= 0f) remove = true

            if (!remove) {
                val speed = sqrt(b.vx * b.vx + b.vy * b.vy)
                val steps = max(1, (speed * dt / 20f).toInt() + 1)
                val stepDt = dt / steps
                for (s in 0 until steps) {
                    b.x += b.vx * stepDt
                    b.y += b.vy * stepDt

                    // объекты
                    var hitObj: Ent? = null
                    for (e in objects) {
                        if (e.dead || !blocksBullets(e.type)) continue
                        val dx = e.x - b.x
                        val dy = e.y - b.y
                        val rr = e.r + 5f
                        if (dx * dx + dy * dy < rr * rr) {
                            hitObj = e
                            break
                        }
                    }
                    if (hitObj != null) {
                        val owner = players[b.owner]
                        if (b.rocket) {
                            explode(b.x, b.y, owner, if (owner != null) b.dmg else 48f)
                        } else {
                            damageEnt(hitObj, b.dmg, owner)
                        }
                        remove = true
                        break
                    }

                    // игроки
                    var hitP: P? = null
                    for (p in players.values) {
                        if (p.id == b.owner || !p.alive) continue
                        if (!p.isMe && !p.isBot) {
                            // в чужих не стреляем локально — им отправим урон
                            val owner = players[b.owner]
                            if (owner?.isMe != true) continue
                        }
                        val dx = p.x - b.x
                        val dy = p.y - b.y
                        if (dx * dx + dy * dy < (22f + 6f) * (22f + 6f)) {
                            hitP = p
                            break
                        }
                    }
                    if (hitP != null) {
                        val owner = players[b.owner]
                        if (b.rocket) {
                            explode(b.x, b.y, owner, b.dmg)
                        } else if (!hitP.isMe && !hitP.isBot) {
                            events.remoteDamage(hitP.id, b.dmg * (if (owner?.isBot == true) 0.55f else 1f), owner?.name ?: "?")
                        } else {
                            damagePlayer(hitP, b.dmg * (if (owner?.isBot == true) 0.55f else 1f), owner)
                        }
                        remove = true
                        break
                    }

                    if (b.x < 0f || b.y < 0f || b.x > W || b.y > H) {
                        remove = true
                        break
                    }
                }
            }
            if (remove) it.remove()
        }
    }

    private fun damageEnt(e: Ent, dmg: Float, by: P?) {
        if (e.dead) return
        e.hp -= dmg
        e.flash = 0.12f
        if (e.hp <= 0f) destroyEnt(e, by)
    }

    private fun destroyEnt(e: Ent, by: P?) {
        if (e.dead) return
        e.dead = true
        when (e.type) {
            T_TREE -> burst(e.x, e.y, 0xFF7A5230.toInt(), 12)
            T_ROCK -> burst(e.x, e.y, 0xFF9AA09A.toInt(), 12)
            T_CRATE -> burst(e.x, e.y, 0xFFB98A55.toInt(), 14)
            T_BARREL -> burst(e.x, e.y, 0xFFD84444.toInt(), 16)
            T_WALL, T_BENCH -> burst(e.x, e.y, 0xFFB98A55.toInt(), 14)
            else -> {}
        }
        events.objectGone(e.id)

        if (by == null) {
            if (e.type == T_BARREL) explode(e.x, e.y, null, 42f)
            return
        }

        when (e.type) {
            T_TREE -> {
                by.wood += 8
                if (by.isMe) pop(e.x, e.y - 20f, "+8 дерева", 0xFFC9E88A.toInt())
            }
            T_ROCK -> {
                by.stone += 8
                if (by.isMe) pop(e.x, e.y - 20f, "+8 камня", 0xFFBEC5C0.toInt())
            }
            T_CRATE -> {
                val gain = 18 + rnd.nextInt(30)
                by.coins += gain
                by.kills += 0
                events.coinsEarned(by, gain)
                if (by.isMe) pop(e.x, e.y - 20f, "+$gain монет", 0xFFFFD95C.toInt())
                when {
                    rnd.nextFloat() < 0.5f -> {
                        by.ammoT += 20
                        if (by.isMe) pop(e.x, e.y - 44f, "+20 патронов", 0xFFFFC65A.toInt())
                    }
                    rnd.nextFloat() < 0.4f -> {
                        by.meds++
                        if (by.isMe) pop(e.x, e.y - 44f, "+1 аптечка", 0xFF8CE0A0.toInt())
                    }
                    rnd.nextFloat() < 0.3f -> {
                        by.ammoR++
                        if (by.isMe) pop(e.x, e.y - 44f, "+1 ракета", 0xFFFFB06A.toInt())
                    }
                }
            }
            T_BARREL -> {
                explode(e.x, e.y, by, 42f)
            }
            T_BENCH -> {
                by.coins += 10
                events.coinsEarned(by, 10)
            }
        }
    }

    private fun explode(x: Float, y: Float, owner: P?, dmg: Float = 52f) {
        shake = max(shake, 10f)
        burst(x, y, 0xFFFFB347.toInt(), 26)
        burst(x, y, 0xFFFF6A3D.toInt(), 14)
        for (e in objects) {
            if (e.dead) continue
            val dx = e.x - x
            val dy = e.y - y
            if (dx * dx + dy * dy < (120f + e.r) * (120f + e.r)) {
                damageEnt(e, dmg * 0.75f, owner)
            }
        }
        for (p in players.values) {
            if (!p.alive) continue
            val dx = p.x - x
            val dy = p.y - y
            val d = sqrt(dx * dx + dy * dy)
            if (d < 130f + 22f) {
                val falloff = 1f - (d / 152f) * 0.55f
                when {
                    p.isMe || p.isBot -> damagePlayer(p, dmg * falloff, owner)
                    owner != null && owner.isMe -> events.remoteDamage(p.id, dmg * falloff, owner.name)
                }
            }
        }
    }

    private fun burst(x: Float, y: Float, color: Int, count: Int) {
        if (particles.size > 320) return
        repeat(count) {
            val a = rnd.nextFloat() * Math.PI.toFloat() * 2f
            val sp = 40f + rnd.nextFloat() * 240f
            particles.add(
                Fx(
                    x, y,
                    cos(a) * sp, sin(a) * sp,
                    0.25f + rnd.nextFloat() * 0.5f, 0.75f,
                    2f + rnd.nextFloat() * 4f, color
                )
            )
        }
    }

    private fun pop(x: Float, y: Float, text: String, color: Int) {
        if (texts.size > 30) texts.removeAt(0)
        texts.add(Pop(x, y, text, color, 0.95f))
    }

    // ------------------------------------------------------------- стрельба

    fun fire(p: P) {
        if (!p.alive) return
        val wi = p.weapon.coerceIn(0, WEAPONS.size - 1)
        val w = WEAPONS[wi]
        when (w.kind) {
            0 -> {
                if (p.fireCd > 0f) return
                p.fireCd = w.rate
                events.shot(p)
                melee(p, w)
            }
            1 -> {
                if (p.fireCd > 0f || p.ammoT <= 0) return
                p.fireCd = w.rate
                p.ammoT--
                spawnBullet(p, w, 0.055f)
                events.shot(p)
            }
            2 -> {
                if (p.fireCd > 0f || p.ammoR <= 0) return
                p.fireCd = w.rate
                p.ammoR--
                spawnRocket(p, w)
                events.shot(p)
            }
            3 -> {
                if (p.healCd > 0f) return
                if (p.meds <= 0) return
                if (p.hp >= 100f) return
                p.healCd = w.rate
                p.meds--
                p.hp = min(100f, p.hp + 45f)
                if (p.isMe) pop(p.x, p.y - 44f, "+45 HP", 0xFF8CE0A0.toInt())
                events.shot(p)
            }
        }
    }

    private fun spawnBullet(p: P, w: Wpn, spread: Float) {
        val a = p.aim + (rnd.nextFloat() - 0.5f) * spread * 2f
        val sx = p.x + cos(a) * 30f
        val sy = p.y + sin(a) * 30f
        bullets.add(Bullet(sx, sy, cos(a) * w.speed, sin(a) * w.speed, w.dmg, p.id, false, 1.1f))
        muzzle(p)
    }

    private fun spawnRocket(p: P, w: Wpn) {
        val a = p.aim
        val sx = p.x + cos(a) * 32f
        val sy = p.y + sin(a) * 32f
        bullets.add(Bullet(sx, sy, cos(a) * w.speed, sin(a) * w.speed, w.dmg, p.id, true, 1.6f))
        muzzle(p)
        shake = max(shake, 3f)
    }

    private fun muzzle(p: P) {
        repeat(2) {
            particles.add(
                Fx(
                    p.x + cos(p.aim) * 34f,
                    p.y + sin(p.aim) * 34f,
                    cos(p.aim) * 90f + (rnd.nextFloat() - 0.5f) * 80f,
                    sin(p.aim) * 90f + (rnd.nextFloat() - 0.5f) * 80f,
                    0.12f, 0.12f, 2.4f, 0xFFFFC65A.toInt()
                )
            )
        }
    }

    private fun melee(p: P, w: Wpn) {
        var bestE: Ent? = null
        var bestEd = Float.MAX_VALUE
        for (e in objects) {
            if (e.dead || !destructible(e.type)) continue
            val dx = e.x - p.x
            val dy = e.y - p.y
            val d = sqrt(dx * dx + dy * dy)
            if (d > w.range + e.r) continue
            if (abs(angleDiff(atan2(dy, dx), p.aim)) > 1.05f) continue
            if (d < bestEd) {
                bestEd = d
                bestE = e
            }
        }
        var bestP: P? = null
        var bestPd = Float.MAX_VALUE
        for (q in players.values) {
            if (q === p || !q.alive) continue
            if (!q.isMe && !q.isBot) {
                if (!p.isMe) continue
            }
            val dx = q.x - p.x
            val dy = q.y - p.y
            val d = sqrt(dx * dx + dy * dy)
            if (d > w.range + 26f) continue
            if (abs(angleDiff(atan2(dy, dx), p.aim)) > 1.05f) continue
            if (d < bestPd) {
                bestPd = d
                bestP = q
            }
        }

        if (bestP != null && (bestE == null || bestPd < bestEd)) {
            if (!bestP.isMe && !bestP.isBot) {
                events.remoteDamage(bestP.id, w.dmg, p.name)
            } else {
                damagePlayer(bestP, w.dmg, p)
            }
            burst(bestP.x, bestP.y, 0xFFD84444.toInt(), 5)
            return
        }

        val e = bestE ?: return
        val mult = harvestMultiplier(p, e)
        var dmg = w.dmg * mult
        if (e.type == T_CRATE) dmg *= 1.5f
        damageEnt(e, dmg, p)

        if (p.isMe) {
            when {
                e.type == T_TREE && p.weapon == 1 -> {
                    p.wood += 2
                    pop(e.x, e.y - 18f, "+2", 0xFFC9E88A.toInt())
                }
                e.type == T_ROCK && p.weapon == 2 -> {
                    p.stone += 2
                    pop(e.x, e.y - 18f, "+2", 0xFFBEC5C0.toInt())
                }
                (e.type == T_TREE || e.type == T_ROCK) && p.weapon == 0 -> {
                    if (e.type == T_TREE) p.wood += 1 else p.stone += 1
                }
            }
        }
        burst(e.x, e.y, when (e.type) {
            T_TREE -> 0xFF7A5230.toInt()
            T_ROCK -> 0xFF9AA09A.toInt()
            else -> 0xFFB98A55.toInt()
        }, 4)
    }

    private fun harvestMultiplier(p: P, e: Ent): Float = when {
        e.type == T_TREE && p.weapon == 1 -> 2.2f
        e.type == T_ROCK && p.weapon == 2 -> 2.2f
        e.type == T_TREE && p.weapon == 2 -> 0.35f
        e.type == T_ROCK && p.weapon == 1 -> 0.45f
        p.weapon == 0 -> 0.55f
        else -> 1f
    }

    private fun damagePlayer(p: P, dmg: Float, attacker: P?) {
        if (!p.alive) return
        p.hp -= dmg
        p.hurt = 0.22f
        p.flash = 0.14f
        if (attacker != null) {
            p.attackerId = attacker.id
            p.attackerName = attacker.name
        }
        pop(p.x + (rnd.nextFloat() - 0.5f) * 26f, p.y - 46f, "-" + dmg.toInt(), 0xFFFF6655.toInt())
        burst(p.x, p.y, 0xFFD84444.toInt(), 5)
        if (p.hp <= 0f) {
            p.hp = 0f
            p.alive = false
            p.deadT = 0f
            burst(p.x, p.y, 0xFF8B2D2D.toInt(), 14)
            events.playerDied(p, attacker, p.attackerId, p.attackerName.ifEmpty { "лес" })
        }
    }

    // ------------------------------------------------------------------ боты

    private fun updateBot(b: P, dt: Float) {
        if (!b.alive) {
            b.deadT += dt
            b.fireCd = max(0f, b.fireCd - dt)
            if (b.deadT > 8f) {
                b.alive = true
                b.hp = 100f
                b.deadT = 0f
                b.weapon = if (rnd.nextInt(100) < 22) 4 else 3
                b.kills = b.kills
                placeFree(b, W / 2f, H / 2f, 300f)
            }
            return
        }

        b.botCd = max(0f, b.botCd - dt)
        b.healCd = max(0f, b.healCd - dt)

        if (b.hp < 42f && b.meds > 0 && b.healCd <= 0f) {
            b.hp = min(100f, b.hp + 35f)
            b.meds--
            b.healCd = 4f
        }

        var target: P? = null
        var best = 470f
        for (q in players.values) {
            if (q === b || !q.alive) continue
            if (!q.isMe && !q.isBot) continue
            val dx = q.x - b.x
            val dy = q.y - b.y
            val d = sqrt(dx * dx + dy * dy)
            if (d < best) {
                best = d
                target = q
            }
        }

        if (target != null && best < 430f) {
            val aimAt = atan2(target.y - b.y, target.x - b.x) + (rnd.nextFloat() - 0.5f) * 0.15f
            b.aim = aimAt
            val advance = when {
                best > 260f -> 1f
                best < 130f -> -0.7f
                else -> 0.25f
            }
            val side = if (b.stuck > 0.5f) 0f else 0.6f
            b.mx = cos(aimAt) * advance + cos(aimAt + 1.5708f) * side
            b.my = sin(aimAt) * advance + sin(aimAt + 1.5708f) * side
            b.weapon = if (b.weapon == 4) 4 else 3
            if (b.botCd <= 0f) {
                b.botCd = 0.26f + rnd.nextFloat() * 0.3f
                fire(b)
            }
        } else {
            b.think -= dt
            if (b.think <= 0f) {
                b.think = 2.5f + rnd.nextFloat() * 4f
                // иногда идём к ближайшему дереву/камню
                var node: Ent? = null
                var nd = 380f
                for (e in objects) {
                    if (e.dead) continue
                    if (e.type != T_TREE && e.type != T_ROCK && e.type != T_CRATE) continue
                    val dx = e.x - b.x
                    val dy = e.y - b.y
                    val d = sqrt(dx * dx + dy * dy)
                    if (d < nd) {
                        nd = d
                        node = e
                    }
                }
                if (node != null && rnd.nextFloat() < 0.55f) {
                    b.targetX = node.x
                    b.targetY = node.y
                } else {
                    b.targetX = 120f + rnd.nextFloat() * (W - 240f)
                    b.targetY = 120f + rnd.nextFloat() * (H - 240f)
                }
            }
            val dx = b.targetX - b.x
            val dy = b.targetY - b.y
            val d = sqrt(dx * dx + dy * dy)
            if (d < 26f) {
                b.think = 0f
                b.mx = 0f
                b.my = 0f
            } else {
                val a = atan2(dy, dx)
                b.aim = a
                b.mx = cos(a)
                b.my = sin(a)
            }
            // боты тоже ломают ящики и стреляют по деревьям
            if (b.botCd <= 0f && rnd.nextFloat() < 0.012f) {
                b.botCd = 0.5f
                fire(b)
            }
        }

        if (b.mx != 0f || b.my != 0f) {
            moveP(b, b.mx, b.my, dt * 225f)
            b.walk += dt * 8f
        }

        val md = (b.x - b.lastX) * (b.x - b.lastX) + (b.y - b.lastY) * (b.y - b.lastY)
        if (md < 1f) b.stuck += dt else b.stuck = 0f
        if (b.stuck > 0.9f) {
            b.targetX = 120f + rnd.nextFloat() * (W - 240f)
            b.targetY = 120f + rnd.nextFloat() * (H - 240f)
            b.think = 2f
            b.stuck = 0f
        }
        b.lastX = b.x
        b.lastY = b.y
    }

    // ------------------------------------------------------------- постройки

    fun nearestBench(): Ent? {
        var best: Ent? = null
        var bd = 150f
        for (e in objects) {
            if (e.dead || e.type != T_BENCH) continue
            val dx = e.x - me.x
            val dy = e.y - me.y
            val d = sqrt(dx * dx + dy * dy)
            if (d < bd) {
                bd = d
                best = e
            }
        }
        return best
    }

    fun wallAction(): String {
        if (me.wood < 12) return "Нужно 12 дерева"
        val bx = me.x + cos(me.aim) * 82f
        val by = me.y + sin(me.aim) * 82f
        if (bx < 70f || by < 70f || bx > W - 70f || by > H - 70f) return "Слишком близко к краю"
        for (e in objects) {
            if (e.dead) continue
            val dx = e.x - bx
            val dy = e.y - by
            if (dx * dx + dy * dy < (e.r + 36f) * (e.r + 36f)) return "Здесь уже что-то стоит"
        }
        me.wood -= 12
        val e = Ent(builtId++, T_WALL, bx, by, 240f, 240f, 32f)
        objects.add(e)
        events.wallBuilt(e)
        pop(bx, by - 20f, "Стена", 0xFFC9E88A.toInt())
        return ""
    }

    fun benchAction(): String {
        val bench = nearestBench()
        if (bench != null) {
            if (me.wood < 15 || me.stone < 10) return "Для ракет: 15 дерева, 10 камня"
            me.wood -= 15
            me.stone -= 10
            me.ammoR += 2
            pop(me.x, me.y - 52f, "+2 ракеты", 0xFFFFB06A.toInt())
            return "Снаряды готовы"
        }
        if (me.wood < 25 || me.stone < 15) return "Для верстака: 25 дерева, 15 камня"
        val bx = me.x + cos(me.aim) * 86f
        val by = me.y + sin(me.aim) * 86f
        if (bx < 70f || by < 70f || bx > W - 70f || by > H - 70f) return "Слишком близко к краю"
        for (e in objects) {
            if (e.dead) continue
            val dx = e.x - bx
            val dy = e.y - by
            if (dx * dx + dy * dy < (e.r + 38f) * (e.r + 38f)) return "Здесь уже что-то стоит"
        }
        me.wood -= 25
        me.stone -= 15
        val e = Ent(builtId++, T_BENCH, bx, by, 200f, 200f, 34f)
        objects.add(e)
        events.wallBuilt(e)
        pop(bx, by - 24f, "Верстак", 0xFFFFD95C.toInt())
        return "Верстак построен"
    }

    // --------------------------------------------------------- вспомогательное

    fun aimAssist(base: Float, range: Float): Float {
        var best = base
        var bestScore = 0.34f
        var bestPlayer = Float.MAX_VALUE
        for (q in players.values) {
            if (q.isMe || !q.alive) continue
            val dx = q.x - me.x
            val dy = q.y - me.y
            val d = sqrt(dx * dx + dy * dy)
            if (d > range) continue
            val a = atan2(dy, dx)
            val diff = abs(angleDiff(a, base))
            if (diff < bestScore) {
                bestScore = diff
                best = a
                bestPlayer = d
            }
        }
        if (bestPlayer < Float.MAX_VALUE) return best
        for (e in objects) {
            if (e.dead) continue
            if (e.type != T_TREE && e.type != T_ROCK && e.type != T_CRATE) continue
            val dx = e.x - me.x
            val dy = e.y - me.y
            val d = sqrt(dx * dx + dy * dy)
            if (d > range * 0.8f) continue
            val a = atan2(dy, dx)
            val diff = abs(angleDiff(a, base))
            if (diff < bestScore) {
                bestScore = diff
                best = a
            }
        }
        return best
    }

    fun angleDiff(a: Float, b: Float): Float {
        var d = (a - b) % (Math.PI.toFloat() * 2f)
        if (d > Math.PI) d -= (Math.PI * 2).toFloat()
        if (d < -Math.PI) d += (Math.PI * 2).toFloat()
        return d
    }
}
