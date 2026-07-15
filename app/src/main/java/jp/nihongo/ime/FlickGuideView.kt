package jp.nihongo.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * フリック方向ガイド（十字ポップアップ）。
 * 中央＝あ段、左＝い段、上＝う段、右＝え段、下＝お段 を表示し、
 * active で示された方向を強調する。
 * chars は [中央, 左, 上, 右, 下] の順。
 */
class FlickGuideView(context: Context) : View(context) {

    var chars: List<String> = emptyList()
        set(value) { field = value; invalidate() }

    /** 選択中の方向: 0=中央,1=左,2=上,3=右,4=下 */
    var active: Int = 0
        set(value) { field = value; invalidate() }

    private val cell = dp(48).toFloat()
    private val gap = dp(4).toFloat()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.KEY_PRESSED }
    private val hlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.ACCENT }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.TEXT
        textAlign = Paint.Align.CENTER
        textSize = dp(22).toFloat()
        typeface = Theme.fontMedium
    }
    private val textHlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Theme.ON_ACCENT
        textAlign = Paint.Align.CENTER
        textSize = dp(24).toFloat()
        typeface = Theme.fontMedium
    }

    // 各方向のセル左上座標 (col,row) : 3x3 の十字配置
    private val slots = listOf(
        1 to 1, // 中央
        0 to 1, // 左
        1 to 0, // 上
        2 to 1, // 右
        1 to 2  // 下
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (cell * 3 + gap * 2).toInt()
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        if (chars.size < 5) return
        for (i in 0 until 5) {
            val (col, row) = slots[i]
            val left = col * (cell + gap)
            val top = row * (cell + gap)
            val rect = RectF(left, top, left + cell, top + cell)
            val isActive = i == active
            canvas.drawRoundRect(rect, dp(8).toFloat(), dp(8).toFloat(),
                if (isActive) hlPaint else bgPaint)
            val cx = rect.centerX()
            val cy = rect.centerY() - (textPaint.ascent() + textPaint.descent()) / 2
            val tp = if (isActive) textHlPaint else textPaint
            canvas.drawText(chars[i], cx, cy, tp)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
