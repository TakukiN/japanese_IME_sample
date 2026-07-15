# === Mozc JNI 境界 ===
# libmozc.so は RegisterNatives でクラス名/メソッド名に依存してバインドするため、
# 完全修飾クラス名・ネイティブメソッドを難読化・削除しない。
-keep class com.google.android.apps.inputmethod.libs.mozc.session.MozcJNI { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# === 生成 protobuf クラス（リフレクション利用） ===
-keep class org.mozc.android.inputmethod.japanese.protobuf.** { *; }
-keep class com.google.protobuf.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
-dontwarn com.google.protobuf.**

# === Android フレームワークから参照されるエントリポイント ===
-keep class jp.nihongo.ime.JapaneseImeService { *; }
-keep class jp.nihongo.ime.LicensesActivity { *; }

# ログはリリースビルドから除去（機密の間接漏洩を防ぐ多重防御）
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
