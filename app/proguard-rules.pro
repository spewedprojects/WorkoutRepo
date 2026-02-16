# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- STRAVA DATA MODELS ---
# Prevent R8 from renaming the fields in your data classes so Gson can find them
-keep class com.gratus.workoutrepo.data.** { *; }

# --- REPOSITORY CACHE ---
# Important! Your CacheData class is private inside an Object.
# We must explicitly keep it so the JSON file saving/loading works.
-keep class com.gratus.workoutrepo.repository.StravaRepository$CacheData { *; }

# --- GSON & RETROFIT GENERIC RULES (Just in case) ---
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**