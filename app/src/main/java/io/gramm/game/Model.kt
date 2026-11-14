package io.gramm.game

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * ГРАММ.io — маленькая survival-io игра.
 * Аккаунты и прогресс лежат локально, мир общий через MQTT.
 */

data class Skin(
    val name: String,
    val price: Int,
    val shirt: Int,
    val pants: Int,
    val tone: Int,
    val hat: Int,
    val hatColor: Int,
    val hair: Int
)

// hat: 0 волосы, 1 кепка, 2 шлем, 3 ирокез, 4 шапка+борода, 5 маска,
//      6 корона, 7 капюшон, 8 бандана, 9 скафандр
val SKINS = listOf(
    Skin("Новичок", 0, 0xFF6B8F71.toInt(), 0xFF3B5242.toInt(), 0xFFE8B98A.toInt(), 0, 0, 0xFF5A3B25.toInt()),
    Skin("Лесник", 150, 0xFF4E7A3A.toInt(), 0xFF32492C.toInt(), 0xFFE3B183.toInt(), 1, 0xFF2F4A2A.toInt(), 0xFF4A3320.toInt()),
    Skin("Спецназ", 300, 0xFF3A4148.toInt(), 0xFF282D33.toInt(), 0xFFD9A97C.toInt(), 2, 0xFF5D6B55.toInt(), 0xFF3B2A1D.toInt()),
    Skin("Рокер", 450, 0xFF2B2B33.toInt(), 0xFF1E1E24.toInt(), 0xFFE5B287.toInt(), 3, 0xFFE84D5B.toInt(), 0xFF25232B.toInt()),
    Skin("Дед", 600, 0xFF7A5C3A.toInt(), 0xFF4F3B25.toInt(), 0xFFDFAE84.toInt(), 4, 0xFF4A3826.toInt(), 0xFFD8D2C4.toInt()),
    Skin("Ниндзя", 900, 0xFF20242B.toInt(), 0xFF16181D.toInt(), 0xFFD9A97C.toInt(), 5, 0xFF20242B.toInt(), 0xFF20242B.toInt()),
    Skin("Пират", 1200, 0xFF8C3B33.toInt(), 0xFF4A2C26.toInt(), 0xFFE0AC80.toInt(), 8, 0xFF2F2F2F.toInt(), 0xFF2A1E16.toInt()),
    Skin("Призрак", 1600, 0xFFBFC7C9.toInt(), 0xFF8E999C.toInt(), 0xFFE4F0EE.toInt(), 7, 0xFFDDE6E6.toInt(), 0xFFC9D3D4.toInt()),
    Skin("Космос", 2200, 0xFFE8EDF2.toInt(), 0xFFB7C3CE.toInt(), 0xFFD9A97C.toInt(), 9, 0xFF9FB4C7.toInt(), 0xFF3B2A1D.toInt()),
    Skin("Золотой", 3000, 0xFFE7B52A.toInt(), 0xFFB98A12.toInt(), 0xFFF3D29B.toInt(), 6, 0xFFFFD95C.toInt(), 0xFF6B4A0C.toInt())
)

data class ServerInfo(
    val name: String,
    val region: String,
    val tag: String
)

val SERVERS = listOf(
    ServerInfo("Сосновый Бор", "RU-01", "лес · базы · новичкам ок"),
    ServerInfo("Туманный Лес", "RU-02", "туман · засады · хардкор"),
    ServerInfo("Каменный Хребет", "RU-03", "горы · камни · тесно"),
    ServerInfo("Мёртвая Пустошь", "RU-04", "пустошь · руины · злые"),
    ServerInfo("Зимний Предел", "RU-05", "снег · холод · отбор")
)

fun fmt(n: Int): String {
    var s = n.toString()
    val neg = s.startsWith("-")
    if (neg) s = s.substring(1)
    val sb = StringBuilder()
    for ((i, ch) in s.withIndex()) {
        if (i > 0 && (s.length - i) % 3 == 0) sb.append(' ')
        sb.append(ch)
    }
    return (if (neg) "-" else "") + sb.toString()
}

object Wipe {
    fun periodDays(server: Int): Int = when (server) {
        4 -> 7
        3 -> 60
        else -> 30
    }

    fun label(server: Int): String = when (server) {
        4 -> "вайп раз в неделю"
        3 -> "вайп раз в 2 месяца"
        else -> "вайп раз в месяц"
    }

    private fun base(now: Long): Calendar {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        c.timeInMillis = now
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c
    }

    fun start(server: Int, now: Long): Long {
        val c = base(now)
        when (periodDays(server)) {
            7 -> {
                val dow = c.get(Calendar.DAY_OF_WEEK)
                val delta = (dow + 5) % 7 // понедельник = 2
                c.add(Calendar.DAY_OF_MONTH, -delta)
            }
            60 -> {
                if (c.get(Calendar.MONTH) % 2 == 1) c.add(Calendar.MONTH, -1)
                c.set(Calendar.DAY_OF_MONTH, 1)
            }
            else -> c.set(Calendar.DAY_OF_MONTH, 1)
        }
        return c.timeInMillis
    }

    fun next(server: Int, now: Long): Long {
        val c = base(start(server, now))
        when (periodDays(server)) {
            7 -> c.add(Calendar.DAY_OF_MONTH, 7)
            60 -> c.add(Calendar.MONTH, 2)
            else -> c.add(Calendar.MONTH, 1)
        }
        return c.timeInMillis
    }

    fun left(server: Int, now: Long): String {
        val ms = (next(server, now) - now).coerceAtLeast(0)
        val d = ms / 86_400_000L
        val h = (ms % 86_400_000L) / 3_600_000L
        val m = (ms % 3_600_000L) / 60_000L
        return when {
            d > 0 -> "$d д $h ч"
            h > 0 -> "$h ч $m мин"
            else -> "$m мин"
        }
    }
}

class Store(context: Context) {
    private val p = context.getSharedPreferences("gramm_io_v1", Context.MODE_PRIVATE)

    val user: String? get() = p.getString("user", null)

    var coins: Int
        get() = p.getInt("coins", 500)
        set(v) {
            p.edit().putInt("coins", v.coerceAtLeast(0)).apply()
        }

    val selectedSkin: Int
        get() {
            val s = p.getInt("skin", 0)
            return if (ownsSkin(s)) s else 0
        }

    fun ownsSkin(id: Int): Boolean = ownedSkins().contains(id)

    fun ownedSkins(): Set<Int> {
        val raw = p.getString("skins", "0") ?: "0"
        val out = HashSet<Int>()
        for (part in raw.split(",")) part.toIntOrNull()?.let { out.add(it) }
        out.add(0)
        return out
    }

    fun selectSkin(id: Int) {
        if (ownsSkin(id)) p.edit().putInt("skin", id).apply()
    }

    fun buySkin(id: Int): Boolean {
        if (id < 0 || id >= SKINS.size) return false
        if (ownsSkin(id)) {
            selectSkin(id)
            return true
        }
        val price = SKINS[id].price
        if (coins < price) return false
        coins = coins - price
        val set = ownedSkins().toMutableSet()
        set.add(id)
        p.edit()
            .putString("skins", set.sorted().joinToString(","))
            .putInt("skin", id)
            .apply()
        return true
    }

    fun createAccount(login: String, pass: String): String? {
        val name = login.trim()
        if (name.length < 3) return "Ник — минимум 3 символа"
        if (name.length > 14) return "Ник — максимум 14 символов"
        if (!name.all { it.isLetterOrDigit() || it == '_' || it == '-' }) return "Только буквы, цифры, _ и -"
        if (pass.length < 4) return "Пароль — минимум 4 символа"
        val key = "acc_" + name.lowercase(Locale.US)
        if (p.contains(key)) return "Этот ник уже занят"
        val salt = randomSalt()
        val h = hash(pass, salt)
        p.edit().putString(key, "$salt:$h:$name").apply()
        p.edit().putString("user", name).apply()
        return null
    }

    fun signIn(login: String, pass: String): String? {
        val name = login.trim()
        if (name.isBlank()) return "Введи ник"
        if (pass.isEmpty()) return "Введи пароль"
        val key = "acc_" + name.lowercase(Locale.US)
        val rec = p.getString(key, null) ?: return "Аккаунт не найден. Нажми «Создать»"
        val parts = rec.split(":")
        if (parts.size < 2) return "Аккаунт повреждён"
        if (hash(pass, parts[0]) != parts[1]) return "Неверный пароль"
        val display = if (parts.size > 2) parts[2] else name
        p.edit().putString("user", display).apply()
        return null
    }

    fun signOut() {
        p.edit().remove("user").apply()
    }

    fun kills(server: Int): Int = p.getInt("kills_$server", 0)

    fun addKill(server: Int) {
        p.edit().putInt("kills_$server", kills(server) + 1).apply()
    }

    fun addCoins(n: Int) {
        if (n == 0) return
        coins = coins + n
    }

    fun resetIfWiped(server: Int, now: Long) {
        val key = Wipe.start(server, now).toString()
        if (p.getString("wipe_$server", null) != key) {
            p.edit()
                .putString("wipe_$server", key)
                .putInt("kills_$server", 0)
                .apply()
        }
    }

    fun totalKills(): Int = (0 until SERVERS.size).sumOf { kills(it) }

    private fun randomSalt(): String {
        val b = ByteArray(8)
        SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.NO_WRAP)
    }

    private fun hash(pass: String, salt: String): String {
        var data = (salt + "|" + pass).toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        repeat(400) {
            data = md.digest(data)
            md.reset()
        }
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }
}
