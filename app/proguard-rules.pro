# Add project specific ProGuard rules here.
-keepattributes Signature
-keepattributes *Annotation*

# Kotlin Serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.chen.schedule.**$$serializer { *; }
-keepclassmembers class com.chen.schedule.** {
    *** Companion;
}
-keepclasseswithmembers class com.chen.schedule.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Jsoup
-keep class org.jsoup.** { *; }

# JSpecify 注解(jsoup 可选依赖,仅缺失类警告)
-dontwarn org.jspecify.**
