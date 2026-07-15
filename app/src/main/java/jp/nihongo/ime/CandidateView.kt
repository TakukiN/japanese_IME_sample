package jp.nihongo.ime

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 変換候補を横スクロールで表示するビュー（業務用ダークテーマ）。
 * 選択中の候補（`selectedIndex`）をアクセント色で強調し、可視位置へスクロールする。
 * 候補タップで [onPick] を通知する。
 */
class CandidateView(context: Context) : HorizontalScrollView(context) {

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dpX(6), 0, dpX(6), 0)
    }
    private val chips = mutableListOf<TextView>()

    var onPick: ((String) -> Unit)? = null

    init {
        isFillViewport = true
        setBackgroundColor(Theme.CAND_BG)
        isHorizontalScrollBarEnabled = false
        addView(
            row,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        minimumHeight = dpX(48)
    }

    /**
     * 候補を表示する。
     * @param selectedIndex 強調する候補の位置。負値なら先頭を控えめに強調（未選択状態）。
     */
    fun setCandidates(candidates: List<String>, selectedIndex: Int = -1) {
        row.removeAllViews()
        chips.clear()
        candidates.forEachIndexed { index, candidate ->
            val primary = index == selectedIndex || (selectedIndex < 0 && index == 0)
            val chip = makeChip(candidate, primary)
            chips += chip
            row.addView(chip)
        }
        scrollToSelected(selectedIndex)
    }

    fun clear() {
        row.removeAllViews()
        chips.clear()
    }

    private fun scrollToSelected(selectedIndex: Int) {
        if (selectedIndex <= 0 || selectedIndex >= chips.size) {
            scrollX = 0
            return
        }
        val target = chips[selectedIndex]
        post { smoothScrollTo(target.left, 0) }
    }

    private fun makeChip(text: String, primary: Boolean): TextView = TextView(context).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
        typeface = Theme.fontMedium
        setTextColor(if (primary) Theme.ON_ACCENT else Theme.TEXT)
        setPadding(dpX(18), dpX(9), dpX(18), dpX(9))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(dpX(3), dpX(7), dpX(3), dpX(7)) }
        background = if (primary) {
            Theme.chipBackground(Theme.ACCENT, Theme.ACCENT_DK, context)
        } else {
            Theme.chipBackground(Theme.CAND_CHIP, Theme.KEY_BORDER, context)
        }
        setOnClickListener { onPick?.invoke(text) }
    }

    private fun dpX(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
