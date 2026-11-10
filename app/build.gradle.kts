plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "app.pulso"
    compileSdk = 34
    defaultConfig { applicationId = "app.pulso"; minSdk = 26; targetSdk = 34; versionCode = 8; versionName = "4.3" }
    signingConfigs { create("pulso") { storeFile = file("pulso.keystore"); storePassword = "pulso123"; keyAlias = "pulso"; keyPassword = "pulso123" } }
    buildTypes { getByName("debug") { signingConfig = signingConfigs.getByName("pulso") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
