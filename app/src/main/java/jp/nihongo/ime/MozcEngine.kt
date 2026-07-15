package jp.nihongo.ime

import android.content.Context
import android.util.Log
import com.google.android.apps.inputmethod.libs.mozc.session.MozcJNI
import java.io.File

/**
 * libmozc.so のライフサイクルを管理するラッパ。
 * 辞書データ(mozc.data)を assets から内部ストレージへ展開し、エンジンを初期化する。
 */
class MozcEngine(private val context: Context) {

    var initialized = false
        private set

    /** ネイティブエンジンを初期化する。成功可否を返す。 */
    fun initialize(): Boolean {
        if (initialized) return true
        return try {
            System.loadLibrary("mozc")
            if (!MozcJNI.initialize()) {
                Log.e(TAG, "MozcJNI.initialize() failed")
                return false
            }
            val dataFile = copyAssetToFiles("mozc.data")
            val profileDir = File(context.filesDir, "mozc_profile").apply { mkdirs() }
            val ok = MozcJNI.onPostLoad(profileDir.absolutePath, dataFile.absolutePath)
            if (!ok) {
                Log.e(TAG, "MozcJNI.onPostLoad() failed")
                return false
            }
            initialized = true
            Log.i(TAG, "Mozc initialized. dataVersion=${MozcJNI.getDataVersion()}")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Mozc initialization error", t)
            false
        }
    }

    fun dataVersion(): String = if (initialized) MozcJNI.getDataVersion() else ""

    /** commands.Command のシリアライズbytesを評価し、結果bytesを返す。 */
    fun evalCommand(commandBytes: ByteArray): ByteArray = MozcJNI.evalCommand(commandBytes)

    /** assets/<name> を filesDir/<name> にコピーして File を返す（既存はサイズ一致でスキップ）。 */
    private fun copyAssetToFiles(name: String): File {
        val out = File(context.filesDir, name)
        val assetSize = context.assets.openFd(name).use { it.length }
        if (out.exists() && out.length() == assetSize) return out
        context.assets.open(name).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }

    companion object {
        private const val TAG = "MozcEngine"
    }
}
