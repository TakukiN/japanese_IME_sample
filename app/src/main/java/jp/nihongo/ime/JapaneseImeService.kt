package jp.nihongo.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout

/**
 * 日本語IME本体。
 * フリックキーボードで読み（ひらがな）を未確定文字列として蓄積し、
 * 「変換」で候補を提示、候補選択で確定する。変換は Converter に委譲。
 */
class JapaneseImeService : InputMethodService() {

    private lateinit var converter: Converter
    private lateinit var candidateView: CandidateView
    private val composing = StringBuilder()

    // 濁点・半濁点・小文字トグル（最後の1文字を巡回変換）
    private val dakutenCycle: Map<Char, Char> = buildCycle(
        listOf("かが"), listOf("きぎ"), listOf("くぐ"), listOf("けげ"), listOf("こご"),
        listOf("さざ"), listOf("しじ"), listOf("すず"), listOf("せぜ"), listOf("そぞ"),
        listOf("ただ"), listOf("ちぢ"), listOf("てで"), listOf("とど"),
        listOf("つづっ"),
        listOf("はばぱ"), listOf("ひびぴ"), listOf("ふぶぷ"), listOf("へべぺ"), listOf("ほぼぽ"),
        listOf("うゔ"),
        listOf("あぁ"), listOf("いぃ"), listOf("えぇ"), listOf("おぉ"),
        listOf("やゃ"), listOf("ゆゅ"), listOf("よょ"), listOf("わゎ")
    )

    private lateinit var mozc: MozcEngine

    override fun onCreate() {
        super.onCreate()
        // 変換: Mozc（未初期化時は軽量辞書にフォールバック）
        mozc = MozcEngine(this)
        converter = MozcConverter(mozc, SimpleDictionaryConverter(this))
        Thread {
            val ok = mozc.initialize()
            android.util.Log.i("JapaneseImeService", "Mozc init=$ok version=${mozc.dataVersion()}")
        }.start()
    }

    override fun onCreateInputView(): View {
        candidateView = CandidateView(this).apply {
            onPick = { commitCandidate(it) }
        }
        val keyboard = FlickKeyboardView(this).apply {
            onKana = { appendKana(it) }
            onConvert = { convertOrSpace() }
            onDakuten = { toggleDakuten() }
            onBackspace = { backspace() }
            onEnter = { enter() }
            onDirect = { commitDirect(it) }
            onToggleLast = { toggleLastChar() }
            onModeChanged = { finishComposingIfAny() }
            onCursorLeft = { sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DPAD_LEFT) }
            onCursorRight = { sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DPAD_RIGHT) }
            onGlobe = { switchIme() }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.BG)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(
                candidateView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                keyboard,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        resetComposing()
    }

    // ↺ 文字トグル用の状態（最後に入力したキーの循環リング）
    private var toggleRing: List<Char> = emptyList()
    private var toggleIndex = 0

    // 各かなの所属キー番号
    private val keyNumberOf: Map<Char, Char> = buildMap {
        listOf(
            '1' to "あいうえお", '2' to "かきくけこ", '3' to "さしすせそ",
            '4' to "たちつてと", '5' to "なにぬねの", '6' to "はひふへほ",
            '7' to "まみむめも", '8' to "やゆよ", '9' to "らりるれろ", '0' to "わをん"
        ).forEach { (num, chars) -> chars.forEach { put(it, num) } }
    }
    private val smallOf: Map<Char, Char> = mapOf(
        'あ' to 'ぁ', 'い' to 'ぃ', 'う' to 'ぅ', 'え' to 'ぇ', 'お' to 'ぉ',
        'つ' to 'っ', 'や' to 'ゃ', 'ゆ' to 'ゅ', 'よ' to 'ょ', 'わ' to 'ゎ'
    )

    /** その文字の循環リング [文字, 番号, 小文字] を返す。 */
    private fun ringFor(c: Char): List<Char> = buildList {
        add(c)
        keyNumberOf[c]?.let { add(it) }
        smallOf[c]?.let { add(it) }
    }

    private fun appendKana(kana: String) {
        composing.append(kana)
        updateComposing()
        // トグル状態を最後の文字で初期化
        toggleRing = ringFor(composing.last())
        toggleIndex = 0
    }

    private fun updateComposing() {
        currentInputConnection?.setComposingText(composing, 1)
        // 入力の都度、簡易的に候補を先読み表示
        if (composing.isNotEmpty()) {
            candidateView.setCandidates(converter.convert(composing.toString()))
        } else {
            candidateView.clear()
        }
    }

    private fun convertOrSpace() {
        if (composing.isEmpty()) {
            currentInputConnection?.commitText("　", 1) // 全角スペース
            return
        }
        candidateView.setCandidates(converter.convert(composing.toString()))
    }

    private fun commitCandidate(text: String) {
        currentInputConnection?.commitText(text, 1)
        resetComposing()
    }

    private fun toggleDakuten() {
        if (composing.isEmpty()) return
        val last = composing.last()
        val next = dakutenCycle[last] ?: return
        composing.setCharAt(composing.length - 1, next)
        updateComposing()
    }

    private fun backspace() {
        if (composing.isNotEmpty()) {
            composing.deleteCharAt(composing.length - 1)
            updateComposing()
        } else {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
    }

    private fun enter() {
        if (composing.isNotEmpty()) {
            // 未確定をそのまま（かな）確定
            currentInputConnection?.finishComposingText()
            resetComposing()
        } else {
            sendDefaultEditorAction(true)
        }
    }

    /** 英字・数字・記号を直接確定する（未確定があれば先に確定）。 */
    private fun commitDirect(text: String) {
        if (composing.isNotEmpty()) {
            currentInputConnection?.finishComposingText()
            resetComposing()
        }
        currentInputConnection?.commitText(text, 1)
    }

    /** モード切替時: 未確定をそのまま確定してクリア。 */
    private fun finishComposingIfAny() {
        if (composing.isEmpty()) return
        currentInputConnection?.finishComposingText()
        resetComposing()
    }

    /** ↺ 最後の入力文字を、そのキーの循環リング [文字→番号→小文字] で次へ送る。 */
    private fun toggleLastChar() {
        if (composing.isEmpty() || toggleRing.size <= 1) return
        // 直近の文字がリングの現在位置と一致する場合のみ循環
        if (composing.last() != toggleRing[toggleIndex]) return
        toggleIndex = (toggleIndex + 1) % toggleRing.size
        composing.setCharAt(composing.length - 1, toggleRing[toggleIndex])
        updateComposing()
    }

    /** 🌐 次のIMEへ切替。不可ならIMEピッカーを表示。 */
    private fun switchIme() {
        try {
            if (!switchToNextInputMethod(false)) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showInputMethodPicker()
            }
        } catch (t: Throwable) {
            android.util.Log.w("JapaneseImeService", "switchIme failed", t)
        }
    }

    private fun resetComposing() {
        composing.setLength(0)
        if (::candidateView.isInitialized) candidateView.clear()
    }

    /** 各巡回文字列から「次の文字」マップを構築（末尾→先頭で循環） */
    private fun buildCycle(vararg groups: List<String>): Map<Char, Char> {
        val map = HashMap<Char, Char>()
        for (group in groups) for (s in group) {
            for (i in s.indices) {
                map[s[i]] = s[(i + 1) % s.length]
            }
        }
        return map
    }
}
