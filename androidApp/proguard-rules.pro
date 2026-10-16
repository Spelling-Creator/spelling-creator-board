# Keep kotlinx.serialization generated serializers for the settings model.
-keepclassmembers class org.spellingcreator.board.settings.** {
    *** Companion;
}
-keepclasseswithmembers class org.spellingcreator.board.settings.** {
    kotlinx.serialization.KSerializer serializer(...);
}
