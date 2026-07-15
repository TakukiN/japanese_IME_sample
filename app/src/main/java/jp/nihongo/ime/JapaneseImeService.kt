package jp.nihongo.ime

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout

/**
 * 日本語IME本体。
 *
 * フリックキーボードで読み（ひらがな）を未確定文字列（composing）として蓄積し、
 * 変換候補を提示、候補選択で確定する。かな漢字変換は [Converter] に委譲する。
 *
 * セキュリティ（OWASP MASVS）:
 * - パスワード等の機密入力欄（[isSecureField]）では変換・候補提示・エンジン送信を行わず、
 *   打鍵をそのまま直接確定する（学習・ログ・外部エンジンへ機密文字を渡さない）。
 * - INTERNET 権限を持たず、入力内容の外部送信は一切行わない（完全オンデバイス）。
 * - 打鍵内容をログ出力しない。
 */
class JapaneseImeService : InputMethodService() {

    private lateinit var converter: Converter
    private lateinit var mozc: MozcEngine
    private lateinit var candidateView: CandidateView

    private val composing = StringBuilder()

    /** 機密入力欄（パスワード等）か。true の間は変換・候補・エンジン送信を無効化する。 */
    private var secureField = false

    // ↺ 文字トグルの状態（最後に入力したキーの循環リングと現在位置）
    private var toggleRing: List<Char> = emptyList()
    private var toggleIndex = 0

    // 変換候補の選択状態（「空白変換」押下で順送り）。selectedIndex<0 は未選択（読み表示中）。
    private var candidates: List<String> = emptyList()
    private var selectedIndex = -1

    override fun onCreate() {
        super.onCreate()
        // 変換: Mozc（未初期化時は軽量辞書へフォールバック）
        mozc = MozcEngine(this)
        converter = MozcConverter(mozc, SimpleDictionaryConverter(this))
        Thread {
            val initialized = mozc.initialize()
            Log.i(TAG, "Mozc initialized=$initialized version=${mozc.dataVersion()}")
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
            onReplaceLast = { prev, next -> replaceLastCommitted(prev, next) }
            onModeChanged = { finishComposingIfAny() }
            onCursorLeft = { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT) }
            onCursorRight = { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT) }
            onGlobe = { switchIme() }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.BG)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            addView(candidateView, matchWidthWrapHeight())
            addView(keyboard, matchWidthWrapHeight())
        }
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        secureField = isSecureField(info)
        resetComposing()
    }

    // ---- 入力ハンドラ ----

    private fun appendKana(kana: String) {
        if (secureField) {
            // 機密欄: 変換・候補・エンジン送信を行わず直接確定する
            commitText(kana)
            return
        }
        if (selectedIndex >= 0) {
            // 変換候補プレビュー中の新規入力 → 選択候補を確定してから新しい入力を開始
            currentInputConnection?.finishComposingText()
            resetComposing()
        }
        composing.append(kana)
        updateComposing()
        toggleRing = CharacterTables.toggleRingFor(composing.last())
        toggleIndex = 0
    }

    private fun updateComposing() {
        currentInputConnection?.setComposingText(composing, 1)
        // 読みが変わったので候補の選択状態はリセットする
        selectedIndex = -1
        if (composing.isNotEmpty()) {
            candidates = converter.convert(composing.toString())
            candidateView.setCandidates(candidates, selectedIndex)
        } else {
            candidates = emptyList()
            candidateView.clear()
        }
    }

    /**
     * 「空白変換」押下。未確定が無ければ全角スペースを入力。
     * 未確定があれば、押下ごとに候補を順に選択移動し、未確定表示を選択候補に更新する。
     */
    private fun convertOrSpace() {
        if (composing.isEmpty()) {
            commitText(FULL_WIDTH_SPACE)
            return
        }
        if (candidates.isEmpty()) {
            candidates = converter.convert(composing.toString())
            if (candidates.isEmpty()) return
        }
        selectedIndex = if (selectedIndex < 0) 0 else (selectedIndex + 1) % candidates.size
        currentInputConnection?.setComposingText(candidates[selectedIndex], 1)
        candidateView.setCandidates(candidates, selectedIndex)
    }

    private fun commitCandidate(text: String) {
        commitText(text)
        resetComposing()
    }

    private fun toggleDakuten() {
        if (composing.isEmpty()) return
        val next = CharacterTables.dakutenCycle[composing.last()] ?: return
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
            currentInputConnection?.finishComposingText()
            resetComposing()
        } else {
            sendDefaultEditorAction(true)
        }
    }

    /** 英字・数字・記号を直接確定する（未確定があれば先に確定）。 */
    private fun commitDirect(text: String) {
        finishComposingIfAny()
        commitText(text)
    }

    /** モード切替時などに、未確定をそのまま確定してクリアする。 */
    private fun finishComposingIfAny() {
        if (composing.isEmpty()) return
        currentInputConnection?.finishComposingText()
        resetComposing()
    }

    /** ↺ 最後の入力文字を、そのキーの循環リング `[文字→番号→小文字]` で次へ送る。 */
    private fun toggleLastChar() {
        if (composing.isEmpty() || toggleRing.size <= 1) return
        if (composing.last() != toggleRing[toggleIndex]) return
        toggleIndex = (toggleIndex + 1) % toggleRing.size
        composing.setCharAt(composing.length - 1, toggleRing[toggleIndex])
        updateComposing()
    }

    /** ↺（英字/数字）直前に確定した文字 [prev] を [next] へ置換する。想定と一致する時のみ実行。 */
    private fun replaceLastCommitted(prev: String, next: String) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(prev.length, 0)?.toString()
        if (before != prev) return
        ic.deleteSurroundingText(prev.length, 0)
        ic.commitText(next, 1)
    }

    /** 🌐 次のIMEへ切替。不可ならIMEピッカーを表示する。 */
    private fun switchIme() {
        runCatching {
            if (!switchToNextInputMethod(false)) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }.onFailure { Log.w(TAG, "switchIme failed", it) }
    }

    // ---- ヘルパー ----

    private fun commitText(text: CharSequence) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun resetComposing() {
        composing.setLength(0)
        toggleRing = emptyList()
        toggleIndex = 0
        candidates = emptyList()
        selectedIndex = -1
        if (::candidateView.isInitialized) candidateView.clear()
    }

    private fun matchWidthWrapHeight() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    )

    /** パスワード等の機密入力欄、または個人化学習を拒否する欄かを判定する。 */
    private fun isSecureField(info: EditorInfo?): Boolean {
        if (info == null) return false
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val inputClass = info.inputType and InputType.TYPE_MASK_CLASS
        // テキスト系の秘匿入力欄（通常/可視/Web）
        val textPwField = inputClass == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            )
        // 数値系の秘匿入力欄（PIN等）
        val numberPwField = inputClass == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        val noPersonalizedLearning =
            (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        return textPwField || numberPwField || noPersonalizedLearning
    }

    private companion object {
        const val TAG = "JapaneseImeService"
        const val FULL_WIDTH_SPACE = "　"
    }
}
