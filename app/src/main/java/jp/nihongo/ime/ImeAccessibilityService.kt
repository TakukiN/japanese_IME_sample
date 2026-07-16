package jp.nihongo.ime

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * InputConnection を使わずに文字入力するための補助アクセシビリティサービス。
 * フォーカス中の編集ノードに ACTION_SET_TEXT で直接テキストを設定する。
 *
 * 注意: 強力な権限（ユーザーが手動で有効化）を要する。OutputMethod.ACCESSIBILITY 選択時のみ使用。
 */
class ImeAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* 監視不要 */ }

    override fun onInterrupt() { /* no-op */ }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** フォーカス中の編集欄の末尾に [text] を追記する。成功可否を返す。 */
    fun appendText(text: String): Boolean {
        val node: AccessibilityNodeInfo =
            rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        return try {
            if (!node.isEditable) return false
            // 空欄のヒント（プレースホルダ）を既存本文と誤認しないよう空扱いにする
            val current = if (node.isShowingHintText) "" else node.text?.toString().orEmpty()
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    current + text,
                )
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } catch (t: Throwable) {
            Log.w(TAG, "appendText failed", t)
            false
        } finally {
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }

    companion object {
        private const val TAG = "ImeA11yService"

        @Volatile
        var instance: ImeAccessibilityService? = null
            private set
    }
}
