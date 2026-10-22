import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.3.12"
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

abstract class PreparePrivacyAssetsTask : DefaultTask() {
    @get:InputFiles abstract val policies: ConfigurableFileCollection
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction fun prepare() {
        val destination = outputDirectory.get().asFile
        destination.mkdirs()
        policies.files.forEach { it.copyTo(destination.resolve(it.name), overwrite = true) }
    }
}

// The offline view uses the same canonical files as the public policy.
val preparePrivacyAssets = tasks.register<PreparePrivacyAssetsTask>("preparePrivacyAssets") {
    policies.from(rootProject.file("publishing/privacy-policy-en.md"), rootProject.file("publishing/privacy-policy-ar.md"))
    outputDirectory.set(layout.buildDirectory.dir("generated/privacyAssets"))
}

android {
    namespace = "com.poetscorner.invoiceqr"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.poetscorner.invoiceqr"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.0.2"
        val billingPublicKey = providers.gradleProperty("playBillingPublicKey").orElse("").get().trim()
        require(billingPublicKey.matches(Regex("[A-Za-z0-9+/=]*"))) { "playBillingPublicKey must be a Base64 public key" }
        buildConfigField("String", "PLAY_BILLING_PUBLIC_KEY", "\"$billingPublicKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Run device validation in a separate sandbox before updating the user's app.
            if (providers.gradleProperty("isolatedValidation").orNull == "true") {
                applicationIdSuffix = ".validation"
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Prebuilt dependencies may retain function symbols without DWARF line info.
            ndk.debugSymbolLevel = "SYMBOL_TABLE"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en", "ar")
    }
}

androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(preparePrivacyAssets, PreparePrivacyAssetsTask::outputDirectory)
}

dependencies {
    implementation("com.google.android.play:review:2.0.2")
    implementation("com.android.billingclient:billing:9.1.0")
    implementation("io.github.zxing-cpp:android:3.1.1")
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    implementation("com.google.android.gms:play-services-base:18.5.0")
    implementation("androidx.camera:camera-camera2:1.5.0")
    implementation("androidx.camera:camera-lifecycle:1.5.0")
    implementation("androidx.camera:camera-view:1.5.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    // Runtime dependencies already select Foundation 1.9.2; compile against the same ABI.
    implementation("androidx.compose.foundation:foundation:1.9.2")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
