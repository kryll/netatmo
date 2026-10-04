-keep class com.arsys.netatmo.data.api.models.** { *; }
-keep class com.arsys.netatmo.domain.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# Strip all android.util.Log calls in release builds to prevent leaking
# APK URLs, file-system paths, and HTTP codes to logcat.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int e(...);
    public static int w(...);
    public static int i(...);
    public static int v(...);
    public static boolean isLoggable(...);
}
