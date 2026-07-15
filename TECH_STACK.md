# 技術スタック — 日本語IME (NihongoIME)

Android 向け自作日本語フリックIME。UIは独自実装、かな漢字変換に Google Mozc エンジンを組み込む。

## 全体アーキテクチャ
```
[FlickKeyboardView (自作UI)] --かな--> [MozcConverter] --evalCommand(protobuf)--> [libmozc.so]
        |                                     |                                        |
   InputMethodService <---- commitText ---- 候補選択                            mozc.data(辞書)
        |
   [CandidateView (候補バー)]
```
変換ロジックは `Converter` インターフェースで抽象化。Mozc未初期化時は軽量辞書 `SimpleDictionaryConverter` にフォールバック。

## アプリ（クライアント）
| 項目 | 内容 |
|---|---|
| 言語 | Kotlin |
| 最低API | Android 12 (API 31) / target API 34 |
| ビルド | Gradle 8.11.1 (Kotlin DSL) / AGP 8.5.2 / Kotlin 2.0.0 |
| JDK | 17 (Temurin) |
| UI | Android View (Compose未使用) — `InputMethodService` 継承 |
| 主要クラス | `JapaneseImeService`(IME本体) / `FlickKeyboardView`(3モードキーボード) / `FlickGuideView`(フリックガイド) / `CandidateView`(候補) / `MozcEngine`(JNIラッパ) / `MozcConverter`(変換) / `MozcJNI`(native境界) / `Theme`(ダークテーマ) |
| protobuf | protobuf-java 3.25.5（生成コードは事前生成し同梱） |

## 変換エンジン（Mozc）
| 項目 | 内容 |
|---|---|
| エンジン | google/mozc (BSD-3-Clause) |
| 成果物 | `libmozc.so`（arm64-v8a / armeabi-v7a / x86 / x86_64） |
| 辞書 | `mozc.data`（OSSデータセット, 約18MB, dataVersion 24.11.oss） |
| ビルド | Bazel(bazelisk) `bazelisk build package --config oss_android --config release_build` |
| 辞書ビルド | `//data_manager/oss:mozc_dataset_for_oss` |
| ビルド環境 | WSL2 Ubuntu 22.04（Windowsネイティブ非対応のため）/ NDK r29 / Python 3.12 |
| JNI境界 | `com.google.android.apps.inputmethod.libs.mozc.session.MozcJNI`（固定FQCN）: initialize / onPostLoad / evalCommand / getDataVersion |
| 変換プロトコル | `mozc.commands.Command` (protobuf)。CREATE_SESSION → SET_REQUEST(mixed_conversion, zero_query) → SEND_KEY(key_string=かな) → all_candidate_words |

## キーボード機能
- 入力方式: フリック入力（中央=あ段, 左=い段, 上=う段, 右=え段, 下=お段）
- 3モード循環（あa1キー）: かな → 英字(ABC) → 数字(123)
- フリック方向ガイドのポップアップ表示
- 濁点・半濁点・小文字トグル（゛゜小）
- ↺ 文字循環トグル（文字→番号→小文字）
- ⌫ 長押しリピート削除
- カーソル移動（‹ ›）/ IME切替（🌐）/ 全角スペース・変換・改行
- 業務用途向けダークテーマ（ティールアクセント, sans-serif-medium）

## 開発環境
| 項目 | 内容 |
|---|---|
| OS | Windows 11 + WSL2 Ubuntu 22.04 |
| ビルド/配布 | Gradle（Windows側）/ Mozcのみ WSL2 |
| 実機 | Android端末（adb経由でインストール・検証） |

## 既知の制約 / TODO
- プロジェクトパスが非ASCII（日本語）だと protoc がパスを開けない → 生成コードを事前生成し同梱で回避（`android.overridePathCheck=true`）
- 変換は毎キー全再変換（O(N)）。学習・文節分割・部分確定は未実装
- 英字/数字は直接確定（変換なし）
- 大容量バイナリ（.so/.data 計 約75MB）を同梱 → 将来 Git LFS 検討
