package io.gramm.game

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.concurrent.thread

/**
 * Самый настоящий мини-MQTT 3.1.1 клиент без библиотек.
 * Публичный брокер работает как общий «сервер» для всех игроков.
 * Есть автоматическое переподключение и смена брокеров.
 */
class Net(private val onMessage: (Int, JSONObject) -> Unit) {

    companion object {
        const val SERVERS = 5
        private const val PREFIX = "grammio/v1/s"
        private const val MAX_PACKET = 64 * 1024
    }

    private data class Host(val host: String, val port: Int, val tls: Boolean)

    private class Out(val server: Int, val payload: ByteArray)

    private val hosts = listOf(
        Host("broker.emqx.io", 1883, false),
        Host("test.mosquitto.org", 1883, false),
        Host("broker.hivemq.com", 1883, false),
        Host("broker.emqx.io", 8883, true)
    )

    @Volatile
    var connected = false
        private set

    @Volatile
    private var running = true
    @Volatile
    private var started = false

    private var socket: Socket? = null
    private var out: OutputStream? = null
    private var input: InputStream? = null

    private val sendQueue = LinkedBlockingQueue<Out>(160)
    private val main = Handler(Looper.getMainLooper())
    private val clientId = "gramm_" + UUID.randomUUID().toString().replace("-", "").take(12)
    private val writeLock = Any()

    fun start() {
        if (started) return
        started = true
        running = true
        thread(name = "gramm-mqtt-rx", isDaemon = true) { rxLoop() }
        thread(name = "gramm-mqtt-tx", isDaemon = true) { txLoop() }
    }

    fun stop() {
        running = false
        connected = false
        closeSocket()
        sendQueue.clear()
    }

    fun send(server: Int, json: JSONObject) {
        if (!connected) return
        if (sendQueue.size > 140) return
        sendQueue.offer(Out(server, json.toString().toByteArray(Charsets.UTF_8)))
    }

    // ---------------------------------------------------------------- threads

    private fun rxLoop() {
        var attempt = 0
        while (running) {
            try {
                val h = hosts[attempt % hosts.size]
                connect(h)
                connected = true
                attempt = 0
                readLoop()
                throw IOException("read loop finished")
            } catch (_: Exception) {
                connected = false
            } finally {
                connected = false
                closeSocket()
            }
            if (!running) break
            attempt++
            try {
                Thread.sleep(1500L)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    private fun txLoop() {
        var lastPing = System.currentTimeMillis()
        while (running) {
            var item: Out? = null
            try {
                item = sendQueue.poll(1000, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                break
            }
            if (item != null && connected) {
                try {
                    writeRaw(publishPacket(PREFIX + item.server, item.payload))
                } catch (_: Exception) {
                    killSocket()
                }
            }
            val now = System.currentTimeMillis()
            if (connected && now - lastPing > 20_000L) {
                lastPing = now
                try {
                    writeRaw(byteArrayOf(0xC0.toByte(), 0x00))
                } catch (_: Exception) {
                    killSocket()
                }
            }
        }
    }

    // ---------------------------------------------------------------- connect

    private fun connect(h: Host) {
        closeSocket()
        val raw = Socket()
        raw.tcpNoDelay = true
        raw.connect(InetSocketAddress(h.host, h.port), 7000)
        raw.soTimeout = 7000
        val s: Socket = if (h.tls) {
            val f = SSLSocketFactory.getDefault() as SSLSocketFactory
            f.createSocket(raw, h.host, h.port, true).also { (it as SSLSocket).startHandshake() }
        } else raw
        socket = s
        input = BufferedInputStream(s.getInputStream())
        out = s.getOutputStream()

        writeRaw(build(0x10, connectBody()))
        val ack = readPacket() ?: throw IOException("no connack")
        if (ack.size < 4 || (ack[0].toInt() and 0xFF) != 0x20) throw IOException("bad connack")
        if (ack[3].toInt() != 0) throw IOException("mqtt refused " + ack[3])

        writeRaw(subscribePacket())
        var subacked = false
        for (i in 0 until 5) {
            val pk = readPacket() ?: break
            val type = (pk[0].toInt() and 0xFF) shr 4
            if (type == 9) {
                subacked = true
                break
            }
            if (type == 3) dispatchPublish(pk)
        }
        if (!subacked) throw IOException("no suback")

        s.soTimeout = 0
    }

    private fun connectBody(): ByteArray {
        val b = ByteArrayOutputStream()
        writeUtf(b, "MQTT")
        b.write(4)      // protocol level 3.1.1
        b.write(2)      // clean session
        b.write(0)      // keepalive 60 sec
        b.write(60)
        writeUtf(b, clientId)
        return b.toByteArray()
    }

    private fun subscribePacket(): ByteArray {
        val b = ByteArrayOutputStream()
        b.write(0)
        b.write(1) // packet id
        for (i in 0 until SERVERS) {
            writeUtf(b, PREFIX + i)
            b.write(0) // QoS 0
        }
        return build(0x82, b.toByteArray())
    }

    private fun publishPacket(topic: String, payload: ByteArray): ByteArray {
        val b = ByteArrayOutputStream()
        writeUtf(b, topic)
        b.write(payload)
        return build(0x30, b.toByteArray())
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

    // ------------------------------------------------------------------- io

    private fun writeRaw(bytes: ByteArray) {
        synchronized(writeLock) {
            val o = out ?: throw IOException("no socket")
            o.write(bytes)
            o.flush()
        }
    }

    private fun readPacket(): ByteArray? {
        val i = input ?: throw IOException("no socket")
        val first = i.read()
        if (first < 0) throw IOException("eof")
        var multiplier = 1
        var length = 0
        var digit: Int
        do {
            digit = i.read()
            if (digit < 0) throw IOException("eof len")
            length += (digit and 0x7F) * multiplier
            multiplier *= 128
            if (multiplier > 128 * 128 * 128) throw IOException("bad len")
        } while ((digit and 0x80) != 0)
        if (length < 0 || length > MAX_PACKET) throw IOException("len $length")
        val body = ByteArray(length)
        var off = 0
        while (off < length) {
            val n = i.read(body, off, length - off)
            if (n < 0) throw IOException("eof body")
            off += n
        }
        val packet = ByteArray(length + 1)
        packet[0] = first.toByte()
        System.arraycopy(body, 0, packet, 1, length)
        return packet
    }

    private fun readLoop() {
        while (running) {
            val pk = readPacket() ?: throw IOException("closed")
            val type = (pk[0].toInt() and 0xFF) shr 4
            if (type == 3) dispatchPublish(pk)
        }
    }

    private fun dispatchPublish(pk: ByteArray) {
        if (pk.size < 4) return
        val qos = (pk[0].toInt() and 0x06) shr 1
        val topicLen = ((pk[1].toInt() and 0xFF) shl 8) or (pk[2].toInt() and 0xFF)
        var start = 3 + topicLen
        if (qos > 0) start += 2
        if (start > pk.size || 3 + topicLen > pk.size) return
        val topic = String(pk, 3, topicLen, Charsets.UTF_8)
        if (!topic.startsWith(PREFIX)) return
        val server = topic.substring(PREFIX.length).toIntOrNull() ?: return
        if (start == pk.size) return
        val payload = String(pk, start, pk.size - start, Charsets.UTF_8)
        val obj = try {
            JSONObject(payload)
        } catch (_: Exception) {
            return
        }
        main.post {
            if (running) {
                try {
                    onMessage(server, obj)
                } catch (_: Exception) {
                    // плохие пакеты не должны ронять игру
                }
            }
        }
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        input = null
        out = null
    }

    private fun killSocket() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
    }
}
