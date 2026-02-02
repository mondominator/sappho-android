# Add project specific ProGuard rules here.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Gson
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keep class com.sappho.audiobooks.data.remote.dto.** { *; }
-keep class com.sappho.audiobooks.domain.model.** { *; }
-keep class com.sappho.audiobooks.data.remote.*Response { *; }
-keep class com.sappho.audiobooks.data.remote.*Request { *; }

# Keep all presentation layer classes (ViewModels, UI states, etc.)
-keep class com.sappho.audiobooks.presentation.** { *; }

# Keep Kotlin sealed classes and data classes
-keepclassmembers class * extends kotlin.Sealed {
    <fields>;
    <methods>;
}

# Keep exception classes for proper error message handling
-keep class java.net.** { *; }
-keep class javax.net.ssl.** { *; }
-keep class java.io.IOException { *; }

# Keep OkHttp/Retrofit error handling
-keep class okhttp3.** { *; }
-keep class retrofit2.** { *; }
-keepclassmembers class retrofit2.Response { *; }

# Keep Kotlin metadata for reflection
-keepattributes RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }

# Media3
-keep class androidx.media3.** { *; }
