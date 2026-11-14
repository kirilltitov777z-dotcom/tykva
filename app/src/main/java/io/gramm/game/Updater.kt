package io.gramm.game

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Обновления прямо из GitHub Releases.
 * При старте приложение спрашивает последний релиз и, если он новее,
 * показывает кнопку. APK скачивается в Downloads и сразу открывается установщик.
 */
object Updater {

    private const val API = "https://api.github.com/repos/kirilltitov777z-dotcom/tykva/releases/latest"

    data class Info(val version: String, val url: String, val notes: String, val page: String)

    fun check(cb: (Info?) -> Unit) {
        thread(name = "tykva-update", isDaemon = true) {
            cb(fetch())
        }
    }

    private fun fetch(): Info? {
        try {
            val c = URL(API).openConnection() as HttpURLConnection
            c.connectTimeout = 6000
            c.readTimeout = 6000
            c.setRequestProperty("User-Agent", "Tykva-Android")
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode != 200) return null
            val text = c.inputStream.bufferedReader().use { it.readText() }
            val j = JSONObject(text)
            val tag = j.optString("tag_name").removePrefix("v").removePrefix("V")
            if (tag.isEmpty() || !isNewer(tag, APP_VERSION)) return null
            val assets = j.optJSONArray("assets")
            var url = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    val n = a.optString("name")
                    if (n.endsWith(".apk", true)) {
                        url = a.optString("browser_download_url")
                        if (n.contains("tykva", true)) break
                    }
                }
            }
            val page = j.optString("html_url")
            if (url.isEmpty()) url = page
            return Info(tag, url, j.optString("body"), page)
        } catch (_: Exception) {
            return null
        }
    }

    fun download(context: Context, url: String, version: String): Long {
        val name = "tykva-$version.apk"
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle("GHOUL $version")
            .setDescription("Скачивание обновления")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(req)
    }

    fun openInstaller(context: Context, id: Long) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = try {
            dm.getUriForDownloadedFile(id)
        } catch (_: Exception) {
            null
        } ?: return
        val i = android.content.Intent(android.content.Intent.ACTION_VIEW)
        i.setDataAndType(uri, "application/vnd.android.package-archive")
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(i)
        } catch (_: Exception) {
        }
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val a = remote.split(".", "-").mapNotNull { it.toIntOrNull() }
        val b = local.split(".", "-").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
