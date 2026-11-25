import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) {
        versionPropsFile.inputStream().use { load(it) }
    }
}
val verMajor = versionProps.getProperty("VERSION_MAJOR")?.toIntOrNull() ?: 1
val verMinor = versionProps.getProperty("VERSION_MINOR")?.toIntOrNull() ?: 0
val verPatch = versionProps.getProperty("VERSION_PATCH")?.toIntOrNull() ?: 4
val defaultVerName = "$verMajor.$verMinor.$verPatch"
val defaultVerCode = versionProps.getProperty("VERSION_CODE")?.toIntOrNull()
    ?: (verMajor * 10000 + verMinor * 100 + verPatch)

val releaseVersionName = providers.gradleProperty("versionName").orNull ?: defaultVerName
val releaseVersionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: defaultVerCode

tasks.register("bumpVersion") {
    group = "release"
    description = "Tự động tăng VERSION_PATCH và VERSION_CODE cho mỗi bản xuất APK mới."
    val propsFile = versionPropsFile
    doLast {
        val p = Properties().apply {
            if (propsFile.exists()) propsFile.inputStream().use { load(it) }
        }
        val major = p.getProperty("VERSION_MAJOR")?.toIntOrNull() ?: 1
        val minor = p.getProperty("VERSION_MINOR")?.toIntOrNull() ?: 0
        val nextPatch = (p.getProperty("VERSION_PATCH")?.toIntOrNull() ?: 0) + 1
        val nextCode = (p.getProperty("VERSION_CODE")?.toIntOrNull() ?: (major * 10000 + minor * 100)) + 1
        propsFile.writeText(
            "VERSION_MAJOR=$major\n" +
                "VERSION_MINOR=$minor\n" +
                "VERSION_PATCH=$nextPatch\n" +
                "VERSION_CODE=$nextCode\n"
        )
        println("Bumped version to $major.$minor.$nextPatch (versionCode=$nextCode)")
    }
}

android {
    namespace = "com.example.cardreader"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.cardreader"
        minSdk = 23
        targetSdk = 35
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "DEFAULT_TARGET_URL", "\"http://diemdanhhs.com\"")
        buildConfigField("String", "DEFAULT_MDNS_HOSTNAME", "\"diemdanh.local\"")
        buildConfigField("String", "DEFAULT_GITHUB_REPO", "\"danhhoa20005/cardreader\"")
        buildConfigField("int", "CONTROL_SERVER_PORT", "8888")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.exists()) {
                val props = Properties().apply {
                    propsFile.inputStream().use { load(it) }
                }
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            } else {
                val envStoreFile = System.getenv("KEYSTORE_FILE")
                if (!envStoreFile.isNullOrBlank()) {
                    storeFile = file(envStoreFile)
                    storePassword = System.getenv("KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("KEY_ALIAS")
                    keyPassword = System.getenv("KEY_PASSWORD")
                }
            }
        }
    }

    buildTypes {
        release {
            val hasReleaseSigning = rootProject.file("keystore.properties").exists() ||
                !System.getenv("KEYSTORE_FILE").isNullOrBlank()
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.leanback)
    implementation(libs.glide)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
}
