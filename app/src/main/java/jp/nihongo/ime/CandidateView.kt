package jp.nihongo.ime

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 変換候補を横スクロール表示するビュー（業務用ダークテーマ）。
 * 第一候補はアクセント色で強調。候補タップで onPick を通知する。
 */
class CandidateView(context: Context) : HorizontalScrollView(context) {

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dpX(6), 0, dpX(6), 0)
    }

    var onPick: ((String) -> Unit)? = null

    init {
        isFillViewport = true
        setBackgroundColor(Theme.CAND_BG)
        isHorizontalScrollBarEnabled = false
        addView(
            row,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        minimumHeight = dpX(48)
    }

    fun setCandidates(candidates: List<String>) {
        row.removeAllViews()
        candidates.forEachIndexed { i, c -> row.addView(makeChip(c, i == 0)) }
        scrollX = 0
    }

    fun clear() = row.removeAllViews()

    private fun makeChip(text: String, primary: Boolean): TextView = TextView(context).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
        typeface = Theme.fontMedium
        setTextColor(if (primary) Theme.ON_ACCENT else Theme.TEXT)
        setPadding(dpX(18), dpX(9), dpX(18), dpX(9))
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(dpX(3), dpX(7), dpX(3), dpX(7))
        layoutParams = lp
        background = if (primary)
            Theme.chipBackground(Theme.ACCENT, Theme.ACCENT_DK, context)
        else
            Theme.chipBackground(Theme.CAND_CHIP, Theme.KEY_BORDER, context)
        setOnClickListener { onPick?.invoke(text) }
    }

    private fun dpX(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
