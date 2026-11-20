# Ktor / kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class dev.aprax.tmaii.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.aprax.tmaii.**$$serializer { *; }

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class org.slf4j.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
