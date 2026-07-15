package com.google.android.apps.inputmethod.libs.mozc.session

/**
 * Mozc ネイティブ(libmozc.so)への JNI 橋渡し。
 *
 * 【重要】このクラスの完全修飾名は
 *   com.google.android.apps.inputmethod.libs.mozc.session.MozcJNI
 * でなければならない。libmozc.so 内の JNIEXPORT シンボル
 *   Java_com_google_android_apps_inputmethod_libs_mozc_session_MozcJNI_initialize
 * がこの名前に固定でバインドされており、initialize() が残りの
 * ネイティブメソッド(evalCommand/onPostLoad/getDataVersion)を RegisterNatives で登録するため。
 *
 * 使用手順:
 *   1. System.loadLibrary("mozc")
 *   2. MozcJNI.initialize()        // 他ネイティブメソッドを登録
 *   3. MozcJNI.onPostLoad(dir,data) // エンジン＋辞書データを初期化
 *   4. MozcJNI.evalCommand(bytes)   // commands.Command(protobuf) を評価
 */
object MozcJNI {

    /** 残りのネイティブメソッドを登録する。loadLibrary 後に最初に呼ぶ。 */
    @JvmStatic external fun initialize(): Boolean

    /** ユーザープロファイルディレクトリと辞書データファイルを指定してエンジン初期化。 */
    @JvmStatic external fun onPostLoad(
        userProfileDirectoryPath: String,
        dataFilePath: String
    ): Boolean

    /** commands.Command をシリアライズした byte[] を渡し、結果の byte[] を得る。 */
    @JvmStatic external fun evalCommand(command: ByteArray): ByteArray

    /** ロード済み辞書データのバージョン文字列。スモークテスト用。 */
    @JvmStatic external fun getDataVersion(): String
}
