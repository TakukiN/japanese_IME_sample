# Android 日本語IME 要件定義（ドラフト v0.1）

作成日: 2026-07-15

## 1. 参考プロジェクト（GitHub / OSS）

| プロジェクト | 内容 | URL | 備考 |
|---|---|---|---|
| google/mozc | Google日本語入力のOSS版。かな漢字変換エンジン本体。Android版ビルドあり | https://github.com/google/mozc | ライセンス BSD-3。変換エンジンとして最有力の再利用候補 |
| AOSP LatinIME | Androidの標準英語キーボード実装。`InputMethodService`の実装リファレンス | https://android.googlesource.com/platform/packages/inputmethods/LatinIME/ | UI/フレームワーク層の教科書 |
| SoftKeyboard sample | Android公式の最小IMEサンプル | https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method | 骨格を掴む用 |
| OpenWnn / nicoWnnG | 旧・日本語IME OSS。フリック入力の参考 | 各フォーク要確認（未確認） | メンテ停止気味。設計参考のみ |
| libanthy / Anthy | かな漢字変換エンジン（Linux系） | https://github.com/fujiwarat/anthy （未確認: フォーク状況） | Mozcより軽量だが精度は劣る |

> 変換エンジンは自作せず **Mozc を組み込む** のが現実的。UI(キーボード)を自作し、変換はエンジンに委譲する構成を推奨。

## 2. アーキテクチャ（推奨構成）

```
[Keyboard UI (自作)] --かな入力--> [変換エンジン: Mozc] --候補--> [候補ビューUI]
        |                                                          |
   InputMethodService (Androidフレームワーク) <---- 確定文字列 ----+
```

- `InputMethodService` を継承し IME 本体を実装
- 入力方式: フリック / トグル(ケータイ打ち) / QWERTYローマ字 を切替
- 変換エンジンは JNI 経由で Mozc、または純Kotlin実装（辞書自作）

## 3. 機能要件

### 3.1 入力方式
- [ ] フリック入力（五十音、濁点/半濁点/小文字）
- [ ] トグル入力（あ→い→う…）
- [ ] QWERTYローマ字入力
- [ ] 英数字・記号・絵文字キーボード
- [ ] 入力方式のワンタッチ切替

### 3.2 変換
- [ ] かな漢字変換（連文節）
- [ ] 予測変換（前方一致・学習）
- [ ] 候補リスト表示・スクロール・選択
- [ ] 文節の伸縮・再変換
- [ ] 確定/取り消し（Backspace, undo）
- [ ] 学習機能（変換履歴の重み付け）
- [ ] ユーザー辞書登録

### 3.3 UI/UX
- [ ] キーリピート、ロングプレス（記号ポップアップ）
- [ ] カーソル移動・範囲選択
- [ ] ダーク/ライトテーマ
- [ ] キーボード高さ・サイズ調整
- [ ] 片手モード（任意）
- [ ] 触覚フィードバック / キー音

### 3.4 システム連携
- [ ] `EditorInfo` に応じた入力タイプ切替（メール/数字/パスワード等）
- [ ] クリップボード連携
- [ ] 音声入力連携（任意）
- [ ] マルチウィンドウ / 折りたたみ端末対応

## 4. 非機能要件
- 変換レイテンシ: 1候補生成 < 50ms 目標（未確認: 端末依存）
- 起動（キーボード表示）: < 200ms
- メモリ: 辞書ロード含め常駐を抑制
- オフライン完結（クラウド送信なし = プライバシー要件）
- 対応OS: Android 8.0 (API 26) 以上を想定（要決定）
- 対応言語: 日本語（＋英数）

## 5. プライバシー / セキュリティ
- 入力内容を外部送信しない（オンデバイス変換）
- パスワードフィールドで学習・ログを無効化
- 権限は最小限（インターネット権限は原則不要）

## 6. 技術スタック候補
- 言語: Kotlin
- UI: Android View / Jetpack Compose（IMEでのCompose採用は要検証）
- 変換: Mozc (C++/JNI) または 自作Trie辞書
- ビルド: Gradle / NDK（Mozc組込み時）

## 7. 確定事項（2026-07-15 ヒアリング）
- 変換エンジン: **Mozc 組込み**（JNI/NDK。BSD-3 ライセンス）
- 入力方式（優先）: **フリック入力** ＋ **QWERTYローマ字入力**（トグルは後回し）
- 最低対応OS: **Android 12 (API 31)**

## 8. 未決定事項（次回ヒアリング）
- Compose採用可否（IME UIでの採用は要検証、初期はView推奨）
- 商用配布（Play Store）予定の有無 → Mozc/辞書のライセンス整理が必要
- 辞書サイズ・容量方針（フル辞書 or 軽量版）
