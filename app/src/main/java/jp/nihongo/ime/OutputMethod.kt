package jp.nihongo.ime

import android.content.Context

/**
 * テキストを対象アプリへ出力する手段。設定画面で選択し、SharedPreferences に保存する。
 *
 * - COMMIT_TEXT: InputConnection.commitText（標準・日本語/変換フル対応）
 * - KEY_EVENT:   KeyEvent 送出（英数字のみ、日本語は commitText フォールバック）
 * - CLIPBOARD:   クリップボードへ置き貼り付け（非機密のみ・元内容を復元）
 * - ACCESSIBILITY: AccessibilityService 経由で編集欄に直接設定（要権限）
 */
enum class OutputMethod {
    COMMIT_TEXT,
    KEY_EVENT,
    CLIPBOARD,
    ACCESSIBILITY;

    companion object {
        private const val PREFS = "ime_settings"
        private const val KEY = "output_method"

        fun load(context: Context): OutputMethod {
            val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, COMMIT_TEXT.name)
            return runCatching { valueOf(name!!) }.getOrDefault(COMMIT_TEXT)
        }

        fun save(context: Context, method: OutputMethod) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, method.name).apply()
        }
    }
}
