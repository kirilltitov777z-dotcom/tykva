package io.gramm.game

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var store: Store
    private var game: GameView? = null

    private val accent = 0xFFC8FF4D.toInt()
    private val fg = 0xFFF0F8E9.toInt()
    private val dim = 0xFF91A894.toInt()
    private val dark = 0xFF0C150F.toInt()
    private val card = 0xFF13231A.toInt()
    private val stroke = 0xFF2E4C36.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        store = Store(this)
        if (store.user == null) showLogin() else showGame()
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun bg(fill: Int, strokeColor: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(fill)
            if (strokeColor != 0) setStroke(dp(1.2f), strokeColor)
        }
    }

    private fun label(s: String, size: Float, color: Int, bold: Boolean): TextView {
        return TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            typeface = if (bold) Typeface.create("sans-serif-black", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    private fun showLogin() {
        game?.destroy()
        game = null
        hideBars()

        val root = FrameLayout(this)
        root.addView(AuthBg(this), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.overScrollMode = View.OVER_SCROLL_NEVER

        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.VERTICAL
        outer.gravity = Gravity.CENTER
        outer.setPadding(dp(22f), dp(22f), dp(22f), dp(22f))

        val logo = ImageView(this)
        logo.setImageResource(R.mipmap.ic_launcher)
        val logoLp = LinearLayout.LayoutParams(dp(76f), dp(76f))
        logoLp.gravity = Gravity.CENTER_HORIZONTAL
        outer.addView(logo, logoLp)

        val title = label("ГРАММ.io", 40f, fg, true)
        title.gravity = Gravity.CENTER
        title.setPadding(0, dp(10f), 0, 0)
        outer.addView(title)

        val sub = label("выживание · онлайн", 13f, accent, false)
        sub.gravity = Gravity.CENTER
        sub.setPadding(0, dp(2f), 0, dp(22f))
        outer.addView(sub)

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.background = bg(card, stroke, 20f)
        box.setPadding(dp(18f), dp(18f), dp(18f), dp(18f))

        val login = EditText(this).apply {
            hint = "Никнейм"
            setSingleLine(true)
            textSize = 16f
            setTextColor(fg)
            setHintTextColor(0xFF708675.toInt())
            setBackground(bg(0xFF0E1A13.toInt(), 0xFF2B4633.toInt(), 13f))
            setPadding(dp(15f), dp(13f), dp(15f), dp(13f))
            imeOptions = EditorInfo.IME_ACTION_NEXT
            inputType = InputType.TYPE_CLASS_TEXT
        }
        box.addView(login, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52f)))

        val pass = EditText(this).apply {
            hint = "Пароль"
            setSingleLine(true)
            textSize = 16f
            setTextColor(fg)
            setHintTextColor(0xFF708675.toInt())
            setBackground(bg(0xFF0E1A13.toInt(), 0xFF2B4633.toInt(), 13f))
            setPadding(dp(15f), dp(13f), dp(15f), dp(13f))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val passLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52f))
        passLp.topMargin = dp(10f)
        box.addView(pass, passLp)

        val error = label("", 11f, 0xFFFF6B6B.toInt(), false)
        error.visibility = View.GONE
        val errLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        errLp.topMargin = dp(8f)
        box.addView(error, errLp)

        val signIn = TextView(this).apply {
            text = "ВОЙТИ"
            textSize = 16f
            setTextColor(dark)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
            background = bg(accent, 0, 15f)
            isClickable = true
            isFocusable = true
        }
        val inLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54f))
        inLp.topMargin = dp(14f)
        box.addView(signIn, inLp)

        val create = TextView(this).apply {
            text = "СОЗДАТЬ АККАУНТ"
            textSize = 13f
            setTextColor(fg)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            background = bg(0xFF1A2E21.toInt(), stroke, 15f)
            isClickable = true
            isFocusable = true
        }
        val crLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48f))
        crLp.topMargin = dp(9f)
        box.addView(create, crLp)

        fun attempt(make: Boolean) {
            val err = if (make) {
                store.createAccount(login.text.toString(), pass.text.toString())
            } else {
                store.signIn(login.text.toString(), pass.text.toString())
            }
            if (err == null) {
                showGame()
            } else {
                error.text = err
                error.visibility = View.VISIBLE
            }
        }

        signIn.setOnClickListener { attempt(false) }
        create.setOnClickListener { attempt(true) }
        pass.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attempt(false)
                true
            } else false
        }

        val note = label("Аккаунт хранится на этом телефоне.\nОнлайн — через публичный MQTT-брокер.", 10f, dim, false)
        note.gravity = Gravity.CENTER
        note.setPadding(0, dp(12f), 0, 0)
        note.setLineSpacing(0f, 1.25f)
        box.addView(note)

        val boxLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        outer.addView(box, boxLp)

        val footer = label("ГРАММ.io v1.0 · сделано на телефоне", 9f, 0xFF61786A.toInt(), false)
        footer.gravity = Gravity.CENTER
        footer.setPadding(0, dp(18f), 0, 0)
        outer.addView(footer)

        scroll.addView(outer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)
        login.requestFocus()
    }

    private fun showGame() {
        val gv = GameView(this, store)
        gv.onLogout = { showLogin() }
        game = gv
        setContentView(gv)
        hideBars()
    }

    private fun hideBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    @Deprecated("android")
    override fun onBackPressed() {
        if (game != null) {
            if (game!!.handleBack()) return
            game!!.destroy()
            game = null
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    override fun onDestroy() {
        game?.destroy()
        super.onDestroy()
    }

    private class AuthBg(context: Context) : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            p.shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(0xFF0A1511.toInt(), 0xFF11281C.toInt(), 0xFF08110D.toInt()),
                null, Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, w, h, p)
            p.shader = null

            p.style = Paint.Style.STROKE
            p.strokeWidth = 1f
            p.color = 0x08FFFFFF
            var x = 0f
            while (x < w) {
                c.drawLine(x, 0f, x, h, p)
                x += 44f * resources.displayMetrics.density
            }
            var y = 0f
            while (y < h) {
                c.drawLine(0f, y, w, y, p)
                y += 44f * resources.displayMetrics.density
            }
            p.style = Paint.Style.FILL
            p.color = 0x14C8FF4D
            c.drawCircle(w * 0.10f, h * 0.16f, 130f, p)
            p.color = 0x10C8FF4D
            c.drawCircle(w * 0.92f, h * 0.78f, 170f, p)

            p.color = 0x33000000
            val path = Path()
            val base = h
            for (i in 0 until 8) {
                val bx = w * (i + 0.5f) / 8f
                val bw = 55f + (i % 3) * 14f
                path.moveTo(bx - bw, base)
                path.lineTo(bx, base - 150f - (i % 4) * 22f)
                path.lineTo(bx + bw, base)
                path.close()
            }
            c.drawPath(path, p)
        }
    }
}
