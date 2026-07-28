# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep droidscan data classes (serializable)
-keep class com.droidscan.shared.core.** { *; }

# Keep Shizuku
-keep class moe.shizuku.api.** { *; }

# Keep all collectors
-keep class com.droidscan.telemetry.** { *; }
