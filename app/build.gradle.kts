plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.ffmpeg_create_video"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.example.ffmpeg_create_video"
        minSdk = 24
        targetSdk = 33
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation ("com.github.abuanzeh:CustomConfirmDialog:1.0.2")

    implementation("com.arthenica:mobile-ffmpeg-full:4.4")
    implementation("org.florescu.android.rangeseekbar:rangeseekbar-library:0.3.0")

    implementation ("com.github.HeyAlex:BottomDrawer:v1.0.0")
    implementation ("com.github.certified84:CustomProgressIndicator:1.0")
    implementation("de.hdodenhof:circleimageview:3.1.0")
    implementation ("com.google.code.gson:gson:2.9.1")

}