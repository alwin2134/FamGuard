import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

fun getSecret(key: String, fallback: String = ""): String {
    return System.getenv(key)
        ?: localProperties.getProperty(key)
        ?: fallback
}

val supabaseUrl = getSecret("SUPABASE_URL", "")
val supabaseAnonKey = getSecret("SUPABASE_ANON_KEY", "")
val mapsApiKey = getSecret("MAPS_API_KEY", "AIzaSyFamGuardPlaceholderKeyForDevBuild")

android {
    namespace = "com.famguard.controller"
    compileSdk = 34

    val appVersionName = project.findProperty("versionName") as String? ?: "1.0.0"
    val appVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

    defaultConfig {
        applicationId = "com.famguard.controller"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "DEFAULT_SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "DEFAULT_SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("signing/famguard-release.jks")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = "famguard123"
                keyAlias = "famguard"
                keyPassword = "famguard123"
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":packages:api-contracts"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Maps
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)

    // Security & QR
    implementation(libs.androidx.security.crypto)
    implementation(libs.zxing.core)

    // Network & Coroutines
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
