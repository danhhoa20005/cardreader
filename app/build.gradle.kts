import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val releaseVersionName = providers.gradleProperty("versionName").orNull ?: "1.0.0"
val releaseVersionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: 10000

android {
    namespace = "com.example.cardreader"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.cardreader"
        minSdk = 30
        targetSdk = 37
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        buildConfigField("String", "DEFAULT_TARGET_URL", "\"http://diemdanhhs.com\"")
        buildConfigField("String", "DEFAULT_MDNS_HOSTNAME", "\"diemdanh.local\"")
        buildConfigField("int", "CONTROL_SERVER_PORT", "8080")
        buildConfigField("String", "DEFAULT_GITHUB_REPO", "\"danhhoa20005/cardreader\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
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
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
