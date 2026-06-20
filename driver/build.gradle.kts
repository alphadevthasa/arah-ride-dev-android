plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "id.arahride.driver"
    compileSdk = 35
    defaultConfig { applicationId = "id.arahride.driver"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1" }
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("String", "API_BASE_URL", "\"${providers.gradleProperty("API_BASE_URL").orElse("http://10.0.2.2:18080").get()}\"")
        buildConfigField("String", "MAP_STYLE_URL", "\"${providers.gradleProperty("MAP_STYLE_URL").orElse("https://tiles.openfreemap.org/styles/liberty").get()}\"")
    }
    buildTypes {
        getByName("debug") { manifestPlaceholders["usesCleartext"] = "true" }
        getByName("release") {
            isMinifyEnabled = false
            manifestPlaceholders["usesCleartext"] = "false"
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation(project(":core")); implementation("androidx.activity:activity:1.10.1") }
