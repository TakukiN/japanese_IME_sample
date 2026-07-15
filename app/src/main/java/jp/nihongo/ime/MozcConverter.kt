package jp.nihongo.ime

import android.util.Log
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Command
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Input
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.KeyEvent
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Output
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Request
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.SessionCommand

/**
 * Mozc エンジンによるかな漢字変換。
 * 読み（ひらがな）を Mozc セッションに key_string として送り、候補を取得する。
 * エンジン未初期化時は [fallback]（軽量辞書）を使う。
 */
class MozcConverter(
    private val engine: MozcEngine,
    private val fallback: Converter
) : Converter {

    private var sessionId: Long = 0

    private fun eval(input: Input): Output {
        val cmd = Command.newBuilder().setInput(input).build()
        val outBytes = engine.evalCommand(cmd.toByteArray())
        return Command.parseFrom(outBytes).output
    }

    private fun ensureSession() {
        if (sessionId != 0L) return
        val out = eval(Input.newBuilder().setType(Input.CommandType.CREATE_SESSION).build())
        sessionId = out.id
        // モバイル向け設定: 予測・サジェストを有効化
        val request = Request.newBuilder()
            .setMixedConversion(true)
            .setZeroQuerySuggestion(true)
            .build()
        eval(
            Input.newBuilder()
                .setType(Input.CommandType.SET_REQUEST)
                .setId(sessionId)
                .setRequest(request)
                .build()
        )
    }

    @Synchronized
    override fun convert(reading: String): List<String> {
        if (reading.isEmpty()) return emptyList()
        if (!engine.initialized) return fallback.convert(reading)
        return try {
            ensureSession()
            // 前回の未確定を破棄
            eval(
                Input.newBuilder()
                    .setType(Input.CommandType.SEND_COMMAND)
                    .setId(sessionId)
                    .setCommand(SessionCommand.newBuilder().setType(SessionCommand.CommandType.REVERT))
                    .build()
            )
            // 読みを1文字ずつ送信して合成
            var out: Output? = null
            for (ch in reading) {
                out = eval(
                    Input.newBuilder()
                        .setType(Input.CommandType.SEND_KEY)
                        .setId(sessionId)
                        .setKey(KeyEvent.newBuilder().setKeyString(ch.toString()))
                        .build()
                )
            }
            val candidates = out?.allCandidateWords?.candidatesList
                ?.map { it.value }
                ?.filter { it.isNotEmpty() }
                ?.distinct()
                .orEmpty()
            if (candidates.isNotEmpty()) candidates else fallback.convert(reading)
        } catch (t: Throwable) {
            Log.e(TAG, "convert failed", t)
            fallback.convert(reading)
        }
    }

    companion object {
        private const val TAG = "MozcConverter"
    }
}
