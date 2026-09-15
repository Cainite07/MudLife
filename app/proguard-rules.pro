# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ── Retrofit ──
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep class cn.mudlife.app.api.** { *; }
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ── Gson ──
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keepclassmembers class cn.mudlife.app.model.** { <fields>; }
-keep class cn.mudlife.app.model.** { *; }
-keep class cn.mudlife.app.api.** { *; }
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclasseswithmembers class * { @com.google.gson.annotations.SerializedName <fields>; }

# ── Keep entire app package (Gson generics break under R8) ──
-keep class cn.mudlife.app.** { *; }

# ── Kotlin Metadata (required for suspend function return types) ──
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# ── Force keep generic signatures for Gson type resolution ──
-keepattributes Signature, Exceptions, *Annotation*
-keep class cn.mudlife.app.model.BaseResponse { *; }
-keep class cn.mudlife.app.model.BaseResponse$* { *; }

# ── OkHttp / Okio ──
-dontwarn okhttp3.**
-dontwarn okio.**

# ── MQTT Paho ──
-keep class org.eclipse.paho.** { *; }

# ── Compose ──
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# ── CameraX ──
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ── ML Kit ──
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ── Jetpack Security (EncryptedSharedPreferences) ──
-keep class androidx.security.** { *; }
-dontwarn androidx.security.**
