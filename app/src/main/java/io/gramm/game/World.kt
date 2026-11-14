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
    var armor = 0f
    var alive = true
    var aim = 0f
    var angle = 0f
    var weapon = 4
    var weaponSkin = 0
    var toolLevel = 0
    var kills = 0
    var coins = 0
    var wood = 0
    var stone = 0
    var iron = 0
    var sulfur = 0
    var ammoT = 60
    var ammoP = 12
    var ammoR = 2
    var grenades = 2
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
    val grenade: Boolean,
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
        const val W = 14000f
        const val H = 14000f

        const val T_TREE = 0
        const val T_ROCK = 1
        const val T_BUSH = 2
        const val T_CRATE = 3
        const val T_BARREL = 4
        const val T_WALL = 5
        const val T_BENCH = 6
        const val T_PUMPKIN = 7

        const val SLOT_SPEAR = 1
        const val SLOT_AXE = 2
        const val SLOT_PICK = 3
        const val SLOT_GUN = 4
        const val SLOT_REVOLVER = 5
        const val SLOT_ROCKET = 6
        const val SLOT_TNT = 7
        const val SLOT_MED = 8

        data class Wpn(val name: String, val kind: Int, val dmg: Float, val rate: Float, val speed: Float, val range: Float)

        val WEAPONS = listOf(
            Wpn("Кулаки", 0, 7f, 0.38f, 0f, 68f),
            Wpn("Копьё", 0, 24f, 0.55f, 0f, 112f),
            Wpn("Топор", 0, 18f, 0.50f, 0f, 84f),
            Wpn("Кирка", 0, 14f, 0.50f, 0f, 84f),
            Wpn("Томпсон", 1, 13f, 0.11f, 1180f, 720f),
            Wpn("Револьвер", 1, 44f, 0.55f, 1600f, 760f),
            Wpn("Базука", 2, 55f, 1.55f, 660f, 900f),
            Wpn("Взрывчатка", 4, 80f, 1.35f, 500f, 560f),
            Wpn("Аптечка", 3, 0f, 1.10f, 0f, 0f),
            Wpn("Дуло", 6, 85f, 0.80f, 520f, 430f),
            Wpn("Лук", 5, 22f, 0.95f, 300f, 180f)
        )
    }

    interface Events {
        fun remoteDamage(targetId: String, dmg: Float, byName: String)
        fun objectGone(id: Int)
        fun built(e: Ent)
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
    private fun pumpkinHp() = 34f

    private fun radiusOf(t: Int) = when (t) {
        T_TREE -> 34f
        T_ROCK -> 31f
        T_BUSH -> 25f
        T_CRATE -> 21f
        T_BARREL -> 23f
        T_WALL -> 34f
        T_BENCH -> 34f
        T_PUMPKIN -> 27f
        else -> 24f
    }

    private fun hpOf(t: Int) = when (t) {
        T_TREE -> treeHp()
        T_ROCK -> rockHp()
        T_CRATE -> crateHp()
        T_BARREL -> barrelHp()
        T_WALL -> 240f
        T_BENCH -> 200f
        T_PUMPKIN -> pumpkinHp()
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
        while (made < 90 && guard < 2500) {
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
        while (crates < 18 && guard < 2000) {
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
        while (barrels < 12 && guard < 2000) {
            guard++
            val x = 120f + rnd.nextFloat() * (W - 240f)
            val y = 120f + rnd.nextFloat() * (H - 240f)
            if (overlaps(x, y, 28f)) continue
            add(T_BARREL, x, y)
            barrels++
        }

        // хэллоуинские тыквы по всему лесу
        var pumpkins = 0
        guard = 0
        while (pumpkins < 12 && guard < 3000) {
            guard++
            val x = 100f + rnd.nextFloat() * (W - 200f)
            val y = 100f + rnd.nextFloat() * (H - 200f)
            if (overlaps(x, y, 18f)) continue
            add(T_PUMPKIN, x, y)
            pumpkins++
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
            add(T_PUMPKIN, bx - 92f, by - 20f)
            add(T_PUMPKIN, bx + 92f, by + 20f)
        }

        add(T_CRATE, W / 2 + 210f, H / 2 + 60f)
        add(T_CRATE, W / 2 - 170f, H / 2 - 120f)
        add(T_TREE, W / 2 + 280f, H / 2 - 140f)
        add(T_PUMPKIN, W / 2 + 150f, H / 2 + 170f)
        add(T_PUMPKIN, W / 2 - 220f, H / 2 + 40f)
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
        me.weapon = SLOT_GUN
        me.aim = 0f
        players[me.id] = me
        placeFree(me, W / 2f, H / 2f, 0f)
    }

    private fun botName(): String {
        val names = arrayOf("Волк", "Бык", "Енот", "Лось", "Филин", "Медведь", "Олень", "Барсук", "Тигр", "Еж", "Кабан", "Кот")
        return names[rnd.nextInt(names.size)] + (100 + rnd.nextInt(899))
    }

    private fun spawnBot() {
        val b = P("bot_" + nextObjId++ + "_" + rnd.nextInt(9999), botName(), rnd.nextInt(SKINS.size), W / 2f, H / 2f, true, false)
        b.weapon = when {
            rnd.nextInt(100) < 12 -> SLOT_REVOLVER
            rnd.nextInt(100) < 22 -> SLOT_TNT
            rnd.nextInt(100) < 35 -> SLOT_ROCKET
            else -> SLOT_GUN
        }
        b.ammoT = 99_999
        b.ammoP = 99_999
        b.ammoR = 99_999
        b.grenades = 99_999
        b.meds = 3
        b.coins = rnd.nextInt(120)
        b.kills = rnd.nextInt(4)
        b.toolLevel = rnd.nextInt(3)
        b.weaponSkin = rnd.nextInt(WEAPON_SKINS.size)
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
        me.armor = 0f
        me.deadT = 0f
        me.kills = 0
        me.weapon = SLOT_GUN
        me.ammoT = 30
        me.ammoP = 12
        me.ammoR = 1
        me.grenades = 1
        me.meds = 1
        me.wood = 0
        me.stone = 0
        me.iron = 0
        me.sulfur = 0
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
        coins: Int,
        iron: Int = 0,
        sulfur: Int = 0,
        armor: Float = 0f,
        toolLevel: Int = 0,
        weaponSkin: Int = 0,
        grenades: Int = 0
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
        p.iron = iron
        p.sulfur = sulfur
        p.armor = armor
        p.toolLevel = toolLevel
        p.weaponSkin = weaponSkin
        p.grenades = grenades
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

    fun addRemoteBuild(id: Int, x: Float, y: Float, hp: Float, type: Int) {
        for (e in objects) if (e.id == id) return
        val t = if (type in 0..7) type else T_WALL
        val e = Ent(id, t, x, y, hp.coerceAtLeast(1f), hpOf(t), radiusOf(t))
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

        val pli = players.entries.iterator()
        while (pli.hasNext()) {
            val p = pli.next().value
            if (p.isMe || p.isBot) continue
            p.x += (p.tx - p.x) * min(1f, dt * 12f)
            p.y += (p.ty - p.y) * min(1f, dt * 12f)
            p.angle = lerpAngle(p.angle, p.aim, min(1f, dt * 14f))
            p.walk += dt * 3f
            if (!p.alive) p.deadT += dt
            if (!p.alive && p.deadT > 8f) {
                pli.remove()
            } else if (System.currentTimeMillis() - p.lastSeen > 15_000L) {
                pli.remove()
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
            var boom = false
            if (b.life <= 0f) {
                remove = true
                boom = b.rocket || b.grenade
            }

            if (!remove) {
                val speed = sqrt(b.vx * b.vx + b.vy * b.vy)
                val steps = max(1, (speed * dt / 20f).toInt() + 1)
                val stepDt = dt / steps
                for (s in 0 until steps) {
                    b.x += b.vx * stepDt
                    b.y += b.vy * stepDt

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
                        if (b.rocket || b.grenade) {
                            explode(b.x, b.y, owner, b.dmg)
                        } else {
                            damageEnt(hitObj, b.dmg, owner)
                        }
                        remove = true
                        break
                    }

                    var hitP: P? = null
                    for (p in players.values) {
                        if (p.id == b.owner || !p.alive) continue
                        if (!p.isMe && !p.isBot) {
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
                        if (b.rocket || b.grenade) {
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
            if (remove) {
                if (boom) explode(b.x, b.y, players[b.owner], b.dmg)
                it.remove()
            }
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
            T_PUMPKIN -> burst(e.x, e.y, 0xFFFF8A2A.toInt(), 16)
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
                if (rnd.nextFloat() < 0.35f) {
                    by.iron += 1
                    if (by.isMe) pop(e.x, e.y - 40f, "+1 железо", 0xFFD8DEE3.toInt())
                }
                if (rnd.nextFloat() < 0.22f) {
                    by.sulfur += 1
                    if (by.isMe) pop(e.x, e.y - 58f, "+1 сера", 0xFFE4E05B.toInt())
                }
            }
            T_CRATE -> {
                val gain = 18 + rnd.nextInt(30)
                by.coins += gain
                events.coinsEarned(by, gain)
                if (by.isMe) pop(e.x, e.y - 20f, "+$gain монет", 0xFFFFD95C.toInt())
                if (rnd.nextFloat() < 0.55f) {
                    by.ammoT += 20
                    if (by.isMe) pop(e.x, e.y - 40f, "+20 патронов", 0xFFFFC65A.toInt())
                }
                if (rnd.nextFloat() < 0.35f) {
                    by.ammoP += 6
                    if (by.isMe) pop(e.x, e.y - 58f, "+6 револьвер", 0xFFFFB27A.toInt())
                }
                if (rnd.nextFloat() < 0.30f) {
                    by.meds++
                    if (by.isMe) pop(e.x, e.y - 76f, "+1 аптечка", 0xFF8CE0A0.toInt())
                }
                if (rnd.nextFloat() < 0.24f) {
                    by.ammoR++
                    if (by.isMe) pop(e.x, e.y - 94f, "+1 ракета", 0xFFFFB06A.toInt())
                }
                if (rnd.nextFloat() < 0.25f) {
                    by.grenades++
                    if (by.isMe) pop(e.x, e.y - 112f, "+1 взрывчатка", 0xFFFF7A3C.toInt())
                }
                if (rnd.nextFloat() < 0.20f) {
                    by.iron += 1
                    if (by.isMe) pop(e.x, e.y - 130f, "+1 железо", 0xFFD8DEE3.toInt())
                }
                if (rnd.nextFloat() < 0.18f) {
                    by.sulfur += 1
                    if (by.isMe) pop(e.x, e.y - 148f, "+1 сера", 0xFFE4E05B.toInt())
                }
            }
            T_PUMPKIN -> {
                val gain = 8 + rnd.nextInt(14)
                by.coins += gain
                events.coinsEarned(by, gain)
                if (by.isMe) pop(e.x, e.y - 20f, "+$gain монет", 0xFFFFB347.toInt())
                if (rnd.nextFloat() < 0.45f) {
                    by.sulfur += 1
                    if (by.isMe) pop(e.x, e.y - 40f, "+1 сера", 0xFFE4E05B.toInt())
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

    private fun toolDamage(p: P, slot: Int): Float {
        if (slot != SLOT_SPEAR && slot != SLOT_AXE && slot != SLOT_PICK) return 1f
        return 1f + p.toolLevel * 0.35f
    }

    fun fire(p: P) {
        if (!p.alive) return
        val wi = p.weapon.coerceIn(0, WEAPONS.size - 1)
        val w = WEAPONS[wi]
        when (w.kind) {
            0 -> {
                if (p.fireCd > 0f) return
                p.fireCd = w.rate / (1f + p.toolLevel * 0.10f)
                events.shot(p)
                melee(p, w, toolDamage(p, wi))
            }
            1 -> {
                if (p.fireCd > 0f) return
                if (wi == SLOT_REVOLVER) {
                    if (p.ammoP <= 0) return
                    p.ammoP--
                } else {
                    if (p.ammoT <= 0) return
                    p.ammoT--
                }
                p.fireCd = w.rate
                spawnBullet(p, w, if (wi == SLOT_REVOLVER) 0.02f else 0.055f)
                events.shot(p)
            }
            2 -> {
                if (p.fireCd > 0f || p.ammoR <= 0) return
                p.fireCd = w.rate
                p.ammoR--
                spawnRocket(p, w)
                events.shot(p)
            }
            4 -> {
                if (p.fireCd > 0f || p.grenades <= 0) return
                p.fireCd = w.rate
                p.grenades--
                spawnGrenade(p, w)
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
            6 -> {
                // дуло: 5 выстрелов в цель
                if (p.fireCd > 0f) return
                if (p.ammoT <= 0) return
                p.fireCd = w.rate
                p.ammoT--
                val base = p.aim
                for (i in -2..2) {
                    val a = base + i * 0.10f
                    bullets.add(Bullet(p.x + cos(a) * 30f, p.y + sin(a) * 30f, cos(a) * w.speed, sin(a) * w.speed, w.dmg, p.id, false, false, 1.3f))
                }
                muzzle(p)
                events.shot(p)
            }
            5 -> {
                // лук
                if (p.fireCd > 0f || p.ammoT <= 0) return
                p.fireCd = w.rate
                p.ammoT--
                spawnBullet(p, w, 0.9f)
                events.shot(p)
            }
        }
    }

    private fun spawnBullet(p: P, w: Wpn, spread: Float) {
        val a = p.aim + (rnd.nextFloat() - 0.5f) * spread * 2f
        val sx = p.x + cos(a) * 30f
        val sy = p.y + sin(a) * 30f
        bullets.add(Bullet(sx, sy, cos(a) * w.speed, sin(a) * w.speed, w.dmg, p.id, false, false, 1.1f))
        muzzle(p)
    }

    private fun spawnRocket(p: P, w: Wpn) {
        val a = p.aim
        val sx = p.x + cos(a) * 32f
        val sy = p.y + sin(a) * 32f
        bullets.add(Bullet(sx, sy, cos(a) * w.speed, sin(a) * w.speed, w.dmg, p.id, true, false, 1.6f))
        muzzle(p)
        shake = max(shake, 3f)
    }

    private fun spawnGrenade(p: P, w: Wpn) {
        val a = p.aim
        val sx = p.x + cos(a) * 28f
        val sy = p.y + sin(a) * 28f
        bullets.add(Bullet(sx, sy, cos(a) * w.speed, sin(a) * w.speed, w.dmg, p.id, true, true, 0.95f))
        muzzle(p)
        shake = max(shake, 2f)
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

    private fun melee(p: P, w: Wpn, dmgMult: Float) {
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
                events.remoteDamage(bestP.id, w.dmg * dmgMult, p.name)
            } else {
                damagePlayer(bestP, w.dmg * dmgMult, p)
            }
            burst(bestP.x, bestP.y, 0xFFD84444.toInt(), 5)
            return
        }

        val e = bestE ?: return
        val mult = harvestMultiplier(p, e)
        var dmg = w.dmg * dmgMult * mult
        if (e.type == T_CRATE) dmg *= 1.5f
        if (e.type == T_PUMPKIN) dmg *= 1.6f
        damageEnt(e, dmg, p)

        if (p.isMe) {
            when {
                e.type == T_TREE && p.weapon == SLOT_AXE -> {
                    p.wood += 2
                    pop(e.x, e.y - 18f, "+2", 0xFFC9E88A.toInt())
                }
                e.type == T_ROCK && p.weapon == SLOT_PICK -> {
                    p.stone += 2
                    if (rnd.nextFloat() < 0.10f) p.iron += 1
                    pop(e.x, e.y - 18f, "+2", 0xFFBEC5C0.toInt())
                }
                (e.type == T_TREE || e.type == T_ROCK) && p.weapon == 0 -> {
                    if (e.type == T_TREE) p.wood += 1 else p.stone += 1
                }
                e.type == T_PUMPKIN && (p.weapon == SLOT_AXE || p.weapon == SLOT_PICK || p.weapon == SLOT_SPEAR) -> {
                    p.sulfur += 1
                    pop(e.x, e.y - 18f, "+1 сера", 0xFFE4E05B.toInt())
                }
            }
        }
        burst(e.x, e.y, when (e.type) {
            T_TREE -> 0xFF7A5230.toInt()
            T_ROCK -> 0xFF9AA09A.toInt()
            T_PUMPKIN -> 0xFFFF8A2A.toInt()
            else -> 0xFFB98A55.toInt()
        }, 4)
    }

    private fun harvestMultiplier(p: P, e: Ent): Float {
        val bonus = 1f + p.toolLevel * 0.25f
        return when {
            e.type == T_TREE && p.weapon == SLOT_AXE -> 2.2f * bonus
            e.type == T_ROCK && p.weapon == SLOT_PICK -> 2.2f * bonus
            e.type == T_TREE && p.weapon == SLOT_PICK -> 0.35f
            e.type == T_ROCK && p.weapon == SLOT_AXE -> 0.45f
            e.type == T_PUMPKIN && (p.weapon == SLOT_AXE || p.weapon == SLOT_PICK) -> 1.8f * bonus
            p.weapon == SLOT_SPEAR -> 0.9f
            p.weapon == 0 -> 0.55f
            else -> 1f
        }
    }

    private fun damagePlayer(p: P, dmg: Float, attacker: P?) {
        if (!p.alive) return
        var real = dmg
        if (p.armor > 0f) {
            val absorbed = min(p.armor, real * 0.5f)
            p.armor = max(0f, p.armor - absorbed)
            real -= absorbed
            pop(p.x + (rnd.nextFloat() - 0.5f) * 20f, p.y - 62f, "броня", 0xFF9FD8FF.toInt())
        }
        p.hp -= real
        p.hurt = 0.22f
        p.flash = 0.14f
        if (attacker != null) {
            p.attackerId = attacker.id
            p.attackerName = attacker.name
        }
        pop(p.x + (rnd.nextFloat() - 0.5f) * 26f, p.y - 46f, "-" + real.toInt(), 0xFFFF6655.toInt())
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
                b.weapon = if (rnd.nextInt(100) < 22) SLOT_REVOLVER else SLOT_GUN
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
            if (b.weapon == SLOT_TNT || b.weapon == SLOT_ROCKET) b.weapon = SLOT_GUN
            if (b.botCd <= 0f) {
                b.botCd = 0.26f + rnd.nextFloat() * 0.3f
                fire(b)
            }
        } else {
            b.think -= dt
            if (b.think <= 0f) {
                b.think = 2.5f + rnd.nextFloat() * 4f
                var node: Ent? = null
                var nd = 380f
                for (e in objects) {
                    if (e.dead) continue
                    if (e.type != T_TREE && e.type != T_ROCK && e.type != T_CRATE && e.type != T_PUMPKIN) continue
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

    private fun freeSpot(offset: Float, pad: Float): Pair<Float, Float>? {
        val bx = me.x + cos(me.aim) * offset
        val by = me.y + sin(me.aim) * offset
        if (bx < 70f || by < 70f || bx > W - 70f || by > H - 70f) return null
        for (e in objects) {
            if (e.dead) continue
            val dx = e.x - bx
            val dy = e.y - by
            if (dx * dx + dy * dy < (e.r + pad) * (e.r + pad)) return null
        }
        return bx to by
    }

    fun wallAction(): String {
        if (me.wood < 12) return "Нужно 12 дерева"
        val spot = freeSpot(82f, 36f) ?: return "Здесь уже что-то стоит"
        me.wood -= 12
        val e = Ent(builtId++, T_WALL, spot.first, spot.second, 240f, 240f, 32f)
        objects.add(e)
        events.built(e)
        pop(e.x, e.y - 20f, "Стена", 0xFFC9E88A.toInt())
        return ""
    }

    fun pumpkinAction(): String {
        if (me.wood < 10) return "Нужно 10 дерева"
        val spot = freeSpot(80f, 30f) ?: return "Здесь уже что-то стоит"
        me.wood -= 10
        val e = Ent(builtId++, T_PUMPKIN, spot.first, spot.second, pumpkinHp(), pumpkinHp(), 27f)
        objects.add(e)
        events.built(e)
        pop(e.x, e.y - 20f, "Тыква", 0xFFFF8A2A.toInt())
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
        val spot = freeSpot(86f, 38f) ?: return "Здесь уже что-то стоит"
        me.wood -= 25
        me.stone -= 15
        val e = Ent(builtId++, T_BENCH, spot.first, spot.second, 200f, 200f, 34f)
        objects.add(e)
        events.built(e)
        pop(e.x, e.y - 24f, "Верстак", 0xFFFFD95C.toInt())
        return "Верстак построен"
    }

    fun craft(kind: String): String {
        val near = nearestBench() != null
        if (!near) return "Нужно встать рядом с верстаком"
        return when (kind) {
            "armor" -> {
                if (me.armor >= 99f) return "Броня уже надета"
                if (me.iron < 8 || me.wood < 5) return "Броня: 8 железа, 5 дерева"
                me.iron -= 8
                me.wood -= 5
                me.armor = 100f
                pop(me.x, me.y - 50f, "Броня 100", 0xFF9FD8FF.toInt())
                "Броня надета"
            }
            "tnt" -> {
                if (me.sulfur < 4 || me.wood < 8) return "Взрывчатка: 4 серы, 8 дерева"
                me.sulfur -= 4
                me.wood -= 8
                me.grenades += 2
                pop(me.x, me.y - 50f, "+2 взрывчатки", 0xFFFF7A3C.toInt())
                "Взрывчатка готова"
            }
            "ammo" -> {
                if (me.iron < 3 || me.wood < 6) return "Патроны: 3 железа, 6 дерева"
                me.iron -= 3
                me.wood -= 6
                me.ammoP += 12
                pop(me.x, me.y - 50f, "+12 патронов", 0xFFFFB27A.toInt())
                "Револьвер заряжен"
            }
            "rocket" -> {
                if (me.wood < 15 || me.stone < 10) return "Ракеты: 15 дерева, 10 камня"
                me.wood -= 15
                me.stone -= 10
                me.ammoR += 2
                pop(me.x, me.y - 50f, "+2 ракеты", 0xFFFFB06A.toInt())
                "Ракеты готовы"
            }
            "upgrade" -> {
                if (me.toolLevel >= 3) return "Инструменты уже максимальные"
                val ironNeed = when (me.toolLevel) { 0 -> 5; 1 -> 10; else -> 20 }
                val coinNeed = when (me.toolLevel) { 0 -> 200; 1 -> 500; else -> 1200 }
                val woodNeed = 10 + me.toolLevel * 10
                if (me.iron < ironNeed || me.wood < woodNeed || me.coins < coinNeed) {
                    return "Апгрейд: $ironNeed железа, $woodNeed дерева, ${fmt(coinNeed)} монет"
                }
                me.iron -= ironNeed
                me.wood -= woodNeed
                me.coins -= coinNeed
                me.toolLevel++
                pop(me.x, me.y - 50f, "Уровень ${me.toolLevel}", 0xFFB8FF6A.toInt())
                "Инструменты: уровень ${me.toolLevel}"
            }
            else -> "Неизвестный крафт"
        }
    }

    fun upgradeCostText(): String {
        if (me.toolLevel >= 3) return "макс."
        val ironNeed = when (me.toolLevel) { 0 -> 5; 1 -> 10; else -> 20 }
        val coinNeed = when (me.toolLevel) { 0 -> 200; 1 -> 500; else -> 1200 }
        val woodNeed = 10 + me.toolLevel * 10
        return "$ironNeed ж · $woodNeed д · ${fmt(coinNeed)}"
    }

    fun canUpgrade(): Boolean {
        if (me.toolLevel >= 3) return false
        val ironNeed = when (me.toolLevel) { 0 -> 5; 1 -> 10; else -> 20 }
        val coinNeed = when (me.toolLevel) { 0 -> 200; 1 -> 500; else -> 1200 }
        val woodNeed = 10 + me.toolLevel * 10
        return me.iron >= ironNeed && me.wood >= woodNeed && me.coins >= coinNeed
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
            if (e.type != T_TREE && e.type != T_ROCK && e.type != T_CRATE && e.type != T_PUMPKIN) continue
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
