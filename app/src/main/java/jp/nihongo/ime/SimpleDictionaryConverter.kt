package jp.nihongo.ime

import android.content.Context

/**
 * assets/dictionary.tsv を読み込む軽量な辞書引きコンバータ。
 * 形式: 「読み\t表記」1行1エントリ。同一読みに複数表記可。
 *
 * これは Mozc 導入前の暫定実装。連文節変換は未対応で、
 * 読み全体に一致する候補＋カタカナ＋ひらがなを返す。
 */
class SimpleDictionaryConverter(context: Context) : Converter {

    // 読み -> 表記リスト（登録順を保持）
    private val dict: Map<String, List<String>>

    init {
        val map = LinkedHashMap<String, MutableList<String>>()
        runCatching {
            context.assets.open("dictionary.tsv").bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.isBlank() || line.startsWith("#")) continue
                    val parts = line.split('\t')
                    if (parts.size < 2) continue
                    val reading = parts[0].trim()
                    val surface = parts[1].trim()
                    if (reading.isEmpty() || surface.isEmpty()) continue
                    map.getOrPut(reading) { mutableListOf() }.add(surface)
                }
            }
        }
        dict = map
    }

    override fun convert(reading: String): List<String> {
        if (reading.isEmpty()) return emptyList()
        val result = LinkedHashSet<String>()
        // 1. 辞書の完全一致候補
        dict[reading]?.let { result.addAll(it) }
        // 2. カタカナ
        result.add(toKatakana(reading))
        // 3. ひらがな（そのまま）
        result.add(reading)
        return result.toList()
    }
}
