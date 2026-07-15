package jp.nihongo.ime

/**
 * かな漢字変換エンジンの抽象。
 * 現在は軽量な辞書引き実装を使うが、将来 Mozc(JNI) 実装に差し替えられるよう
 * この interface の背後に隠蔽しておく。
 */
interface Converter {
    /**
     * 読み（ひらがな）を変換候補リストに変換する。
     * @return 候補（表示順）。先頭が第一候補。
     */
    fun convert(reading: String): List<String>
}

/** ひらがな→カタカナ変換 */
fun toKatakana(hiragana: String): String = buildString {
    for (c in hiragana) {
        if (c in 'ぁ'..'ゖ') append(c + 0x60) else append(c)
    }
}
