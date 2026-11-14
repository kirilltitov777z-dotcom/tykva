package io.gramm.game

import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Облачные аккаунты без своего сервера: публичный MQTT-брокер хранит
 * retained-сообщения. Один телефон создаёт аккаунт, другой — входит.
 * Пароль не уходит в сеть: передаётся только соль и SHA-256-хэш.
 */
object Cloud {

    private const val ACCOUNTS = "grammio/v2/accounts"
    private const val PROFILE_PREFIX = "grammio/v2/profiles/"

    data class Auth(val error: String?, val profile: JSONObject?, val network: Boolean)

    private val busy = AtomicInteger(0)

    fun register(name: String, pass: String, cb: (Auth) -> Unit) {
        thread(name = "tykva-cloud-reg", isDaemon = true) {
            cb(doRegister(name.trim(), pass))
        }
    }

    fun login(name: String, pass: String, cb: (Auth) -> Unit) {
        thread(name = "tykva-cloud-login", isDaemon = true) {
            cb(doLogin(name.trim(), pass))
        }
    }

    fun pushProfile(name: String, profile: JSONObject) {
        if (name.isBlank()) return
        thread(name = "tykva-cloud-push", isDaemon = true) {
            try {
                val c = Mqtt()
                if (c.open()) {
                    val j = JSONObject(profile.toString())
                    j.put("name", name)
                    c.publishRetained(profileTopic(name), j.toString().toByteArray(Charsets.UTF_8))
                }
                c.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun profileTopic(name: String): String =
        PROFILE_PREFIX + name.lowercase(Locale.US)

    private fun doRegister(name: String, pass: String): Auth {
        val c = Mqtt()
        return try {
            if (!c.open()) return Auth("Нет связи с облаком. Проверь интернет", null, true)
            if (!c.subscribe(ACCOUNTS)) return Auth("Облако не отвечает. Попробуй ещё", null, true)

            val raw = c.readRetained(ACCOUNTS, 1600)
            val accounts = if (raw.isNullOrBlank()) JSONObject().put("v", 2).put("users", JSONObject()) else JSONObject(raw)
            val users = accounts.optJSONObject("users") ?: JSONObject()
            val key = name.lowercase(Locale.US)
            val old = users.optJSONObject(key)
            if (old != null) {
                val salt = old.optString("s")
                val hash = old.optString("h")
                if (salt.isNotEmpty() && hash == Pass.hash(pass, salt)) {
                    val profile = c.subscribe(PROFILE_PREFIX + key).let {
                        if (it) readProfile(c, name) else null
                    }
                    return Auth(null, profile, false)
                }
                return Auth("Этот ник уже занят. Попробуй войти", null, false)
            }

            val salt = Pass.randomSalt()
            val rec = JSONObject()
                .put("n", name)
                .put("s", salt)
                .put("h", Pass.hash(pass, salt))
                .put("c", System.currentTimeMillis() / 1000L)
            users.put(key, rec)
            accounts.put("users", users)
            accounts.put("v", 2)
            accounts.put("u", System.currentTimeMillis())

            val payload = accounts.toString().toByteArray(Charsets.UTF_8)
            c.publishRetained(ACCOUNTS, payload)
            val echo = c.readRetained(ACCOUNTS, 2200)
            if (echo == null) return Auth("Не удалось сохранить аккаунт. Ещё разок", null, true)
            val check = JSONObject(echo).optJSONObject("users")?.optJSONObject(key)
            if (check == null || check.optString("h") != rec.optString("h")) {
                return Auth("Облако занято, попробуй другой ник", null, false)
            }
            Auth(null, null, false)
        } catch (_: Exception) {
            Auth("Облако недоступно. Попробуй ещё", null, true)
        } finally {
            c.close()
        }
    }

    private fun doLogin(name: String, pass: String): Auth {
        val c = Mqtt()
        return try {
            if (!c.open()) return Auth("Нет связи с облаком. Проверь интернет", null, true)
            if (!c.subscribe(ACCOUNTS)) return Auth("Облако не отвечает. Попробуй ещё", null, true)
            val raw = c.readRetained(ACCOUNTS, 1600)
            if (raw.isNullOrBlank()) return Auth("Аккаунт не найден. Нажми «Создать»", null, false)
            val users = JSONObject(raw).optJSONObject("users") ?: JSONObject()
            val rec = users.optJSONObject(name.lowercase(Locale.US))
                ?: return Auth("Аккаунт не найден. Нажми «Создать»", null, false)
            val salt = rec.optString("s")
            if (salt.isEmpty()) return Auth("Аккаунт повреждён", null, false)
            if (Pass.hash(pass, salt) != rec.optString("h")) return Auth("Неверный пароль", null, false)
            val profile = if (c.subscribe(profileTopic(name))) readProfile(c, name) else null
            Auth(null, profile, false)
        } catch (_: Exception) {
            Auth("Облако недоступно. Попробуй ещё", null, true)
        } finally {
            c.close()
        }
    }

    private fun readProfile(c: Mqtt, name: String): JSONObject? {
        val raw = c.readRetained(profileTopic(name), 1400) ?: return null
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------ MQTT

    private class Mqtt {
        private data class Host(val host: String, val port: Int)

        private val hosts = listOf(
            Host("broker.emqx.io", 1883),
            Host("test.mosquitto.org", 1883),
            Host("broker.hivemq.com", 1883)
        )

        private var socket: Socket? = null
        private var input: InputStream? = null
        private var output: OutputStream? = null
        private val clientId = "tykva_" + System.nanoTime().toString(36) + (100..999).random()
        private var packetId = 1

        fun open(): Boolean {
            for (h in hosts) {
                try {
                    val s = Socket()
                    s.tcpNoDelay = true
                    s.connect(InetSocketAddress(h.host, h.port), 3500)
                    s.soTimeout = 4000
                    socket = s
                    input = BufferedInputStream(s.getInputStream())
                    output = s.getOutputStream()
                    write(build(0x10, connectBody()))
                    val ack = readPacket(4000) ?: throw Exception("no ack")
                    if ((ack[0].toInt() and 0xFF) != 0x20) throw Exception("bad ack")
                    if (ack.size < 4 || ack[3].toInt() != 0) throw Exception("refused")
                    return true
                } catch (_: Exception) {
                    close()
                }
            }
            return false
        }

        fun subscribe(topic: String): Boolean {
            val id = nextId()
            val b = ByteArrayOutputStream()
            b.write((id shr 8) and 0xFF)
            b.write(id and 0xFF)
            writeUtf(b, topic)
            b.write(0)
            write(build(0x82, b.toByteArray()))
            val deadline = System.currentTimeMillis() + 3500
            while (System.currentTimeMillis() < deadline) {
                val pk = readPacket(700) ?: continue
                val type = (pk[0].toInt() and 0xFF) shr 4
                if (type == 9) return true
                if (type == 3) {
                    // retained может прийти раньше SUBACK
                    stash = parsePublish(pk)
                }
            }
            return false
        }

        fun readRetained(topic: String, timeoutMs: Int): String? {
            val st = stash ?: null
            if (st != null && st.first == topic) {
                stash = null
                return st.second
            }
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val left = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(120)
                val pk = readPacket(left) ?: continue
                val type = (pk[0].toInt() and 0xFF) shr 4
                if (type != 3) continue
                val parsed = parsePublish(pk) ?: continue
                if (parsed.first == topic) return parsed.second
                stash = parsed
            }
            return null
        }

        fun publishRetained(topic: String, payload: ByteArray) {
            val b = ByteArrayOutputStream()
            writeUtf(b, topic)
            b.write(payload)
            write(build(0x31, b.toByteArray()))
        }

        fun close() {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
            socket = null
            input = null
            output = null
        }

        private var stash: Pair<String, String>? = null

        private fun nextId(): Int {
            packetId++
            if (packetId > 65000) packetId = 1
            return packetId
        }

        private fun connectBody(): ByteArray {
            val b = ByteArrayOutputStream()
            writeUtf(b, "MQTT")
            b.write(4)
            b.write(2)
            b.write(0)
            b.write(45)
            writeUtf(b, clientId)
            return b.toByteArray()
        }

        private fun writeUtf(b: ByteArrayOutputStream, s: String) {
            val bytes = s.toByteArray(Charsets.UTF_8)
            b.write((bytes.size shr 8) and 0xFF)
            b.write(bytes.size and 0xFF)
            b.write(bytes)
        }

        private fun build(header: Int, body: ByteArray): ByteArray {
            val b = ByteArrayOutputStream()
            b.write(header)
            var x = body.size
            do {
                var d = x % 128
                x /= 128
                if (x > 0) d = d or 0x80
                b.write(d)
            } while (x > 0)
            b.write(body)
            return b.toByteArray()
        }

        private fun write(bytes: ByteArray) {
            val o = output ?: throw Exception("no socket")
            o.write(bytes)
            o.flush()
        }

        private fun readPacket(timeoutMs: Int): ByteArray? {
            val i = input ?: return null
            try {
                socket?.soTimeout = timeoutMs
            } catch (_: Exception) {
            }
            return try {
                val first = i.read()
                if (first < 0) return null
                var multiplier = 1
                var length = 0
                var digit: Int
                do {
                    digit = i.read()
                    if (digit < 0) return null
                    length += (digit and 0x7F) * multiplier
                    multiplier *= 128
                    if (multiplier > 128 * 128 * 128) return null
                } while ((digit and 0x80) != 0)
                if (length < 0 || length > 512 * 1024) return null
                val body = ByteArray(length)
                var off = 0
                while (off < length) {
                    val n = i.read(body, off, length - off)
                    if (n < 0) return null
                    off += n
                }
                val packet = ByteArray(length + 1)
                packet[0] = first.toByte()
                System.arraycopy(body, 0, packet, 1, length)
                packet
            } catch (_: Exception) {
                null
            }
        }

        private fun parsePublish(pk: ByteArray): Pair<String, String>? {
            if (pk.size < 4) return null
            val qos = (pk[0].toInt() and 0x06) shr 1
            val topicLen = ((pk[1].toInt() and 0xFF) shl 8) or (pk[2].toInt() and 0xFF)
            var start = 3 + topicLen
            if (qos > 0) start += 2
            if (start > pk.size) return null
            val topic = String(pk, 3, topicLen, Charsets.UTF_8)
            val payload = String(pk, start, pk.size - start, Charsets.UTF_8)
            return topic to payload
        }
    }
}
