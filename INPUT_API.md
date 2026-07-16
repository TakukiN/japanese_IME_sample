# テキスト入力 API 実装詳細（InputConnection）

IMEが対象アプリのテキスト欄へ文字を反映する仕組み。出典: Android 公式
`android.view.inputmethod.InputConnection` / `InputMethodService`。

## 1. 接続の取得とライフサイクル
- `InputMethodService.getCurrentInputConnection()` で対象欄への接続を得る。**null になり得る**ため常に `?.` で扱う。
- `onStartInput(EditorInfo, restarting)`: 入力欄にフォーカスが来た時。`EditorInfo` で欄の種類（`inputType` / `imeOptions`）を判定（本アプリはここでパスワード欄を検出）。
- `onFinishInput()`: フォーカスが外れた時。未確定のクリア等を行う。

## 2. 確定テキストと未確定テキストのモデル
IMEの入力は2層で成り立つ:
- **Composing（未確定）**: 変換前の下線付きテキスト。差し替え・削除が自由。
- **Committed（確定）**: 確定済みで通常の本文。

| メソッド | 役割 | 本アプリの使用箇所 |
|---|---|---|
| `setComposingText(text, newCursorPos)` | 未確定領域を text で置換・表示更新 | かな入力の逐次表示、候補プレビュー |
| `finishComposingText()` | 未確定をそのまま確定して領域を解除 | 改行・モード切替・新規入力開始時 |
| `commitText(text, newCursorPos)` | text を確定挿入（未確定があれば置換） | 候補確定・英数字直接入力・全角スペース |
| `deleteSurroundingText(before, after)` | カーソル前後の文字数を削除 | ⌫（未確定が空の時） |
| `deleteSurroundingTextInCodePoints(...)` | サロゲート/絵文字安全な削除 | （改善候補） |

`newCursorPos`: 1 = 挿入テキストの直後、0 = 直前。通常 1。

## 3. キーイベント送出
- `sendKeyEvent(KeyEvent)` / `InputMethodService.sendDownUpKeyEvents(keyCode)`:
  実際のキー押下をエミュレート。テキスト編集ではなく**カーソル移動・特殊キー**に使う。
  - 本アプリ: `KEYCODE_DPAD_LEFT/RIGHT` でカーソル移動（‹ ›）。
- `sendDefaultEditorAction(fromEnterKey)`:
  `imeOptions` に応じた既定アクション（検索/送信/次へ/改行）を発火。
  - 本アプリ: 改行キーで未確定が無い時に呼ぶ。

## 4. 周辺テキストの取得（文脈参照）
- `getTextBeforeCursor(n, flags)` / `getTextAfterCursor(n, flags)`:
  カーソル周辺 n 文字を取得。**IPC 経由で非同期的**なため null あり・重い。
  - 本アプリ: ↺（英字/数字）で直前確定文字を検証してから置換。
- `getSelectedText(flags)`: 選択中テキスト。
- `getSurroundingText(before, after, flags)`（API 31+）: 前後をまとめて取得（推奨）。

## 5. 選択・カーソル操作
- `setSelection(start, end)`: 選択範囲/カーソル位置を設定。
- `onUpdateSelection(...)`（IMS コールバック）: 外部要因でカーソルが動いた通知。
  未確定中にここでズレ検知→ composing をリセットする実装が堅牢。

## 6. バッチ編集（ちらつき防止）
複数の編集を1トランザクションにまとめ、再描画を1回に:
```kotlin
val ic = currentInputConnection ?: return
ic.beginBatchEdit()
try {
    ic.deleteSurroundingText(1, 0)
    ic.commitText(next, 1)
} finally {
    ic.endBatchEdit()
}
```
- 複数 API を連続実行する箇所（例: ↺ の削除+挿入）はバッチ化が望ましい（改善候補）。

## 7. その他
- `commitCompletion(CompletionInfo)`: オートコンプリート候補の確定。
- `commitCorrection(CorrectionInfo)`: 自動修正の通知。
- `performContextMenuAction(id)`: コピー/貼付/全選択等の実行。
- `requestCursorUpdates(...)`: カーソル位置のリアルタイム通知要求（フローティング候補窓等）。
- `commitContent(...)`: 画像/GIF 等リッチコンテンツの挿入。

## 8. 実装上の注意（本アプリの方針）
- 接続は毎回 `currentInputConnection` を取得し null 安全に扱う（保持しない）。
- パスワード欄（`isSecureField`）では変換・候補・エンジン送信を行わず `commitText` で直接確定。
- 破壊的操作（`deleteSurroundingText` 後の `commitText`）は、`getTextBeforeCursor` で想定値を検証してから行う（誤削除防止）。
- サロゲートペア（絵文字）を扱うなら `*InCodePoints` 系や `getSurroundingText` を使う。

## 9. 出力手段の設定と実装（本アプリ）
設定画面（`SettingsActivity`、候補バーのギア⚙から遷移）で **テキスト出力手段** を選択できる（`OutputMethod` / SharedPreferences 保存、`onStartInput` で反映）。確定テキストは `outputText()` が手段別にルーティングする。

| 手段 | 実装 | 日本語 | 検証状況 |
|---|---|---|---|
| commitText（既定） | `InputConnection.commitText` | ✅ | 動作 |
| KeyEvent | `KeyCharacterMap.getEvents` → `sendKeyEvent`、不可文字は commitText へフォールバック | ❌（英数字のみ） | 動作 |
| クリップボード | バッファに蓄積し**一括貼付**（`ClipData` + `performContextMenuAction(paste)`/`KEYCODE_PASTE`、元内容復元、`EXTRA_IS_SENSITIVE`付与） | ✅ | 動作 |
| AccessibilityService | `ImeAccessibilityService` が `ACTION_SET_TEXT` で編集欄へ設定（要ユーザー許可） | ✅ | **実機で確認済**（`setText result=true`） |

### 共通方針
- **機密欄（`isSecureField`）は手段に関わらず `commitText` 固定**（クリップボード/アクセシビリティ経由の漏洩防止）。
- **クリップボードのバッチ化**: 700ms 無操作 or 24文字 or 改行/離脱で1回だけ貼付し、貼付Toast（OS表示）の回数を削減。Back は未貼付バッファを1文字戻すのみ（貼付しない）。
- **AccessibilityService**: `findFocus(FOCUS_INPUT)` の編集ノードへ「現在値＋text」を `ACTION_SET_TEXT`。空欄は `isShowingHintText` でヒントを空扱いにし、プレースホルダ連結を防ぐ。未有効/失敗時は `commitText` フォールバック。

### 出力手段の選択指針
- 確実性・日本語・変換連携 → **commitText**（推奨）。
- commitText を無視するアプリの英数字入力 → KeyEvent。
- IC 非対応アプリへの強制入力 → クリップボード（最終手段）/ AccessibilityService（要権限）。
