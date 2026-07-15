# セキュリティレビュー報告書 — NihongoIME

基準: OWASP Mobile Top 10 (2024) / OWASP MASVS
対象: 自作日本語IME（Kotlin, InputMethodService, Mozc組込み）
判定レベル: **MASVS L1（一般アプリ）+ R（リバース耐性の一部）**

## エグゼクティブサマリ
本アプリは **完全オンデバイス** で動作し、入力内容を外部送信しない。IME特有の最大リスクである
「キーロギング／機密入力の漏洩」に対し、パスワード欄検出・エンジン非送信・バックアップ禁止で対策済み。
本レビューで検出した項目はすべて是正実装を完了した。

## コンプライアンス状況
| レベル | 状況 | 備考 |
|---|---|---|
| L1（基本） | ✅ 準拠 | ストレージ/通信/プラットフォーム対策済み |
| L2（高保護） | 対象外 | 金融・医療用途ではないため未適用 |
| R（リバース耐性） | ◯ 一部準拠 | release で R8 難読化・縮小・ログ除去を有効化 |

## MASVS チェックリスト（主要項目）
| カテゴリ | 項目 | 判定 | 対応 |
|---|---|---|---|
| MASVS-STORAGE | 機密データを不要に保存しない | ✅ | 打鍵をログ・永続化しない |
| MASVS-STORAGE | バックアップからの漏洩防止 | ✅ | `allowBackup=false` + `dataExtractionRules` で全除外 |
| MASVS-STORAGE | パスワード欄で学習・提案を無効化 | ✅ | `isSecureField()` で変換/候補/エンジン送信を停止し直接確定 |
| MASVS-NETWORK | 平文通信の禁止 | ✅ | `usesCleartextTraffic=false`。そもそも通信なし |
| MASVS-NETWORK | 不要な権限を持たない | ✅ | **INTERNET 権限を宣言しない**（外部送信不可能） |
| MASVS-PLATFORM | コンポーネントの適切な公開範囲 | ✅ | IMEサービスは `BIND_INPUT_METHOD` で保護。公開Activityは非機密のライセンス表示のみ |
| MASVS-PLATFORM | 機密入力の非個人化学習フラグ尊重 | ✅ | `IME_FLAG_NO_PERSONALIZED_LEARNING` を検出し機密欄扱い |
| MASVS-CODE | デバッグ無効（release） | ✅ | `debuggable` を明示設定せず（release=false） |
| MASVS-CODE | 入力の直接確定でインジェクション面を持たない | ✅ | WebView/JS/SQL 等の危険面なし |
| MASVS-RESILIENCE | 難読化・不要コード削除 | ◯ | release で R8 有効。JNI/protobuf は keep ルールで保護 |
| MASVS-RESILIENCE | ログの本番除去 | ✅ | `-assumenosideeffects` で Log.v/d/i を除去 |

## OWASP Mobile Top 10 (2024) 対応
| ID | リスク | 該当 | 状況 |
|---|---|---|---|
| M1 不適切な資格情報利用 | 認証情報なし | ― | 資格情報を扱わない |
| M2 サプライチェーン | OSS依存 | 低 | Mozc/protobuf/AndroidX（寛容ライセンス・著名OSS） |
| M4 不十分な入出力検証 | 変換入力 | 低 | ネイティブ境界は protobuf でシリアライズ、危険面なし |
| M5 安全でない通信 | 通信なし | ― | INTERNET権限なし・完全オフライン |
| M6 不適切な認可 | ― | ― | 認可対象なし |
| M8 セキュリティ設定ミス | Manifest | ✅ | backup/cleartext/権限を是正済み |
| M9 安全でないデータ保存 | 学習/辞書 | ✅ | 打鍵非保存・機密欄で学習停止・backup禁止 |

## 実施した是正（本レビュー）
1. パスワード/数値パスワード/可視パスワード/Web パスワード欄、および
   `IME_FLAG_NO_PERSONALIZED_LEARNING` を検出（`JapaneseImeService.isSecureField`）。
   機密欄では **変換・候補提示・Mozc へのキー送信を行わず、打鍵を直接確定**。
2. `AndroidManifest`: `allowBackup=false` / `usesCleartextTraffic=false` /
   `dataExtractionRules`（cloud-backup・device-transfer を全除外）。
3. `build.gradle`(release): `isMinifyEnabled=true` / `isShrinkResources=true`。
   `proguard-rules.pro` で MozcJNI・native・protobuf を keep、Log.v/d/i を除去。
4. 打鍵内容をログ出力しないことを確認（初期化ログはバージョン文字列のみ）。

## 残リスク / 推奨（今後）
- **未確認**: mozc.data 由来辞書に含まれる文字列は Mozc OSS 公開データ（個人情報ではない）。
- L2 相当が必要になった場合: ルート検知・StrongBox 鍵・改ざん検知の追加を検討。
- 依存脆弱性は `dependency-check` 等で定期監査を推奨。
- 学習機能を実装する際は、機密欄除外に加え保存データの暗号化（EncryptedFile 等）を要検討。

> 本報告は静的レビューに基づく。APK/動的解析・第三者ペネトレーションは別途推奨。
