import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.googleService)
    alias(libs.plugins.firebaseCrashlyticsP)
}

android {
    namespace = "com.mjdevp.podevapp"
    compileSdk = 34

    buildFeatures{
        viewBinding = true
        mlModelBinding = true
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.mjdevp.podevapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { inputStream ->
                localProperties.load(inputStream)
            }
        }

        val apiKeyGmaps = localProperties.getProperty("API_KEYGMAPS")
        if (apiKeyGmaps != null) {
            buildConfigField("String", "API_KEY_GMAPS", "\"$apiKeyGmaps\"")
            resValue("string", "API_KEY_GOOGLE_MAPS", apiKeyGmaps)
        }
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.tensorflow.lite.support)
    implementation(libs.tensorflow.lite.metadata)
    implementation(libs.tensorflow.lite.gpu)
    implementation(libs.play.services.location)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // CameraX View class
    implementation(libs.cameraCore)
    implementation(libs.camera2)
    implementation(libs.cameraLifecycle)
    implementation(libs.cameraVideo)

    implementation(libs.cameraView)
    implementation(libs.cameraExtensions)
    implementation(libs.libraryCameraX)

    // Firebase
    implementation(libs.firebaseBom)
    implementation(libs.firebaseAnalytics)
    implementation(libs.firebaseAuth)
    implementation(libs.googleAuth)
    implementation(libs.googleAuthApiPhone)
    implementation(libs.googleIntegrity)
    implementation(libs.androidxBrowser)
    implementation(libs.firebaseCrashlytics)
    implementation(libs.firestore)
    implementation(libs.firebaseMessaging)

    // Google Maps
    implementation(libs.servicesMaps)
    implementation(libs.servicesPlaces)
    implementation(libs.servicesLocation)

    implementation(libs.coroutinesCore)
    implementation(libs.coroutinesAndroid)

    // OpenCv
    implementation("com.github.jose-jhr:openCvAndroidGameBall:1.0.2")

    // Tesseract
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.7.0")

    //implementation(platform("com.google.firebase:firebase-bom:33.2.0"))
    //implementation ("com.google.firebase:firebase-ml-vision:24.0.3")

    // ML Kit
    implementation ("com.google.mlkit:text-recognition:16.0.1")
}