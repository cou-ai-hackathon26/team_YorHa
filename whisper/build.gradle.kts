// whisper.cpp JNI wrapper. Builds its own static copy of ggml so it never clashes with :llama.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// ABIs to build. Default: real arm64 phones only (fast builds). For the x86_64 emulator run
// ./gradlew ... -Pmedidiary.abis=arm64-v8a,x86_64   (or edit gradle.properties)
val medidiaryAbis = (findProperty("medidiary.abis") as String? ?: "arm64-v8a")
    .split(",").map { it.trim() }.filter { it.isNotEmpty() }

android {
    namespace = "com.medidiary.whisper"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        ndk { abiFilters += medidiaryAbis }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DANDROID_STL=c++_shared",
                    "-DBUILD_SHARED_LIBS=OFF",
                    "-DGGML_NATIVE=OFF",
                    "-DGGML_OPENMP=OFF",
                    "-DWHISPER_BUILD_EXAMPLES=OFF",
                    "-DWHISPER_BUILD_TESTS=OFF",
                    "-DWHISPER_BUILD_SERVER=OFF",
                )
            }
        }
    }
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
