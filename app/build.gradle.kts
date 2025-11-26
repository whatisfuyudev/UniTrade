plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.kapt")                   // kapt untuk annotation processors (Hilt, Glide)
    id("dagger.hilt.android.plugin")    // Hilt plugin
    id("com.google.gms.google-services")
}

android {
    namespace = "com.unitrade.unitrade"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.unitrade.unitrade"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // ADD THIS BLOCK
    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Lifecycle / ViewModel / LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.1")

    // Navigation component
    implementation("androidx.navigation:navigation-fragment-ktx:2.6.0")
    implementation("androidx.navigation:navigation-ui-ktx:2.6.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.57.2")
    kapt("com.google.dagger:hilt-compiler:2.57.2")

    // Glide (image loading)
    implementation("com.github.bumptech.glide:glide:4.15.1")
    kapt("com.github.bumptech.glide:compiler:4.15.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")
    // helpers to use Tasks.await in coroutines for Firebase
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Firebase (use BOM already included)
    implementation(platform("com.google.firebase:firebase-bom:34.5.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")


    // Image / UI helpers
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // (if you prefer Coil instead of Glide, swap here)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // --------------------------
// NEW DEPENDENCIES (tambahkan ke dependencies block)
// --------------------------

// NEW: Realtime Database (non-KTX) - direkomendasikan bila ingin fitur presence / onDisconnect
    implementation("com.google.firebase:firebase-database") // NEW

// NEW: OkHttp - untuk upload multipart langsung ke Cloudinary (atau HTTP request lain)
    implementation("com.squareup.okhttp3:okhttp:4.11.0") // NEW

// NEW: Gson - parsing response Cloudinary / JSON ringan
    implementation("com.google.code.gson:gson:2.10.1") // NEW

// NEW: Hilt navigation helper - mempermudah injection dalam fragment yang dipakai nav component
    implementation("androidx.hilt:hilt-navigation-fragment:1.0.0") // NEW

// OPTIONAL - NEW: Retrofit + Gson converter (jika ingin wrapper HTTP yang terstruktur)
    implementation("com.squareup.retrofit2:retrofit:2.9.0") // NEW (opsional)
    implementation("com.squareup.retrofit2:converter-gson:2.9.0") // NEW (opsional)

    // PHOTO VIEW untuk pinch-to-zoom
    implementation("com.github.chrisbanes:PhotoView:2.3.0")

    implementation("de.hdodenhof:circleimageview:3.1.0")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:<latest-version>")

    // Google AI (Gemini) for chatbot
    implementation("com.google.ai.client.generativeai:generativeai:0.1.2")
}
