# Mozc組込み 実現性調査（2026-07-15）

## 結論
**実現可能。ただしビルドは WSL2(Ubuntu) 上で行う。** Windowsネイティブは非対応。

## 根拠
- Mozc公式のAndroidビルドは **macOS/Linuxのみサポート**（Windows非対応）
  出典: https://github.com/google/mozc/blob/master/docs/build_mozc_for_android.md
- この環境には **WSL2 Ubuntu が導入済み**（`wsl -l -v` で確認）→ Linuxビルド環境として利用可
- ビルドシステムは **Bazel(bazelisk)**。ビルドコマンド:
  `bazelisk build package --config oss_android --config release_build`
- 成果物は **`libmozc.so`（JNIネイティブライブラリ）**
- **重要**: MozcのAndroid APKクライアントは廃止済み。**変換エンジンのライブラリ(libmozc.so)のみ維持**
  → 我々の構成（UIは自作・変換はエンジンに委譲）と完全に一致

## この環境の状況
| ツール | 状態 |
|---|---|
| WSL2 Ubuntu | 導入済み（Stopped） |
| Bazel / bazelisk | Windows側にあり（WSL側は要確認） |
| NDK | r26.1 / r28.2（Mozcはr29を自動DL） |
| Python | Win側 3.11（Mozcは3.12+必須 → WSL側に3.12+要インストール） |
| git | あり |

## 必要な作業（見積り）
1. WSL Ubuntu を起動し、ビルド依存を導入（bazelisk, python3.12+, clang, git）
2. Mozcを clone → `build_tools/update_deps.py`（NDK r29等を取得）
3. `bazelisk build package --config oss_android --config release_build` で `libmozc.so` 生成
4. `.so` を `app/src/main/jniLibs/<ABI>/` に配置
5. Mozcの変換API（session/Command プロトコル）を叩く **JNIラッパ**を実装
6. `Converter` を実装する `MozcConverter` を作成し `SimpleDictionaryConverter` と差し替え

## リスク・未確認
- Mozcの変換呼び出しはprotobuf(Command)ベースで、JNI境界の設計に工数がかかる（未確認: 公開JNI APIの有無）
- ビルド時間・ディスク消費が大きい（Bazel）。初回は数十分規模の可能性
- WSL⇔Windowsのファイル連携（.so取り出し）は `\\wsl$` 経由で可能

## 次アクション候補
- M1: WSL上でMozcをcloneし依存導入まで進める（ビルド着手）
- M2: 先にJNIラッパ設計（Command protoの最小呼び出し）を机上で固める
