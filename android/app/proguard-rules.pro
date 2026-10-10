# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.assetmgmt.laptops.**$$serializer { *; }
-keepclassmembers class com.assetmgmt.laptops.** { *** Companion; }
-keepclasseswithmembers class com.assetmgmt.laptops.** { kotlinx.serialization.KSerializer serializer(...); }
# Retrofit
-keepattributes Signature, Exceptions
-keep interface com.assetmgmt.laptops.data.remote.ApiService { *; }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
