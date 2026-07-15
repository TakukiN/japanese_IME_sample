# NihongoIME（日本語IME 開発版）

Android向け自作日本語IME。最小フリックキーボードから開始し、
段階的にMozc連携・かな漢字変換を追加していく。

## 現状（v0.2.0）
- `InputMethodService` ベースのIME骨組み
- フリック入力キーボード（かな11キー＋機能キー）
- 濁点・半濁点・小文字トグル（゛゜小）
- **未確定文字列（composing）管理** ＋ 候補ビュー
- **かな漢字変換**（`Converter` 経由・軽量辞書実装）→ 後でMozcに差し替え可
- 削除(⌫)・改行(↵)・全角スペース

## セットアップ
1. Android Studio で本フォルダを開く（Gradle同期でwrapper自動生成）
   - CLIで生成する場合: `gradle wrapper --gradle-version 8.9`
2. ビルド＆実機/エミュレータへインストール
3. 端末の「設定 > システム > 言語と入力 > 画面キーボード」で
   「日本語IME (開発版)」を有効化
4. テキスト欄でキーボード切替アイコンから本IMEを選択

## ロードマップ
1. ✅ 最小IME骨組み＋フリック入力
2. ✅ 未確定文字列＋候補ビュー＋かな漢字変換（軽量辞書）
3. ⬜ QWERTYローマ字入力モード
4. ⬜ Mozc組込み（NDK/JNI）で変換精度を本格化
5. ⬜ 連文節変換・学習・ユーザー辞書・仕上げ

詳細要件: [REQUIREMENTS.md](./REQUIREMENTS.md)

## 構成
```
app/src/main/
├── AndroidManifest.xml
├── java/jp/nihongo/ime/
│   ├── JapaneseImeService.kt   # IME本体
│   └── FlickKeyboardView.kt    # フリックキーボードUI
└── res/
    ├── values/strings.xml
    └── xml/method.xml          # IMEサブタイプ定義
```
