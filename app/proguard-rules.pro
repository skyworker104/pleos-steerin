# Keep Pleos Vehicle SDK public API and AIDL bindings intact.
-keep class ai.pleos.playground.** { *; }
-dontwarn android.car.**
-keep class ai.pleos.playground.tts.** { *; }
-keep class ai.pleos.playground.caas.** { *; }
