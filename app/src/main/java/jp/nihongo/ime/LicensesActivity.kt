package jp.nihongo.ime

import android.app.Activity
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.ScrollView
import android.widget.TextView

/**
 * オープンソースライセンス表示画面。
 * IME設定（システムの「言語と入力 > NihongoIME > 設定」）およびランチャーから開ける。
 * assets/licenses.txt を読み込んでスクロール表示する。
 */
class LicensesActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.licenses_title)

        val text = runCatching {
            assets.open("licenses.txt").bufferedReader().use { it.readText() }
        }.getOrDefault("ライセンス情報を読み込めませんでした。")

        val pad = (16 * resources.displayMetrics.density).toInt()
        val body = TextView(this).apply {
            this.text = text
            setTextColor(Theme.TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(pad, pad, pad, pad)
            setTextIsSelectable(true)
            gravity = Gravity.START
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Theme.BG)
            addView(body)
        }
        setContentView(scroll)
    }
}
