package io.gramm.game

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
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
    private var busy = false

    private val accent = 0xFFFF6B2C.toInt()
    private val gold = 0xFFFFC24D.toInt()
    private val fg = 0xFFFFF3E6.toInt()
    private val dim = 0xFFB9A08F.toInt()
    private val dark = 0xFF160B08.toInt()
    private val card = 0xFF1E1214.toInt()
    private val stroke = 0xFF4A2A2A.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashHandler()
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

    private fun installCrashHandler() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                val i = Intent(this, CrashActivity::class.java)
                i.putExtra("trace", "GHOUL $APP_VERSION\n$sw")
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(i)
            } catch (_: Exception) {
            }
            prev?.uncaughtException(t, e)
        }
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
        val logoLp = LinearLayout.LayoutParams(dp(82f), dp(82f))
        logoLp.gravity = Gravity.CENTER_HORIZONTAL
        outer.addView(logo, logoLp)

        val title = label("GHOUL", 44f, fg, true)
        title.gravity = Gravity.CENTER
        title.setPadding(0, dp(8f), 0, 0)
        outer.addView(title)

        val sub = label("хэллоуин · выживание · онлайн", 12f, accent, false)
        sub.gravity = Gravity.CENTER
        sub.setPadding(0, dp(2f), 0, dp(20f))
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
            setHintTextColor(0xFF8A7065.toInt())
            setBackground(bg(0xFF160E0B.toInt(), 0xFF4A2E26.toInt(), 13f))
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
            setHintTextColor(0xFF8A7065.toInt())
            setBackground(bg(0xFF160E0B.toInt(), 0xFF4A2E26.toInt(), 13f))
            setPadding(dp(15f), dp(13f), dp(15f), dp(13f))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val passLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52f))
        passLp.topMargin = dp(10f)
        box.addView(pass, passLp)

        val error = label("", 11f, 0xFFFF7A6B.toInt(), false)
        error.visibility = View.GONE
        val errLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        errLp.topMargin = dp(8f)
        box.addView(error, errLp)

        val status = label("", 10.5f, gold, false)
        status.visibility = View.GONE
        val stLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        stLp.topMargin = dp(6f)
        box.addView(status, stLp)

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
            background = bg(0xFF2A1A16.toInt(), stroke, 15f)
            isClickable = true
            isFocusable = true
        }
        val crLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48f))
        crLp.topMargin = dp(9f)
        box.addView(create, crLp)

        fun setBusy(b: Boolean, text: String = "") {
            busy = b
            signIn.isEnabled = !b
            create.isEnabled = !b
            signIn.alpha = if (b) 0.55f else 1f
            create.alpha = if (b) 0.55f else 1f
            if (text.isNotEmpty()) {
                status.text = text
                status.visibility = View.VISIBLE
            } else {
                status.visibility = View.GONE
            }
        }

        fun fail(msg: String) {
            setBusy(false)
            error.text = msg
            error.visibility = View.VISIBLE
        }

        fun success(name: String, profile: org.json.JSONObject?) {
            store.setUser(name)
            if (profile != null) {
                store.applyProfile(profile)
            } else if (store.user != null) {
                Cloud.pushProfile(name, store.exportProfile())
            }
            setBusy(false)
            showGame()
        }

        fun attempt(make: Boolean) {
            if (busy) return
            val name = login.text.toString().trim()
            val password = pass.text.toString()
            Pass.validateName(name)?.let { return fail(it) }
            Pass.validatePass(password)?.let { return fail(it) }
            error.visibility = View.GONE
            setBusy(true, if (make) "Создаём аккаунт в облаке…" else "Входим в облако…")
            val cb: (Cloud.Auth) -> Unit = { auth ->
                runOnUiThread {
                    if (auth.error == null) {
                        success(name, auth.profile)
                    } else if (auth.network) {
                        // облако недоступно — не выбрасываем игрока, входим локально
                        val local = if (make) store.createAccount(name, password) else store.signIn(name, password)
                        if (local == null) {
                            status.text = "Облако недоступно · локальный вход"
                            success(name, null)
                        } else {
                            fail("${auth.error}. Локально: $local")
                        }
                    } else {
                        fail(auth.error ?: "Ошибка входа")
                    }
                }
            }
            if (make) Cloud.register(name, password, cb) else Cloud.login(name, password, cb)
        }

        signIn.setOnClickListener { attempt(false) }
        create.setOnClickListener { attempt(true) }
        pass.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attempt(false)
                true
            } else false
        }

        val note = label(
            "Аккаунт в облаке: создал на одном телефоне —\nзаходи с другого. Пароль хранится только хэшем.",
            10f, dim, false
        )
        note.gravity = Gravity.CENTER
        note.setPadding(0, dp(12f), 0, 0)
        note.setLineSpacing(0f, 1.25f)
        box.addView(note)

        val boxLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        outer.addView(box, boxLp)

        val footer = label("GHOUL $APP_VERSION · хэллоуин-сезон · сделано на телефоне", 9f, 0xFF7A5F55.toInt(), false)
        footer.gravity = Gravity.CENTER
        footer.setPadding(0, dp(18f), 0, 0)
        outer.addView(footer)

        scroll.addView(outer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)
        hideBars()
        login.requestFocus()
    }

    private fun showGame() {
        val gv = GameView(this, store)
        gv.onLogout = {
            store.user?.let { Cloud.pushProfile(it, store.exportProfile()) }
            store.signOut()
            showLogin()
        }
        game = gv
        setContentView(gv)
        hideBars()
    }

    private fun hideBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            val ic = window.decorView?.getWindowInsetsController()
            ic?.let {
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
            game?.destroy()
            game = null
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    override fun onDestroy() {
        store.user?.let { Cloud.pushProfile(it, store.exportProfile()) }
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
                intArrayOf(0xFF120A0A.toInt(), 0xFF2A1010.toInt(), 0xFF0B0606.toInt()),
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
            p.color = 0x22FF5A1F
            c.drawCircle(w * 0.10f, h * 0.16f, 130f, p)
            p.color = 0x18FF8A2A
            c.drawCircle(w * 0.92f, h * 0.78f, 190f, p)

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
