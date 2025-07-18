plugins {
    kotlin("jvm") version "2.1.20"
    id("com.diffplug.spotless") version "7.0.4"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0"

    id("org.jreleaser") version "1.18.0"
    signing
    id("org.kordamp.gradle.java-project") version "0.54.0"
    id("org.jetbrains.dokka") version "2.0.0"
}

group = "io.github.mbalatsko"
version = "0.4"
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

dependencies {
    implementation("io.ktor:ktor-client-core:3.2.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.0")
    implementation("io.ktor:ktor-client-cio:3.2.0")
    implementation("com.squareup.okio:okio:3.13.0")
    implementation("org.slf4j:slf4j-api:2.0.13")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-mock:3.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

config {
    info {
        name = rootProject.name
        description = rootProject.description
        inceptionYear = "2025"
        vendor = "Maksym Balatsko"

        links {
            website = "https://github.com/mbalatsko/${rootProject.name}"
            issueTracker = "https://github.com/mbalatsko/${rootProject.name}/issues"
            scm = "https://github.com/mbalatsko/${rootProject.name}.git"
        }

        scm {
            url = "https://github.com/mbalatsko/${rootProject.name}"
            connection = "scm:git:https://github.com/mbalatsko/${rootProject.name}.git"
            developerConnection = "scm:git:git@github.com:mbalatsko/${rootProject.name}.git"
        }

        people {
            person {
                id = "mbalatsko"
                name = "Maksym Balatsko"
                roles.addAll(arrayOf("developer", "author"))
            }
        }
    }

    licensing {
        licenses {
            license {
                id = "MIT"
                excludes = setOf("**/*")
            }
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

jreleaser {

    deploy {
        maven {
            mavenCentral {
                create("sonatype") {
                    setActive("ALWAYS")
                    url = "https://central.sonatype.com/api/v1/publisher"
                    stagingRepository("build/staging-deploy")
                }
            }
        }
        maven {
            github {
                create("app") {
                    setActive("ALWAYS")
                    applyMavenCentralRules = true
                    stagingRepository("build/staging-deploy")
                }
            }
        }
    }

    signing {
        setActive("ALWAYS")
        armored = true
    }

    checksum {
        individual = true
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
}
