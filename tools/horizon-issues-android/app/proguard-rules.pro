# kotlinx.serialization: keep the generated serializers of the GitHub shapes and the cached models.
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class dev.horizonlab.issues.**$$serializer { *; }
-keepclassmembers class dev.horizonlab.issues.** {
    *** Companion;
}
-keepclasseswithmembers class dev.horizonlab.issues.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp's optional TLS providers.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
