import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    android {
        namespace = "com.poetscorner.invoiceqr.shared"
        compileSdk = 37
        minSdk = 26
        androidResources.enable = true

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        withHostTest { }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    val bridge = layout.projectDirectory.dir("src/nativeInterop/cinterop")
    val nativeBuildScript = rootProject.file("tools/build_ios_zxing.sh")
    targets.withType<KotlinNativeTarget>().configureEach {
        val sdk = if (name == "iosArm64") "iphoneos" else "iphonesimulator"
        val capitalizedName = name.replaceFirstChar { it.uppercaseChar() }
        val output = layout.buildDirectory.dir("zxing/$name")
        val prepareZxing = tasks.register<Exec>("buildZxing$capitalizedName") {
            inputs.file(nativeBuildScript)
            inputs.dir(bridge)
            outputs.file(output.map { it.file("libInvoiceQrZxing.a") })
            commandLine("bash", nativeBuildScript, sdk, output.get().asFile, bridge.asFile)
        }
        compilations.getByName("main").cinterops.create("invoiceqrZxing") {
            definitionFile.set(bridge.file("invoiceqr_zxing.def"))
            includeDirs(bridge.asFile)
            extraOpts("-libraryPath", output.get().asFile.absolutePath)
        }
        tasks.matching { it.name == "cinteropInvoiceqrZxing$capitalizedName" }.configureEach {
            dependsOn(prepareZxing)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.multiplatform.runtime)
            api(libs.compose.multiplatform.foundation)
            api(libs.compose.multiplatform.material3)
            api(libs.compose.multiplatform.resources)
            implementation(libs.compose.multiplatform.lifecycle)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.io.core)
            implementation(libs.kotlinx.serialization.json)
            api(libs.androidx.room.runtime)
            api(libs.androidx.datastore.preferences.core)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.okio)
        }
        androidMain.dependencies {
            implementation(libs.androidx.camera.core)
            implementation(libs.zxing.android)
        }
        iosMain.dependencies {
            implementation(libs.compose.multiplatform.backhandler)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}

ksp {
    // The checked-in Android schemas are the compatibility baseline for every target.
    arg("room.schemaLocation", rootProject.file("app/schemas").absolutePath)
    arg("room.generateKotlin", "true")
}

compose.resources {
    packageOfResClass = "com.poetscorner.invoiceqr.resources"
    publicResClass = true
}
