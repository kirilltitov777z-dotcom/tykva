package io.gramm.game

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * ТЫКВА — маленькая survival-игра.
 * Аккаунты синхронизируются через облако (MQTT retained), прогресс тоже.
 */

const val APP_VERSION = "1.1"
const val APP_TITLE = "ТЫКВА"

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
//      6 корона, 7 капюшон, 8 бандана, 9 скафандр, 10 тыква, 11 шляпа ведьмы
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
    Skin("Золотой", 3000, 0xFFE7B52A.toInt(), 0xFFB98A12.toInt(), 0xFFF3D29B.toInt(), 6, 0xFFFFD95C.toInt(), 0xFF6B4A0C.toInt()),
    // Хэллоуин-ивент
    Skin("Тыква", 0, 0xFFE0601F.toInt(), 0xFF4A2A16.toInt(), 0xFFF0B37A.toInt(), 10, 0xFFFF7A1A.toInt(), 0xFF2A1A0F.toInt()),
    Skin("Ведьма", 666, 0xFF4A245C.toInt(), 0xFF2A1436.toInt(), 0xFFA8C686.toInt(), 11, 0xFF3B1F4A.toInt(), 0xFF1A1020.toInt())
)

data class WeaponSkin(
    val name: String,
    val price: Int,
    val metal: Int,
    val wood: Int,
    val accent: Int
)

val WEAPON_SKINS = listOf(
    WeaponSkin("Заводское", 0, 0xFF9AA4AA.toInt(), 0xFF7A5230.toInt(), 0xFFB9C2C6.toInt()),
    WeaponSkin("Тыква", 350, 0xFFFF8C2A.toInt(), 0xFF4A2C16.toInt(), 0xFFFFD166.toInt()),
    WeaponSkin("Кровь", 700, 0xFFB23A3A.toInt(), 0xFF3A1E1E.toInt(), 0xFFFF6B6B.toInt()),
    WeaponSkin("Золото", 1500, 0xFFE7B52A.toInt(), 0xFF6B4A0C.toInt(), 0xFFFFE08A.toInt())
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
    ServerInfo("Тыквенные Топи", "RU-04", "хэллоуин · топи · жутко"),
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
                val delta = (dow + 5) % 7
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

object Pass {
    fun randomSalt(): String {
        val b = ByteArray(8)
        SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.NO_WRAP)
    }

    fun hash(pass: String, salt: String): String {
        var data = (salt + "|" + pass).toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        repeat(400) {
            data = md.digest(data)
            md.reset()
        }
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    fun validateName(login: String): String? {
        val name = login.trim()
        if (name.length < 3) return "Ник — минимум 3 символа"
        if (name.length > 14) return "Ник — максимум 14 символов"
        if (!name.all { it.isLetterOrDigit() || it == '_' || it == '-' }) return "Только буквы, цифры, _ и -"
        return null
    }

    fun validatePass(pass: String): String? {
        if (pass.length < 4) return "Пароль — минимум 4 символа"
        if (pass.length > 40) return "Пароль — максимум 40 символов"
        return null
    }
}

class Store(context: Context) {
    private val p = context.getSharedPreferences("tykva_v2", Context.MODE_PRIVATE)

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
        val raw = p.getString("skins", "") ?: ""
        val out = HashSet<Int>()
        for (part in raw.split(",")) part.toIntOrNull()?.let { if (it in SKINS.indices) out.add(it) }
        out.add(0)
        out.add(10) // Тыква — подарок на Хэллоуин
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

    val selectedWeaponSkin: Int
        get() {
            val s = p.getInt("wskin", 0)
            return if (ownsWeaponSkin(s)) s else 0
        }

    fun ownsWeaponSkin(id: Int): Boolean = ownedWeaponSkins().contains(id)

    fun ownedWeaponSkins(): Set<Int> {
        val raw = p.getString("wskins", "0") ?: "0"
        val out = HashSet<Int>()
        for (part in raw.split(",")) part.toIntOrNull()?.let { if (it in WEAPON_SKINS.indices) out.add(it) }
        out.add(0)
        return out
    }

    fun selectWeaponSkin(id: Int) {
        if (ownsWeaponSkin(id)) p.edit().putInt("wskin", id).apply()
    }

    fun buyWeaponSkin(id: Int): Boolean {
        if (id < 0 || id >= WEAPON_SKINS.size) return false
        if (ownsWeaponSkin(id)) {
            selectWeaponSkin(id)
            return true
        }
        val price = WEAPON_SKINS[id].price
        if (coins < price) return false
        coins = coins - price
        val set = ownedWeaponSkins().toMutableSet()
        set.add(id)
        p.edit()
            .putString("wskins", set.sorted().joinToString(","))
            .putInt("wskin", id)
            .apply()
        return true
    }

    var toolLevel: Int
        get() = p.getInt("tool", 0).coerceIn(0, 3)
        set(v) {
            p.edit().putInt("tool", v.coerceIn(0, 3)).apply()
        }

    fun setUser(name: String) {
        p.edit().putString("user", name).apply()
    }

    fun createAccount(login: String, pass: String): String? {
        val name = login.trim()
        Pass.validateName(name)?.let { return it }
        Pass.validatePass(pass)?.let { return it }
        val key = "acc_" + name.lowercase(Locale.US)
        if (p.contains(key)) return "Этот ник уже занят"
        val salt = Pass.randomSalt()
        val h = Pass.hash(pass, salt)
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
        if (Pass.hash(pass, parts[0]) != parts[1]) return "Неверный пароль"
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

    fun exportProfile(): JSONObject {
        val o = JSONObject()
        o.put("name", user ?: "")
        o.put("coins", coins)
        o.put("skins", JSONArray(ownedSkins().sorted()))
        o.put("skin", selectedSkin)
        o.put("wskins", JSONArray(ownedWeaponSkins().sorted()))
        o.put("wskin", selectedWeaponSkin)
        o.put("tool", toolLevel)
        val k = JSONArray()
        for (i in SERVERS.indices) k.put(kills(i))
        o.put("kills", k)
        o.put("u", System.currentTimeMillis())
        return o
    }

    fun applyProfile(j: JSONObject) {
        val cloudSkins = HashSet<Int>()
        val sa = j.optJSONArray("skins")
        if (sa != null) for (i in 0 until sa.length()) sa.optInt(i, -1).let { if (it in SKINS.indices) cloudSkins.add(it) }
        val skins = ownedSkins().toMutableSet()
        skins.addAll(cloudSkins)
        val cloudW = HashSet<Int>()
        val wa = j.optJSONArray("wskins")
        if (wa != null) for (i in 0 until wa.length()) wa.optInt(i, -1).let { if (it in WEAPON_SKINS.indices) cloudW.add(it) }
        val wskins = ownedWeaponSkins().toMutableSet()
        wskins.addAll(cloudW)

        val skin = j.optInt("skin", selectedSkin)
        val wskin = j.optInt("wskin", selectedWeaponSkin)
        val tool = j.optInt("tool", toolLevel)
        val cloudCoins = j.optInt("coins", coins)

        val killsArr = j.optJSONArray("kills")
        val ed = p.edit()
        ed.putInt("coins", cloudCoins.coerceAtLeast(0))
        ed.putString("skins", skins.sorted().joinToString(","))
        ed.putString("wskins", wskins.sorted().joinToString(","))
        ed.putInt("skin", if (skin in skins) skin else 0)
        ed.putInt("wskin", if (wskin in wskins) wskin else 0)
        ed.putInt("tool", tool.coerceIn(0, 3))
        if (killsArr != null) {
            for (i in 0 until minOf(killsArr.length(), SERVERS.size)) {
                val v = killsArr.optInt(i, 0)
                if (v > kills(i)) ed.putInt("kills_$i", v)
            }
        }
        ed.apply()
    }
}
