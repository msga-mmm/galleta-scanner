plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.application")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
    }
    jvm("desktop")

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
        )
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.material3)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(compose.ui)
                implementation(compose.preview)
                implementation("androidx.activity:activity-compose:1.12.2")
                implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "app.galletascanner.dev"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.galletascanner.dev"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

ktlint {
    android.set(true)
    outputToConsole.set(true)
    ignoreFailures.set(false)
    filter {
        exclude("**/build/**")
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(false)
    }
}

tasks.named("check") {
    dependsOn("ktlintCheck", "detekt", "lint")
}

compose.desktop {
    application {
        mainClass = "app.galletascanner.dev.MainKt"
    }
}
