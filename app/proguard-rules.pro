# SQLCipher supplies consumer rules for its JNI entry points.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keep class com.ai.assistance.operit.core.tools.javascript.QuickJsNativeBridge { *; }
-keep class com.ai.assistance.operit.ui.main.HomeGodotBridge { *; }
# Godot's native library resolves JVM methods by their original names. Removing them aborts engine initialization in Release.
-keep class org.godotengine.godot.** { *; }
