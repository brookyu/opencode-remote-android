# Retrofit
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ai.opencode.remote.**$$serializer { *; }
-keepclassmembers class ai.opencode.remote.** {
    *** Companion;
}
-keepclasseswithmembers class ai.opencode.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Moshi (if used)
-keep class com.squareup.moshi.** { *; }
