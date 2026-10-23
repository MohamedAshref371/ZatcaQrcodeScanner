import com.android.build.api.artifact.MultipleArtifact
import com.android.build.api.artifact.SingleArtifact
import org.gradle.process.ExecOperations
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.3.12"
}

// Preserve real symbols from prebuilt libraries when the local NDK cannot extract them.
abstract class CollectNativeSymbolTables : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nativeLibraries: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val existingSymbols: ListProperty<Directory>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val collectorScript: RegularFileProperty

    @get:OutputDirectory
    abstract val symbolTables: DirectoryProperty

    @get:Input
    abstract val pythonExecutable: Property<String>

    @TaskAction
    fun collect() {
        val arguments = buildList {
            add(pythonExecutable.get())
            add(collectorScript.get().asFile.absolutePath)
            addAll(listOf("--merged-libs", nativeLibraries.get().asFile.absolutePath,
                "--output", symbolTables.get().asFile.absolutePath))
            existingSymbols.get().forEach {
                addAll(listOf("--existing-metadata", it.asFile.absolutePath))
            }
        }
        execOperations.exec { commandLine(arguments) }
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val collectSymbols = tasks.register<CollectNativeSymbolTables>(
            "collect${variant.name.replaceFirstChar { it.uppercaseChar() }}NativeSymbolTables"
        ) {
            nativeLibraries.set(variant.artifacts.get(SingleArtifact.MERGED_NATIVE_LIBS))
            collectorScript.set(rootProject.layout.projectDirectory.file("tools/collect_native_symbol_tables.py"))
            inputs.file(rootProject.file("tools/prepare_native_symbols.py"))
            pythonExecutable.set(providers.gradleProperty("nativeSymbolsPython").orElse("python"))
        }
        // Transform before bundle packaging/signing; preserve AGP-generated symbols when available.
        variant.artifacts.use(collectSymbols)
            .wiredWith(CollectNativeSymbolTables::existingSymbols, CollectNativeSymbolTables::symbolTables)
            .toTransform(MultipleArtifact.NATIVE_SYMBOL_TABLES)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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
        versionCode = 7
        versionName = "1.0.4"
        val billingPublicKey = providers.gradleProperty("playBillingPublicKey").orElse("").get().trim()
        require(billingPublicKey.matches(Regex("[A-Za-z0-9+/=]*"))) { "playBillingPublicKey must be a Base64 public key" }
        buildConfigField("String", "PLAY_BILLING_PUBLIC_KEY", "\"$billingPublicKey\"")

        val privacyPolicyUrl = rootProject.file("publishing/privacy-policy-url.txt").readText().trim()
        require(privacyPolicyUrl.matches(Regex("https://[A-Za-z0-9./_-]+"))) { "Invalid privacy policy URL" }
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"$privacyPolicyUrl\"")

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
    implementation("androidx.datastore:datastore-preferences:1.2.1")
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
