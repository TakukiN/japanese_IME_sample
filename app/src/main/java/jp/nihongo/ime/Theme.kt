package jp.nihongo.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable

/**
 * 業務用途向けのプロフェッショナルなダークテーマ。
 * 落ち着いたチャコール基調＋ティール系アクセント、角丸キーで統一する。
 */
object Theme {
    // 背景・面
    const val BG = 0xFF15181D.toInt()          // キーボード背景（濃いチャコール）
    const val KEY = 0xFF272C34.toInt()          // かなキー
    const val KEY_PRESSED = 0xFF323945.toInt()  // 押下時
    const val KEY_FUNC = 0xFF1D2127.toInt()     // 機能キー
    const val KEY_BORDER = 0xFF3A414B.toInt()   // キー境界線
    const val CAND_BG = 0xFF10131A.toInt()      // 候補バー背景
    const val CAND_CHIP = 0xFF20252E.toInt()    // 候補チップ

    // 文字
    const val TEXT = 0xFFE7EAEE.toInt()         // 主要文字
    const val TEXT_SUB = 0xFF9BA3AE.toInt()     // 補助文字（記号・機能ラベル）

    // アクセント（業務用の落ち着いたティール）
    const val ACCENT = 0xFF3FB8AF.toInt()
    const val ACCENT_DK = 0xFF2A8079.toInt()
    const val ON_ACCENT = 0xFF0B1114.toInt()

    /** プロっぽい業務用サンセリフ（中太）。日本語グリフはNoto Sans CJKが自動適用される */
    val fontMedium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val fontRegular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

    /** 角丸＋境界線のキー背景を生成 */
    fun keyBackground(fill: Int, stroke: Int = KEY_BORDER, radiusDp: Float = 9f, ctx: Context): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * ctx.resources.displayMetrics.density
            setColor(fill)
            setStroke((1.2f * ctx.resources.displayMetrics.density).toInt(), stroke)
        }

    fun chipBackground(fill: Int, stroke: Int, ctx: Context): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f * ctx.resources.displayMetrics.density
            setColor(fill)
            setStroke((1f * ctx.resources.displayMetrics.density).toInt(), stroke)
        }
}
