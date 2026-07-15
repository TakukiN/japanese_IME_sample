# サードパーティOSSライセンス

本アプリ（NihongoIME）が利用するOSSと商用利用可否。**配布物(APK)に含まれるものはすべて商用利用可能な寛容ライセンス**。
※ 本表は開発者による整理であり法的助言ではありません。商用配布前に各採用バージョンのLICENSEを確認してください。

## APKに同梱・配布されるも（要ライセンス表示）
| コンポーネント | ライセンス | 商用 | 主な義務 |
|---|---|---|---|
| Google Mozc（libmozc.so） | BSD-3-Clause | ✅可 | 著作権/ライセンス文・免責の同梱、Google名の無断宣伝利用禁止 |
| Mozc 辞書データ（mozc.data） | 奈良先端大(NAIST) / ICOT Free Software 等 | ✅可 | 各データの著作権/条件表示の同梱 |
| protobuf-java | BSD-3-Clause | ✅可 | 著作権/ライセンス文の同梱 |
| AndroidX core-ktx / appcompat | Apache License 2.0 | ✅可 | LICENSE/NOTICE同梱、変更点の明示 |
| Kotlin standard library | Apache License 2.0 | ✅可 | LICENSE/NOTICE同梱 |
| システムフォント（Roboto=Apache2.0 / Noto Sans CJK=SIL OFL 1.1） | ― | ✅可 | ※端末システムフォントを参照。**アプリに同梱していない**ため配布義務は生じない |

## ビルド時のみ使用（APKに含まれない＝配布義務なし）
| ツール | ライセンス | 備考 |
|---|---|---|
| bazelisk / Bazel | Apache 2.0 | Mozcビルド |
| Android NDK r29 | 独自(再配布可) | Mozcビルド。成果物(.so)配布はMozc側ライセンスに従う |
| Temurin JDK 17 | GPLv2 + Classpath Exception | ビルドツール。アプリに非同梱のため影響なし |
| protoc / uv / Gradle / AGP | BSD-3 / Apache 2.0 | いずれもビルド専用 |

## 商用配布前のアクション
1. アプリ内に **オープンソースライセンス表示画面**（一覧）を用意し、上記(同梱分)のライセンス全文を掲載する。
2. 特に **Mozc本体 + 辞書データ（NAIST/ICOT）** の著作権・ライセンス文を必ず同梱。
3. 採用バージョンの `mozc/LICENSE` および `src/data/**` の各ライセンス表記を再確認（郵便番号データ等の追加データを使う場合は個別確認：**未確認**）。

## まとめ
- **すべて商用OK**（BSD-3 / Apache-2.0 / SIL OFL / 各辞書の寛容ライセンス）。
- コピーレフト（GPL/AGPL/LGPL）で配布物を縛るものは**APKに含まれない**（JDKのGPLv2はビルドツールのみ）。
- 唯一の実務義務は「**ライセンス表示の同梱**」。ソース公開義務は発生しない。
