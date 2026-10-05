# LiteRT-LM uses JNI callbacks into Kotlin classes.
-keep class com.google.ai.edge.litertlm.** { *; }

# kotlinx.serialization: keep generated serializers for the core's data classes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.tripwire.** { *** Companion; }
-keepclasseswithmembers class com.tripwire.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.tripwire.**$$serializer { *; }
# SQLCipher is called from native code.
-keep class net.zetetic.database.** { *; }
-dontwarn com.google.errorprone.annotations.**
