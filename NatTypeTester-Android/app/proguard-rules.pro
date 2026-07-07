# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the SDK tools.

# Keep STUN message classes (used via reflection or serialization)
-keep class com.nattype.tester.stun.** { *; }
-keep class com.nattype.tester.nat.** { *; }

# Keep network related classes
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
