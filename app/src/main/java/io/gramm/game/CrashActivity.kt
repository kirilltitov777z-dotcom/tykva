package io.gramm.game

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Экран крэша: вместо молчаливого закрытия показывает текст ошибки.
 * Если приложение снова не запустится — тут будет видно почему.
 */
class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra("trace") ?: "Неизвестная ошибка"

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xFF120A0A.toInt())
        root.setPadding(dp(16f), dp(24f), dp(16f), dp(16f))

        val title = TextView(this)
        title.text = "ТЫКВА сломалась"
        title.setTextColor(0xFFFF6B2C.toInt())
        title.textSize = 24f
        title.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        root.addView(title)

        val hint = TextView(this)
        hint.text = "Скопируй текст ниже и пришли разработчику. Так я починю быстрее."
        hint.setTextColor(0xFFB9A08F.toInt())
        hint.textSize = 12f
        hint.setPadding(0, dp(6f), 0, dp(10f))
        root.addView(hint)

        val scroll = ScrollView(this)
        val code = TextView(this)
        code.text = trace
        code.setTextColor(0xFFFFE7D6.toInt())
        code.textSize = 10f
        code.typeface = Typeface.MONOSPACE
        code.setTextIsSelectable(true)
        code.setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
        code.setBackgroundColor(0xFF1E1214.toInt())
        scroll.addView(code)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val copy = Button(this)
        copy.text = "СКОПИРОВАТЬ ОШИБКУ"
        copy.setTextColor(0xFF160B08.toInt())
        copy.textSize = 14f
        copy.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        copy.background = GradientDrawable().apply {
            cornerRadius = dp(14f).toFloat()
            setColor(0xFFFF6B2C.toInt())
        }
        copy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("tykva crash", trace))
            copy.text = "СКОПИРОВАНО"
        }
        val clp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50f))
        clp.topMargin = dp(12f)
        root.addView(copy, clp)

        val close = Button(this)
        close.text = "ЗАКРЫТЬ"
        close.setTextColor(0xFFFFF3E6.toInt())
        close.textSize = 14f
        close.background = GradientDrawable().apply {
            cornerRadius = dp(14f).toFloat()
            setColor(0xFF2A1A16.toInt())
            setStroke(dp(1.2f), 0xFF4A2A2A.toInt())
        }
        close.setOnClickListener { finish() }
        val xlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46f))
        xlp.topMargin = dp(8f)
        root.addView(close, xlp)

        val note = TextView(this)
        note.text = "ТЫКВА $APP_VERSION · аварийный экран"
        note.setTextColor(0xFF7A5F55.toInt())
        note.textSize = 9f
        note.gravity = Gravity.CENTER
        note.setPadding(0, dp(10f), 0, 0)
        root.addView(note)

        setContentView(root)
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            window.statusBarColor = 0xFF120A0A.toInt()
            window.navigationBarColor = 0xFF120A0A.toInt()
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
}
