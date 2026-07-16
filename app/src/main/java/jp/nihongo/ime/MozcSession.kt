package jp.nihongo.ime

import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Command
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Input
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.KeyEvent
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Output
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Request
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.SessionCommand

/**
 * Mozc の対話的セッション。連文節変換（Space変換・文節フォーカス・伸縮・候補選択・確定）を
 * キーイベント/コマンドで駆動する。ステートフル（合成・変換状態を保持）。
 */
class MozcSession(private val engine: MozcEngine) {

    private var id: Long = 0

    private fun eval(input: Input): Output {
        val cmd = Command.newBuilder().setInput(input).build()
        return Command.parseFrom(engine.evalCommand(cmd.toByteArray())).output
    }

    private fun key(id: Long) = Input.newBuilder().setType(Input.CommandType.SEND_KEY).setId(id)
    private fun command(id: Long, type: SessionCommand.CommandType) =
        Input.newBuilder().setType(Input.CommandType.SEND_COMMAND).setId(id)
            .setCommand(SessionCommand.newBuilder().setType(type))

    fun ensure() {
        if (id != 0L) return
        id = eval(Input.newBuilder().setType(Input.CommandType.CREATE_SESSION).build()).id
        // 連文節（デスクトップ型）変換のため mixed_conversion は無効
        val request = Request.newBuilder()
            .setMixedConversion(false)
            .setZeroQuerySuggestion(false)
            .build()
        eval(
            Input.newBuilder().setType(Input.CommandType.SET_REQUEST).setId(id)
                .setRequest(request).build(),
        )
    }

    /** かな1文字を合成に追加。 */
    fun insertKana(ch: String): Output =
        eval(key(id).setKey(KeyEvent.newBuilder().setKeyString(ch)).build())

    private fun special(sp: KeyEvent.SpecialKey, shift: Boolean = false): Output {
        val ke = KeyEvent.newBuilder().setSpecialKey(sp)
        if (shift) ke.addModifierKeys(KeyEvent.ModifierKey.SHIFT)
        return eval(key(id).setKey(ke).build())
    }

    fun space(): Output = special(KeyEvent.SpecialKey.SPACE)
    fun focusLeft(): Output = special(KeyEvent.SpecialKey.LEFT)
    fun focusRight(): Output = special(KeyEvent.SpecialKey.RIGHT)
    fun shrink(): Output = special(KeyEvent.SpecialKey.LEFT, shift = true)
    fun expand(): Output = special(KeyEvent.SpecialKey.RIGHT, shift = true)
    fun commit(): Output = special(KeyEvent.SpecialKey.ENTER)
    fun backspace(): Output = special(KeyEvent.SpecialKey.BACKSPACE)

    /** 注目文節の候補を id で選択。 */
    fun selectCandidate(candidateId: Int): Output {
        val cmd = SessionCommand.newBuilder()
            .setType(SessionCommand.CommandType.SELECT_CANDIDATE)
            .setId(candidateId)
        return eval(
            Input.newBuilder().setType(Input.CommandType.SEND_COMMAND).setId(id)
                .setCommand(cmd).build(),
        )
    }

    fun revert(): Output = eval(command(id, SessionCommand.CommandType.REVERT).build())
}
