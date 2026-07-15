package jp.nihongo.ime

/**
 * かな入力に関する変換テーブルと純粋ロジックを集約する。
 *
 * UI（[FlickKeyboardView]）や IME 制御（[JapaneseImeService]）から入力ロジックを分離し、
 * 副作用のない純関数として単体テスト可能に保つ。
 */
object CharacterTables {

    /** 濁点・半濁点・小文字トグル。ある文字 → 次の文字（末尾は先頭へ循環）。 */
    val dakutenCycle: Map<Char, Char> = buildCycle(
        "かが", "きぎ", "くぐ", "けげ", "こご",
        "さざ", "しじ", "すず", "せぜ", "そぞ",
        "ただ", "ちぢ", "てで", "とど",
        "つづっ",
        "はばぱ", "ひびぴ", "ふぶぷ", "へべぺ", "ほぼぽ",
        "うゔ",
        "あぁ", "いぃ", "えぇ", "おぉ",
        "やゃ", "ゆゅ", "よょ", "わゎ",
    )

    /** 各かなが属するテンキーの番号（例: 'か' → '2'）。 */
    private val keyNumberOf: Map<Char, Char> = buildMap {
        listOf(
            '1' to "あいうえお", '2' to "かきくけこ", '3' to "さしすせそ",
            '4' to "たちつてと", '5' to "なにぬねの", '6' to "はひふへほ",
            '7' to "まみむめも", '8' to "やゆよ", '9' to "らりるれろ", '0' to "わをん",
        ).forEach { (number, chars) -> chars.forEach { put(it, number) } }
    }

    /** 小文字を持つかな（例: 'あ' → 'ぁ'）。 */
    private val smallOf: Map<Char, Char> = mapOf(
        'あ' to 'ぁ', 'い' to 'ぃ', 'う' to 'ぅ', 'え' to 'ぇ', 'お' to 'ぉ',
        'つ' to 'っ', 'や' to 'ゃ', 'ゆ' to 'ゅ', 'よ' to 'ょ', 'わ' to 'ゎ',
    )

    /**
     * ↺ キー用の文字循環リング `[文字, 番号, 小文字]` を返す。
     * 該当がなければ文字のみの単一要素リストを返す。
     */
    fun toggleRingFor(c: Char): List<Char> = buildList {
        add(c)
        keyNumberOf[c]?.let { add(it) }
        smallOf[c]?.let { add(it) }
    }

    /** 各巡回文字列から「次の文字」マップを構築する（末尾→先頭で循環）。 */
    private fun buildCycle(vararg rings: String): Map<Char, Char> = buildMap {
        for (ring in rings) {
            for (i in ring.indices) {
                put(ring[i], ring[(i + 1) % ring.length])
            }
        }
    }
}
