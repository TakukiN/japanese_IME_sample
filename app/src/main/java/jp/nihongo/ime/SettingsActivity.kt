package jp.nihongo.ime

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView

/**
 * IME設定画面。テキスト出力手段（OutputMethod）を選択する。
 * IMEのシステム設定（言語と入力 > 日本語IME > 設定）およびランチャーから開ける。
 */
class SettingsActivity : Activity() {

    private val options = listOf(
        OutputMethod.COMMIT_TEXT to "commitText（標準・推奨）\n日本語/変換フル対応",
        OutputMethod.KEY_EVENT to "KeyEvent\n英数字のみ（日本語は自動でcommitText）",
        OutputMethod.CLIPBOARD to "クリップボード貼付\n非機密のみ・元内容を復元",
        OutputMethod.ACCESSIBILITY to "AccessibilityService\n要アクセシビリティ許可",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.settings_title)
        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(sectionLabel("テキスト出力手段"))

        val current = OutputMethod.load(this)
        val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        options.forEachIndexed { index, (method, label) ->
            group.addView(RadioButton(this).apply {
                id = index + 1
                text = label
                setTextColor(Theme.TEXT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(dp(8), dp(12), dp(8), dp(12))
                isChecked = method == current
            })
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            options.getOrNull(checkedId - 1)?.let { OutputMethod.save(this, it.first) }
        }
        root.addView(group)

        root.addView(note("機密入力欄（パスワード等）では、選択に関わらず安全のため commitText を使用します。"))

        root.addView(actionButton("アクセシビリティ設定を開く") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(actionButton("オープンソースライセンス") {
            startActivity(Intent(this, LicensesActivity::class.java))
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Theme.BG)
            addView(root)
        })
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Theme.ACCENT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Theme.fontMedium
        setPadding(0, dp(8), 0, dp(4))
    }

    private fun note(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Theme.TEXT_SUB)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(0, dp(12), 0, dp(16))
    }

    private fun actionButton(label: String, onClick: () -> Unit): View =
        Button(this).apply {
            text = label
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) }
            gravity = Gravity.CENTER
        }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
