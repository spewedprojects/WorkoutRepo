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

# --- DATA MODELS & SERIALIZATION ---
-keep class com.gratus.workoutrepo.archive.model.** { *; }
-keep class com.gratus.workoutrepo.archive.data.** { *; }
-keep class com.gratus.workoutrepo.intervalsicu.data.** { *; }
-keep class com.gratus.workoutrepo.intervalsicu.repository.** { *; }
-keep class com.gratus.workoutrepo.strava.data.** { *; }
-keep class com.gratus.workoutrepo.strava.repository.** { *; }
-keep class com.gratus.workoutrepo.routine.model.** { *; }

# Keep inner data classes (e.g. CacheData) and Gson annotated fields
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- GSON & RETROFIT GENERIC RULES ---
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

-keepclassmembers enum * { *; }

-dontwarn okhttp3.**
-dontwarn retrofit2.**