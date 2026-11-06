# Доменные модели сериализуются kotlinx.serialization — имена полей не должны меняться.
-keepclassmembers class com.turbofetch.domain.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.turbofetch.domain.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
