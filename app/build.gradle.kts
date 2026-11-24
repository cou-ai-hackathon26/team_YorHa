plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ABIs to build. Default: real arm64 phones only (fast builds). For the x86_64 emulator run
// ./gradlew ... -Pmedidiary.abis=arm64-v8a,x86_64   (or edit gradle.properties)
val medidiaryAbis = (findProperty("medidiary.abis") as String? ?: "arm64-v8a")
    .split(",").map { it.trim() }.filter { it.isNotEmpty() }

// Family sync (Task 1): Firebase keys come from app/google-services.json (git-ignored) or from the
// MEDIDIARY_FIREBASE_* environment variables (CI secrets). Without them the family build still
// compiles and runs; Settings just says sync isn't configured.
val firebaseConfig: Map<String, String> = run {
    val json = listOf("google-services.json", "src/family/google-services.json").map { file(it) }.firstOrNull { it.exists() }?.readText()
    fun fromJson(field: String) = json?.let { Regex("\"$field\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
    mapOf(
        "FIREBASE_API_KEY" to (System.getenv("MEDIDIARY_FIREBASE_API_KEY") ?: fromJson("current_key")),
        "FIREBASE_APP_ID" to (System.getenv("MEDIDIARY_FIREBASE_APP_ID") ?: fromJson("mobilesdk_app_id")),
        "FIREBASE_PROJECT_ID" to (System.getenv("MEDIDIARY_FIREBASE_PROJECT_ID") ?: fromJson("project_id")),
    ).mapValues { (_, v) -> v.orEmpty().filter { it.isLetterOrDigit() || it in "-_:." } }
}

android {
    namespace = "com.medidiary.app"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.medidiary.app"
        minSdk = 26
        targetSdk = 34
        // CI: every workflow run gets a higher versionCode, so a newer APK installs over an older one.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = System.getenv("MEDIDIARY_VERSION_NAME") ?: "1.0.0"

        ndk {
            // Only build for modern 64-bit ARM phones (fast builds)
            abiFilters += medidiaryAbis
        }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DCMAKE_BUILD_TYPE=Release"
                )
                cppFlags += "-O3 -fexceptions -frtti"
            }
        }
    }

    // Dev 1's original JNI smoke-test library (NativeBridge.testJni). The real inference
    // libraries live in the :whisper and :llama modules.
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Optional fixed release key (GitHub Actions secrets → env). Without it, release builds use the
    // machine's debug key — fine locally, but CI would produce a different key on every run.
    val keystorePath = System.getenv("MEDIDIARY_KEYSTORE_FILE")
    if (keystorePath != null && file(keystorePath).exists()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("MEDIDIARY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MEDIDIARY_KEY_ALIAS")
                keyPassword = System.getenv("MEDIDIARY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Side-loadable demo builds: the fixed release key if configured, else the debug key.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // offline: the original zero-network app (no INTERNET permission; CI enforces it).
    // family:  same app + opt-in, end-to-end-encrypted family sync of verified entries (Task 1).
    flavorDimensions += "distribution"
    productFlavors {
        create("offline") {
            dimension = "distribution"
            isDefault = true
        }
        create("family") {
            dimension = "distribution"
            applicationIdSuffix = ".family"
            versionNameSuffix = "-family"
            firebaseConfig.forEach { (k, v) -> buildConfigField("String", k, "\"$v\"") }
        }
    }

    androidResources {
        // Never let aapt compress large binary assets (if a team member bundles a model for a demo).
        noCompress += listOf("gguf", "bin")
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        jniLibs { useLegacyPackaging = false }
    }
}

dependencies {
    implementation(project(":whisper"))
    implementation(project(":llama"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)

    "familyImplementation"(platform(libs.firebase.bom))
    "familyImplementation"(libs.firebase.firestore)
    "familyImplementation"(libs.firebase.auth)
    "familyImplementation"(libs.kotlinx.coroutines.play.services)
    "familyImplementation"(libs.zxing.core)
    "familyImplementation"(libs.zxing.android.embedded)
    "familyImplementation"(libs.androidx.fragment.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
