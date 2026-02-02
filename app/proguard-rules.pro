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

# Keep UI state classes for proper error messages
-keep class com.sappho.audiobooks.presentation.login.LoginUiState { *; }
-keep class com.sappho.audiobooks.presentation.login.LoginUiState$* { *; }

# Media3
-keep class androidx.media3.** { *; }
