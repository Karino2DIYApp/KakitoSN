plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.karino2.kakito"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.karino2.kakito"
        minSdk = 26
        targetSdk = 37
        versionCode = 6
        versionName = "6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        jniLibs {
            pickFirsts += "**/libc++_shared.so"
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.onyxsdk.pen)
    implementation(libs.onyxsdk.device)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}