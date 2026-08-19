plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.an.wrist"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.an.wrist"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "5.0"
        
        // Set the NextJS API base URL
        buildConfigField("String", "API_BASE_URL", "\"https://dhrististick.vercel.app/\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    buildFeatures {
        buildConfig = true
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildToolsVersion = "36.1.0"
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    
    // Retrofit for networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    
    // LiveData and ViewModel
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    
    // RecyclerView
    implementation(libs.recyclerview)
    
    // CardView
    implementation(libs.cardview)
    
    // MPAndroidChart for data visualization
    implementation(libs.mpandroidchart)
    
    // Swipe refresh layout
    implementation(libs.swiperefreshlayout)
    
    // Core Splash Screen
    implementation(libs.core.splashscreen)
    
    // OSMDroid for OpenStreetMap
    implementation(libs.osmdroid)
    implementation(libs.osmbonuspack)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}