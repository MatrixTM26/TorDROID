# TorDROID ProGuard Rules
-keep class com.tordroid.** { *; }
-keep class com.tordroid.service.** { *; }
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Netcipher
-keep class info.guardianproject.** { *; }
