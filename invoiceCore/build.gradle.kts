plugins {
    // Available through the root buildscript's Kotlin and AGP classpaths.
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        namespace = "com.poetscorner.invoiceqr.shared"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "InvoiceCore"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
        }
    }
}
