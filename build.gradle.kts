plugins {
    kotlin("multiplatform") version "2.1.20"
    `maven-publish`
    id("com.diffplug.spotless") version "7.0.4"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0"

    signing
    id("org.jetbrains.dokka") version "2.0.0"
}

group = "io.github.mbalatsko"
version = "0.4-SNAPSHOT"
description =
    """
    EmailVerifier is a composable, pluggable Kotlin library for validating email addresses beyond just their syntax.
    It's built with a clear focus: help developers reliably assess whether a given email is real, meaningful, and worth accepting.
    Features: 
    - syntax validation
    - registrability check
    - mx record lookup
    - disposable email detection
    - gravatar existence check
    - free email provider detection
    """.trimIndent()

repositories {
    gradlePluginPortal()
    mavenCentral()
}

spotless {
    kotlin {
        ktlint("1.6.0")
        trimTrailingWhitespace()
        endWithNewline()
        target("**/*.kt")
    }
    kotlinGradle {
        ktlint()
    }
}

kotlin {
    jvm()
    js(IR) {
        browser()
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-core:3.2.0")
                implementation("io.ktor:ktor-client-content-negotiation:3.2.0")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.0")
                implementation("com.squareup.okio:okio:3.13.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.ktor:ktor-client-mock:3.2.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-cio:3.2.0")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jsMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-js:3.2.0")
                implementation(npm("punycode", "2.3.1"))
            }
        }
        val jsTest by getting
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
            dependencies {
                implementation("io.ktor:ktor-client-darwin:3.2.0")
            }
        }
        val iosX64Test by getting
        val iosArm64Test by getting
        val iosSimulatorArm64Test by getting
        val iosTest by creating {
            dependsOn(commonTest)
            iosX64Test.dependsOn(this)
            iosArm64Test.dependsOn(this)
            iosSimulatorArm64Test.dependsOn(this)
        }
    }
}

publishing {
    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

tasks.named("allTests") {
    enabled = false
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}
