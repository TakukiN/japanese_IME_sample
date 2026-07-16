package jp.nihongo.ime

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import kotlin.math.abs

/**
 * フリック入力キーボード（5列×4行）。3モード（かな/英字/数字）を「あa1」で循環切替。
 * 縦LinearLayout×4行 / 各行 横LinearLayout×5セル(weight均等) で格子を揃える。
 *
 * - かな : 中央かな＋右上番号。フリックで各段。onKana で通知（未確定→変換）。
 * - 英字 : ABC/DEF… フリックで各文字。A/a で大小。onDirect で直接確定。
 * - 数字 : 1〜0＋記号。onDirect で直接確定。
 */
class FlickKeyboardView(context: Context) : LinearLayout(context) {

    enum class Mode { KANA, ALPHABET, NUMBER }

    var onKana: ((String) -> Unit)? = null
    var onDirect: ((String) -> Unit)? = null   // 英字・数字・記号を直接確定
    var onConvert: (() -> Unit)? = null
    var onDakuten: (() -> Unit)? = null
    var onBackspace: (() -> Unit)? = null
    var onEnter: (() -> Unit)? = null
    var onToggleLast: (() -> Unit)? = null    // ↺ かなモード: 未確定文字を循環トグル
    // ↺ 英字/数字モード: 直前に確定した文字 prev を next へ置換
    var onReplaceLast: ((prev: String, next: String) -> Unit)? = null
    var onCursorLeft: (() -> Unit)? = null
    var onCursorRight: (() -> Unit)? = null
    // 長押し: 変換中は文節の伸縮（縮小/拡大）
    var onCursorLeftLong: (() -> Unit)? = null
    var onCursorRightLong: (() -> Unit)? = null
    var onGlobe: (() -> Unit)? = null
    var onModeChanged: (() -> Unit)? = null     // モード切替直前（未確定の確定用）

    private var mode = Mode.KANA
    private var caps = false

    // 直接確定モード（英字/数字）の ↺ 用: 直前キーの候補群・選択位置・確定文字
    private var lastDirectGroup: List<String> = emptyList()
    private var lastDirectIndex = 0
    private var lastDirectChar = ""

    /** 文字キー定義。main=大表示, corner=右上小, sub=下部小, chars=フリック候補, direct=直接確定か */
    private data class KeyDef(
        val main: String,
        val corner: String = "",
        val sub: String = "",
        val chars: List<String>,
        val direct: Boolean
    )

    private val flickThreshold = 40f
    private val keyHeight = dp(54)

    private val guideView = FlickGuideView(context)
    private val guidePopup = PopupWindow(
        guideView,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        isTouchable = false
        isFocusable = false
        isClippingEnabled = false
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(Theme.BG)
        setPadding(dp(5), dp(5), dp(5), dp(7))
        build()
    }

    private fun cycleMode() {
        onModeChanged?.invoke()
        mode = when (mode) {
            Mode.KANA -> Mode.ALPHABET
            Mode.ALPHABET -> Mode.NUMBER
            Mode.NUMBER -> Mode.KANA
        }
        caps = false
        resetDirectToggle()
        build()
    }

    private fun resetDirectToggle() {
        lastDirectGroup = emptyList()
        lastDirectIndex = 0
        lastDirectChar = ""
    }

    /** ↺ キー。かなは未確定トグル、英字/数字は直前確定文字を候補で循環。 */
    private fun revolveLast() {
        if (mode == Mode.KANA) {
            onToggleLast?.invoke()
            return
        }
        if (lastDirectGroup.size <= 1) return
        val prev = lastDirectChar
        lastDirectIndex = (lastDirectIndex + 1) % lastDirectGroup.size
        lastDirectChar = applyCaps(lastDirectGroup[lastDirectIndex])
        onReplaceLast?.invoke(prev, lastDirectChar)
    }

    private fun applyCaps(s: String): String = if (caps) s.uppercase() else s

    private fun build() {
        removeAllViews()
        when (mode) {
            Mode.KANA -> buildKana()
            Mode.ALPHABET -> buildAlphabet()
            Mode.NUMBER -> buildNumber()
        }
    }

    // ---- 各モードのレイアウト ----

    private fun buildKana() {
        fun k(main: String, num: String, c: List<String>) =
            charKey(KeyDef(main, corner = num, chars = c, direct = false))
        addRow(
            funcKey("↺", false, false) { revolveLast() },
            k("あ", "1", listOf("あ", "い", "う", "え", "お")),
            k("か", "2", listOf("か", "き", "く", "け", "こ")),
            k("さ", "3", listOf("さ", "し", "す", "せ", "そ")),
            repeatKey("⌫") { onBackspace?.invoke() }
        )
        addRow(
            funcKey("‹", false, false, sizeSp = 30f, onLongClick = { onCursorLeftLong?.invoke() }) { onCursorLeft?.invoke() },
            k("た", "4", listOf("た", "ち", "つ", "て", "と")),
            k("な", "5", listOf("な", "に", "ぬ", "ね", "の")),
            k("は", "6", listOf("は", "ひ", "ふ", "へ", "ほ")),
            funcKey("›", false, false, sizeSp = 30f, onLongClick = { onCursorRightLong?.invoke() }) { onCursorRight?.invoke() }
        )
        addRow(
            funcKey("あa1", false, true) { cycleMode() },
            k("ま", "7", listOf("ま", "み", "む", "め", "も")),
            k("や", "8", listOf("や", "（", "ゆ", "）", "よ")),
            k("ら", "9", listOf("ら", "り", "る", "れ", "ろ")),
            funcKey("空白\n変換", true, true) { onConvert?.invoke() }
        )
        addRow(
            funcKey("🌐", false, false) { onGlobe?.invoke() },
            funcKey("゛゜小", false, true) { onDakuten?.invoke() },
            k("わ", "0", listOf("わ", "を", "ん", "ー", "〜")),
            k("、", "", listOf("、", "。", "？", "！", "…")),
            funcKey("改行", true, true) { onEnter?.invoke() }
        )
    }

    private fun buildAlphabet() {
        fun a(main: String, num: String, c: List<String>) =
            charKey(KeyDef(main, corner = num, chars = c, direct = true))
        addRow(
            funcKey("↺", false, false) { revolveLast() },
            a("@/:~", "1", listOf("@", "/", ":", "~")),
            a("ABC", "2", listOf("a", "b", "c")),
            a("DEF", "3", listOf("d", "e", "f")),
            repeatKey("⌫") { onBackspace?.invoke() }
        )
        addRow(
            funcKey("‹", false, false, sizeSp = 30f, onLongClick = { onCursorLeftLong?.invoke() }) { onCursorLeft?.invoke() },
            a("GHI", "4", listOf("g", "h", "i")),
            a("JKL", "5", listOf("j", "k", "l")),
            a("MNO", "6", listOf("m", "n", "o")),
            funcKey("›", false, false, sizeSp = 30f, onLongClick = { onCursorRightLong?.invoke() }) { onCursorRight?.invoke() }
        )
        addRow(
            funcKey("あa1", false, true) { cycleMode() },
            a("PQRS", "7", listOf("p", "q", "r", "s")),
            a("TUV", "8", listOf("t", "u", "v")),
            a("WXYZ", "9", listOf("w", "x", "y", "z")),
            funcKey("空白", true, true) { onDirect?.invoke(" ") }
        )
        addRow(
            funcKey("🌐", false, false) { onGlobe?.invoke() },
            funcKey("A/a", false, false) { caps = !caps },
            a("-#", "0", listOf("-", "_", "#")),
            a(".,?!", "", listOf(".", ",", "?", "!")),
            funcKey("改行", true, true) { onEnter?.invoke() }
        )
    }

    private fun buildNumber() {
        fun n(main: String, sub: String, c: List<String>) =
            charKey(KeyDef(main, sub = sub, chars = c, direct = true))
        addRow(
            funcKey("↺", false, false) { revolveLast() },
            n("1", "/:@~", listOf("1", "/", ":", "@", "~")),
            n("2", "\"\\%'", listOf("2", "\"", "\\", "%", "'")),
            n("3", "+×÷-", listOf("3", "+", "×", "÷", "-")),
            repeatKey("⌫") { onBackspace?.invoke() }
        )
        addRow(
            funcKey("‹", false, false, sizeSp = 30f, onLongClick = { onCursorLeftLong?.invoke() }) { onCursorLeft?.invoke() },
            n("4", "*;&·", listOf("4", "*", ";", "&", "·")),
            n("5", "¥₩€$", listOf("5", "¥", "₩", "€", "$")),
            n("6", "<^>=", listOf("6", "<", "^", ">", "=")),
            funcKey("›", false, false, sizeSp = 30f, onLongClick = { onCursorRightLong?.invoke() }) { onCursorRight?.invoke() }
        )
        addRow(
            funcKey("あa1", false, true) { cycleMode() },
            n("7", "#♪〒※", listOf("7", "#", "♪", "〒", "※")),
            n("8", "[{}]", listOf("8", "[", "{", "}", "]")),
            n("9", "☆○◇♡", listOf("9", "☆", "○", "◇", "♡")),
            funcKey("空白", true, true) { onDirect?.invoke(" ") }
        )
        addRow(
            funcKey("🌐", false, false) { onGlobe?.invoke() },
            n("…", "「口」", listOf("…", "「", "口", "」")),
            n("0", "()", listOf("0", "(", ")")),
            n(".", ",?!", listOf(".", ",", "?", "!")),
            funcKey("改行", true, true) { onEnter?.invoke() }
        )
    }

    // ---- セル生成 ----

    private fun addRow(vararg cells: View) {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        for (c in cells) {
            // 高さは行に合わせて MATCH_PARENT。これでテキスト行数に依らず全キー同一寸法。
            val lp = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            lp.setMargins(dp(2), dp(2), dp(2), dp(2))
            row.addView(c, lp)
        }
        // 行は固定高さ（keyHeight + 上下マージン分）
        addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, keyHeight + dp(4)))
    }

    private fun charKey(def: KeyDef): View {
        val frame = FrameLayout(context).apply {
            background = Theme.keyBackground(Theme.KEY, ctx = context)
        }
        val main = TextView(context).apply {
            text = def.main
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (def.main.length >= 3) 15f else 22f)
            setTextColor(Theme.TEXT)
            typeface = Theme.fontMedium
        }
        frame.addView(
            main,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        if (def.corner.isNotEmpty()) {
            frame.addView(TextView(context).apply {
                text = def.corner
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(Theme.TEXT_SUB)
                typeface = Theme.fontRegular
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply { setMargins(0, dp(4), dp(6), 0) })
        }
        if (def.sub.isNotEmpty()) {
            frame.addView(TextView(context).apply {
                text = def.sub
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                setTextColor(Theme.TEXT_SUB)
                typeface = Theme.fontRegular
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { setMargins(0, 0, 0, dp(4)) })
        }
        attachFlick(frame, def)
        return frame
    }

    private fun attachFlick(key: View, def: KeyDef) {
        var startX = 0f
        var startY = 0f
        key.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = ev.x; startY = ev.y
                    v.background = Theme.keyBackground(Theme.KEY_PRESSED, Theme.ACCENT, ctx = context)
                    showGuide(v, def.chars, 0)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    guideView.active = flickIndex(ev.x - startX, ev.y - startY, def.chars.size)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.background = Theme.keyBackground(Theme.KEY, ctx = context)
                    hideGuide()
                    val idx = flickIndex(ev.x - startX, ev.y - startY, def.chars.size)
                    if (def.chars[idx].isNotEmpty()) emit(def, idx)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.background = Theme.keyBackground(Theme.KEY, ctx = context)
                    hideGuide(); true
                }
                else -> false
            }
        }
    }

    private fun emit(def: KeyDef, index: Int) {
        val ch = def.chars[index]
        if (def.direct) {
            val out = applyCaps(ch)
            // ↺ 用に直前キーの候補群と選択位置を記録
            lastDirectGroup = def.chars
            lastDirectIndex = index
            lastDirectChar = out
            onDirect?.invoke(out)
        } else {
            onKana?.invoke(ch)
        }
    }

    /** フリック方向→index。候補数が足りない方向は中央(0)に丸める。 */
    private fun flickIndex(dx: Float, dy: Float, size: Int): Int {
        if (abs(dx) < flickThreshold && abs(dy) < flickThreshold) return 0
        val idx = if (abs(dx) > abs(dy)) {
            if (dx < 0) 1 else 3
        } else {
            if (dy < 0) 2 else 4
        }
        return if (idx < size) idx else 0
    }

    private fun funcKey(
        label: String,
        accent: Boolean,
        small: Boolean,
        sizeSp: Float? = null,
        onLongClick: (() -> Unit)? = null,
        action: () -> Unit,
    ): View {
        val tv = TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            // 記号グリフ（↺ ‹ › 等）を上下中央へ正しく収めるため既定の行間余白を無効化
            includeFontPadding = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp ?: if (small) 15f else 21f)
            setTextColor(if (accent) Theme.ON_ACCENT else Theme.TEXT_SUB)
            typeface = Theme.fontMedium
        }
        val fill = if (accent) Theme.ACCENT else Theme.KEY_FUNC
        val stroke = if (accent) Theme.ACCENT_DK else Theme.KEY_BORDER
        tv.background = Theme.keyBackground(fill, stroke, ctx = context)
        tv.setOnClickListener { action() }
        if (onLongClick != null) {
            tv.setOnLongClickListener { onLongClick(); true }
        }
        return tv
    }

    /** 押下中はリピート実行する機能キー（削除の連続入力用）。 */
    private fun repeatKey(label: String, action: () -> Unit): View {
        val tv = TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(Theme.TEXT_SUB)
            typeface = Theme.fontMedium
            background = Theme.keyBackground(Theme.KEY_FUNC, ctx = context)
        }
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                action()
                handler.postDelayed(this, 55) // リピート間隔
            }
        }
        tv.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.background = Theme.keyBackground(Theme.KEY_PRESSED, Theme.ACCENT, ctx = context)
                    action()
                    handler.postDelayed(runnable, 400) // 初回リピートまでの待ち
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.background = Theme.keyBackground(Theme.KEY_FUNC, ctx = context)
                    handler.removeCallbacks(runnable)
                    true
                }
                else -> false
            }
        }
        return tv
    }

    private fun showGuide(key: View, chars: List<String>, active: Int) {
        guideView.chars = chars
        guideView.active = active
        guideView.measure(0, 0)
        val gw = guideView.measuredWidth
        val gh = guideView.measuredHeight
        val loc = IntArray(2)
        key.getLocationInWindow(loc)
        val x = loc[0] + key.width / 2 - gw / 2
        val y = loc[1] - gh - dp(4)
        if (guidePopup.isShowing) {
            guidePopup.update(x, y, gw, gh)
        } else {
            guidePopup.width = gw
            guidePopup.height = gh
            guidePopup.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
        }
    }

    private fun hideGuide() {
        if (guidePopup.isShowing) guidePopup.dismiss()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
