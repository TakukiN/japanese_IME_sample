package jp.nihongo.ime

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 変換候補バー（業務用ダークテーマ）。
 * 右端に設定用ギアアイコンを持ち、**候補が表示されている間は非表示**にする
 * （候補が無いアイドル時のみギアを見せる）。候補タップで [onPick]、ギアで [onGear] を通知。
 */
class CandidateView(context: Context) : LinearLayout(context) {

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dpX(6), 0, dpX(6), 0)
    }
    private val scroller = HorizontalScrollView(context).apply {
        isFillViewport = true
        isHorizontalScrollBarEnabled = false
        addView(
            row,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }
    private val gear = TextView(context).apply {
        text = "⚙"
        gravity = Gravity.CENTER
        includeFontPadding = false
        setTextColor(Theme.TEXT_SUB)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        setPadding(dpX(14), 0, dpX(14), 0)
    }
    private val chips = mutableListOf<TextView>()

    var onPick: ((index: Int) -> Unit)? = null
    var onGear: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL
        setBackgroundColor(Theme.CAND_BG)
        minimumHeight = dpX(48)
        addView(scroller, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(gear, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        gear.setOnClickListener { onGear?.invoke() }
    }

    /** 候補を表示する。候補があればギアを隠す。 */
    fun setCandidates(candidates: List<String>, selectedIndex: Int = -1) {
        row.removeAllViews()
        chips.clear()
        candidates.forEachIndexed { index, candidate ->
            val primary = index == selectedIndex || (selectedIndex < 0 && index == 0)
            val chip = makeChip(candidate, primary, index)
            chips += chip
            row.addView(chip)
        }
        gear.visibility = if (candidates.isEmpty()) View.VISIBLE else View.GONE
        scrollToSelected(selectedIndex)
    }

    /** 候補をクリアし、ギアを再表示する。 */
    fun clear() {
        row.removeAllViews()
        chips.clear()
        gear.visibility = View.VISIBLE
    }

    private fun scrollToSelected(selectedIndex: Int) {
        if (selectedIndex <= 0 || selectedIndex >= chips.size) {
            scroller.scrollX = 0
            return
        }
        val target = chips[selectedIndex]
        post { scroller.smoothScrollTo(target.left, 0) }
    }

    private fun makeChip(text: String, primary: Boolean, index: Int): TextView = TextView(context).apply {
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
        setOnClickListener { onPick?.invoke(index) }
    }

    private fun dpX(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
