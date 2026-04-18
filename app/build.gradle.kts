plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    id("org.jetbrains.kotlin.kapt")
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.novelreader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.novelreader"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // Thêm dòng này để bỏ qua cảnh báo Experimental cho toàn bộ project
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"

    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // --- Core Android ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    // ... (giữ nguyên phần plugins và android ở trên)

    dependencies {
        // --- Core Android ---
        implementation(libs.androidx.core.ktx)
        implementation(libs.androidx.lifecycle.runtime.ktx)
        implementation(libs.androidx.activity.compose)

        // SỬA LỖI TẠI ĐÂY: Nếu 'libs.androidx.core.splashscreen' vẫn báo đỏ,
        // hãy dùng dòng comment phía dưới thay thế
        //implementation(libs.androidx.core.splashscreen)
         implementation("androidx.core:core-splashscreen:1.0.1")

        // --- Jetpack Compose BOM ---
        implementation(platform(libs.androidx.compose.bom))
        implementation(libs.androidx.ui)
        implementation(libs.androidx.ui.graphics)
        implementation(libs.androidx.ui.tooling.preview)
        implementation(libs.androidx.material3)
        implementation(libs.androidx.material.icons.extended)

        // --- Navigation Compose ---
        implementation(libs.androidx.navigation.compose)

        // --- Lifecycle ViewModel ---
        implementation(libs.androidx.lifecycle.viewmodel.compose)
        implementation(libs.androidx.lifecycle.runtime.compose)

        // --- Hilt (Dependency Injection) ---
        implementation(libs.hilt.android)
        ksp(libs.hilt.android.compiler)
        implementation(libs.androidx.hilt.navigation.compose)

        // --- Room (Local Database) ---
        implementation(libs.androidx.room.runtime)
        implementation(libs.androidx.room.ktx)
        ksp(libs.androidx.room.compiler)

        // --- Retrofit + OkHttp (Network) ---
        implementation(libs.retrofit)
        implementation(libs.retrofit.converter.gson)
        implementation(libs.okhttp)
        implementation(libs.okhttp.logging.interceptor)
        implementation(libs.gson)

        // --- Jsoup (Web Crawler) ---
        implementation(libs.jsoup)

        // --- Firebase ---
        implementation(platform(libs.firebase.bom))
        implementation("com.google.firebase:firebase-database-ktx")
        implementation("com.google.firebase:firebase-auth-ktx")
        implementation("com.google.firebase:firebase-firestore-ktx")
        implementation("com.google.firebase:firebase-messaging-ktx")
        // Thêm dòng này để sửa lỗi NonExistentClass cho Firebase Storage
        implementation("com.google.firebase:firebase-storage-ktx")



        // --- Coroutines ---
        implementation(libs.kotlinx.coroutines.android)
        implementation(libs.kotlinx.coroutines.play.services)

        // --- Coil (Image Loading) ---
        implementation(libs.coil.compose)

        // --- DataStore (Preferences) ---
        implementation(libs.androidx.datastore.preferences)

        // --- Paging 3 ---
        implementation(libs.androidx.paging.runtime)
        implementation(libs.androidx.paging.compose)

        // --- Testing ---
        testImplementation(libs.junit)
        androidTestImplementation(libs.androidx.junit)
        androidTestImplementation(libs.androidx.espresso.core)
        androidTestImplementation(platform(libs.androidx.compose.bom))
        androidTestImplementation(libs.androidx.ui.test.junit4)
        debugImplementation(libs.androidx.ui.tooling)
        debugImplementation(libs.androidx.ui.test.manifest)
    }
}