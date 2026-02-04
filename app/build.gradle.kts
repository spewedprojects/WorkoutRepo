plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.gratus.workoutrepo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gratus.workoutrepo"
        minSdk = 24
        targetSdk = 36
        versionCode = 22
        versionName = "6.2.0" // major.minor.patch

        // Pass versionName to the app as a resource
        resValue(
            type = "string",
            name = "app_version",
            value = "v" + versionName!! + " (" + versionCode!! + ")"
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.gridlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(libs.gson)
}