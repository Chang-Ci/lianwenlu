# Add project specific ProGuard rules here.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keepclassmembers class com.lianwenqi.app.** { *; }
-keep,includedescriptorclasses class com.lianwenqi.app.**$$serializer { *; }