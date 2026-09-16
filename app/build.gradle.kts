plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.app.snapmind"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.app.snapmind"
        minSdk = 26
        targetSdk = 36
        // versionCode is never reused: Play rejects an upload that repeats one, even after
        // the build it belonged to was deleted. Increment on every upload.
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room schemas are committed so migrations can be written against them later.
    }
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    buildTypes {
        debug {
            // Hidden by default in every build variant (CLAUDE.md "Release status"). The
            // billing code stays wired -- only the Settings section stops rendering. A build
            // with it visible is `-Psnapmind.billingUi=true`.
            val billingUi = (project.findProperty("snapmind.billingUi") as? String)
                ?.toBooleanStrictOrNull() ?: false
            buildConfigField("boolean", "BILLING_UI_ENABLED", billingUi.toString())
        }
        release {
            isMinifyEnabled = false

            // Hidden for the first Play release (CLAUDE.md "Release status"). The billing code
            // stays wired -- only the Settings section stops rendering. A release build with it
            // visible is `-Psnapmind.billingUi=true`; re-enabling for real is this default.
            val billingUi = (project.findProperty("snapmind.billingUi") as? String)
                ?.toBooleanStrictOrNull() ?: false
            buildConfigField("boolean", "BILLING_UI_ENABLED", billingUi.toString())
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.coil.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime.ktx)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.datastore.preferences)
    implementation(libs.billing)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.work.testing)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.coroutines.test)

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
