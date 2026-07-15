# NihongoIME（日本語IME 開発版）

Android向け自作日本語IME。最小フリックキーボードから開始し、
段階的にMozc連携・かな漢字変換を追加していく。

## 現状（v0.3.0）
- `InputMethodService` ベースのIME骨組み
- フリック入力キーボード（5列×4行, 全モード整列）
- **3モード循環（あa1）: かな → 英字(ABC) → 数字(123)**
- フリック方向ガイド / 濁点・半濁点・小文字トグル（゛゜小）
- ↺ 文字循環トグル / ⌫ 長押しリピート削除 / カーソル移動 / IME切替
- **未確定文字列（composing）管理** ＋ 候補ビュー
- **Mozc かな漢字変換**（`libmozc.so` + `mozc.data`, JNI/protobuf, オフライン）
  - 未初期化時は軽量辞書へフォールバック
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
3. ✅ Mozc組込み（NDK/JNI）で変換精度を本格化
4. ✅ 3モード（かな/英字/数字）＋各種機能キー
5. ⬜ QWERTYローマ字入力モード
6. ⬜ 連文節変換（文節伸縮・部分確定）
7. ⬜ 学習の永続化・ユーザー辞書登録
8. ⬜ 仕上げ（設定画面・テーマ・Play Store配布準備）

詳細要件: [REQUIREMENTS.md](./REQUIREMENTS.md)

## 構成
```
app/src/main/
├── AndroidManifest.xml
├── assets/mozc.data                 # Mozc辞書データ
├── jniLibs/<abi>/libmozc.so         # Mozcネイティブ(全ABI)
├── java/jp/nihongo/ime/
│   ├── JapaneseImeService.kt        # IME本体
│   ├── FlickKeyboardView.kt         # 3モードフリックキーボード
│   ├── FlickGuideView.kt            # フリック方向ガイド
│   ├── CandidateView.kt             # 候補バー
│   ├── Converter.kt / SimpleDictionaryConverter.kt / MozcConverter.kt
│   ├── MozcEngine.kt                # JNIラッパ
│   └── Theme.kt                     # ダークテーマ
├── java/com/google/.../MozcJNI.kt   # native境界(固定FQCN)
├── java/org/mozc/.../protobuf/      # 生成protobufクラス
└── res/ (strings.xml, xml/method.xml)
```

技術詳細: [TECH_STACK.md](./TECH_STACK.md) / [MOZC_FEASIBILITY.md](./MOZC_FEASIBILITY.md)
