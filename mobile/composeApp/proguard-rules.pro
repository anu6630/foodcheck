# LifeX Food — release R8 rules

-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exception
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# Kotlinx Serialization (models + generated serializers)
-keepattributes RuntimeVisibleAnnotations
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.foodcheck.**$$serializer { *; }
-keepclassmembers class com.foodcheck.** {
    *** Companion;
}
-keep class com.foodcheck.** { *; }

# Ktor / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-keep class io.ktor.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Koin
-keep class org.koin.** { *; }
-keepclassmembers class * {
    @org.koin.core.annotation.* <methods>;
}

# Compose / AndroidX — default consumer rules usually enough; keep ViewModels
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# AdMob
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**
