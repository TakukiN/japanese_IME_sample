package jp.nihongo.ime

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.text.InputType
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout

/**
 * 日本語IME本体。
 *
 * フリックキーボードで読み（ひらがな）を蓄積し、候補を提示、確定する。変換は [Converter] に委譲。
 * 確定テキストの対象アプリへの出力手段は設定（[OutputMethod]）で切り替える:
 * commitText / KeyEvent / クリップボード / AccessibilityService。
 *
 * セキュリティ（OWASP MASVS）:
 * - 機密入力欄（[isSecureField]）では出力手段に関わらず commitText を使い、変換・候補・
 *   エンジン送信・クリップボード/アクセシビリティ経由の出力を行わない。
 * - INTERNET 権限を持たず、入力内容の外部送信は行わない。打鍵をログ出力しない。
 */
class JapaneseImeService : InputMethodService() {

    private lateinit var converter: Converter
    private lateinit var mozc: MozcEngine
    private lateinit var candidateView: CandidateView

    private val composing = StringBuilder()
    private var secureField = false

    private var toggleRing: List<Char> = emptyList()
    private var toggleIndex = 0

    private var candidates: List<String> = emptyList()
    private var selectedIndex = -1

    private var outputMethod = OutputMethod.COMMIT_TEXT
    private val keyCharacterMap by lazy { KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD) }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    // クリップボード方式の一括出力バッファ（貼付=Toastの回数を減らす）
    private val pendingOutput = StringBuilder()
    private val flushRunnable = Runnable { flushPendingOutput() }

    /** 対象欄に未確定（下線）プレビューを出すのは commitText 手段の時だけ。 */
    private val previewInTarget: Boolean
        get() = outputMethod == OutputMethod.COMMIT_TEXT

    override fun onCreate() {
        super.onCreate()
        mozc = MozcEngine(this)
        converter = MozcConverter(mozc, SimpleDictionaryConverter(this))
        outputMethod = OutputMethod.load(this)
        Thread {
            val initialized = mozc.initialize()
            Log.i(TAG, "Mozc initialized=$initialized version=${mozc.dataVersion()}")
        }.start()
    }

    override fun onCreateInputView(): View {
        candidateView = CandidateView(this).apply {
            onPick = { commitCandidate(it) }
            onGear = { openSettings() }
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

    private fun openSettings() {
        runCatching {
            requestHideSelf(0)
            startActivity(
                Intent(this, SettingsActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
            )
        }.onFailure { Log.w(TAG, "openSettings failed", it) }
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        // 設定変更を反映
        outputMethod = OutputMethod.load(this)
        secureField = isSecureField(info)
        // 前の入力欄の未貼付バッファは破棄（他欄への混入防止）
        mainHandler.removeCallbacks(flushRunnable)
        pendingOutput.setLength(0)
        resetComposing()
    }

    override fun onFinishInput() {
        // 入力欄を離れる前に未貼付分を確定
        flushPendingOutput()
        super.onFinishInput()
    }

    // ---- 入力ハンドラ ----

    private fun appendKana(kana: String) {
        if (secureField) {
            commitViaInputConnection(kana)
            return
        }
        if (selectedIndex >= 0) {
            // 変換候補プレビュー中の新規入力 → 選択候補を確定してから継続
            finishComposingIfAny()
        }
        composing.append(kana)
        updateComposing()
        toggleRing = CharacterTables.toggleRingFor(composing.last())
        toggleIndex = 0
    }

    private fun updateComposing() {
        if (previewInTarget) currentInputConnection?.setComposingText(composing, 1)
        selectedIndex = -1
        if (composing.isNotEmpty()) {
            candidates = converter.convert(composing.toString())
            candidateView.setCandidates(candidates, selectedIndex)
        } else {
            candidates = emptyList()
            candidateView.clear()
        }
    }

    /** 「空白変換」: 未確定が無ければ空白入力。あれば押下ごとに候補を順に選択移動。 */
    private fun convertOrSpace() {
        if (composing.isEmpty()) {
            outputText(FULL_WIDTH_SPACE)
            return
        }
        if (candidates.isEmpty()) {
            candidates = converter.convert(composing.toString())
            if (candidates.isEmpty()) return
        }
        selectedIndex = if (selectedIndex < 0) 0 else (selectedIndex + 1) % candidates.size
        if (previewInTarget) currentInputConnection?.setComposingText(candidates[selectedIndex], 1)
        candidateView.setCandidates(candidates, selectedIndex)
    }

    private fun commitCandidate(text: String) {
        outputText(text)
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
        } else if (pendingOutput.isNotEmpty()) {
            // 未貼付バッファを1文字戻す（貼付=Toastを発生させない）
            pendingOutput.deleteCharAt(pendingOutput.length - 1)
            scheduleClipboardFlush()
        } else {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
    }

    private fun enter() {
        when {
            composing.isNotEmpty() -> {
                finishComposingIfAny()
                flushPendingOutput()
            }
            pendingOutput.isNotEmpty() -> flushPendingOutput()
            else -> sendDefaultEditorAction(true)
        }
    }

    private fun commitDirect(text: String) {
        finishComposingIfAny()
        outputText(text)
    }

    /** 未確定があれば、選択候補（無ければ読み）を確定してクリアする。 */
    private fun finishComposingIfAny() {
        if (composing.isEmpty()) return
        val finalText = if (selectedIndex >= 0 && candidates.isNotEmpty()) {
            candidates[selectedIndex]
        } else {
            composing.toString()
        }
        outputText(finalText)
        resetComposing()
    }

    /** ↺（かな）未確定の最後の文字を循環トグルする。 */
    private fun toggleLastChar() {
        if (composing.isEmpty() || toggleRing.size <= 1) return
        if (composing.last() != toggleRing[toggleIndex]) return
        toggleIndex = (toggleIndex + 1) % toggleRing.size
        composing.setCharAt(composing.length - 1, toggleRing[toggleIndex])
        updateComposing()
    }

    /** ↺（英字/数字）直前に確定した文字 [prev] を [next] へ置換する。 */
    private fun replaceLastCommitted(prev: String, next: String) {
        val ic = currentInputConnection ?: return
        if (ic.getTextBeforeCursor(prev.length, 0)?.toString() != prev) return
        ic.deleteSurroundingText(prev.length, 0)
        outputText(next)
    }

    private fun switchIme() {
        runCatching {
            if (!switchToNextInputMethod(false)) {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        }.onFailure { Log.w(TAG, "switchIme failed", it) }
    }

    // ---- 出力手段のルーティング ----

    /** 確定テキストを、設定された手段で対象アプリへ出力する。機密欄は常に commitText。 */
    private fun outputText(text: String) {
        if (secureField) {
            commitViaInputConnection(text)
            return
        }
        when (outputMethod) {
            OutputMethod.COMMIT_TEXT -> commitViaInputConnection(text)
            OutputMethod.KEY_EVENT -> outputViaKeyEvent(text)
            OutputMethod.CLIPBOARD -> enqueueForClipboard(text)
            OutputMethod.ACCESSIBILITY -> outputViaAccessibility(text)
        }
    }

    private fun commitViaInputConnection(text: CharSequence) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun outputViaKeyEvent(text: String) {
        val ic = currentInputConnection ?: return
        val events = keyCharacterMap.getEvents(text.toCharArray())
        if (events != null) {
            for (event in events) ic.sendKeyEvent(event)
        } else {
            // 日本語等は KeyEvent 化不可 → commitText フォールバック
            ic.commitText(text, 1)
        }
    }

    /** クリップボード方式: 文字をバッファに溜め、間隔/一定量でまとめて貼り付ける（Toast削減）。 */
    private fun enqueueForClipboard(text: String) {
        pendingOutput.append(text)
        scheduleClipboardFlush()
    }

    private fun scheduleClipboardFlush() {
        mainHandler.removeCallbacks(flushRunnable)
        when {
            pendingOutput.length >= BATCH_MAX -> flushPendingOutput()
            pendingOutput.isNotEmpty() -> mainHandler.postDelayed(flushRunnable, FLUSH_DELAY_MS)
        }
    }

    /** 溜まった文字を1回だけ貼り付ける。 */
    private fun flushPendingOutput() {
        mainHandler.removeCallbacks(flushRunnable)
        if (pendingOutput.isEmpty()) return
        val text = pendingOutput.toString()
        pendingOutput.setLength(0)
        pasteViaClipboard(text)
    }

    private fun pasteViaClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val saved = clipboard.primaryClip
        val clip = ClipData.newPlainText("nihongo-ime", text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 中身をクリップボードプレビューに露出させない
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        val pasted = currentInputConnection?.performContextMenuAction(android.R.id.paste) == true
        if (!pasted) sendDownUpKeyEvents(KeyEvent.KEYCODE_PASTE)
        // 元のクリップボード内容を復元（漏洩・上書き防止）
        saved?.let { old ->
            mainHandler.postDelayed({ runCatching { clipboard.setPrimaryClip(old) } }, 600)
        }
    }

    private fun outputViaAccessibility(text: String) {
        val handled = ImeAccessibilityService.instance?.appendText(text) == true
        if (!handled) commitViaInputConnection(text) // 未有効/失敗時フォールバック
    }

    // ---- ヘルパー ----

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

    private fun isSecureField(info: EditorInfo?): Boolean {
        if (info == null) return false
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val inputClass = info.inputType and InputType.TYPE_MASK_CLASS
        val textPwField = inputClass == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            )
        val numberPwField = inputClass == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        val noPersonalizedLearning =
            (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        return textPwField || numberPwField || noPersonalizedLearning
    }

    private companion object {
        const val TAG = "JapaneseImeService"
        const val FULL_WIDTH_SPACE = "　"

        // クリップボード一括出力: この文字数に達するか、最後の入力から遅延後にまとめて貼付
        const val BATCH_MAX = 24
        const val FLUSH_DELAY_MS = 700L
    }
}
