# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class me.shovon.sms2wallet.** {
    *** Companion;
}
-keepclasseswithmembers class me.shovon.sms2wallet.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Parsers are reflected over by name in the settings screen
-keep class me.shovon.bdparser.** { *; }
