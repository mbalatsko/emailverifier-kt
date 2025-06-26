plugins {
    kotlin("jvm") version "2.1.20"
    id("com.diffplug.spotless") version "7.0.4"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0"
}

group = "io.github.mbalatsko.emailverifier"

version = "1.0-SNAPSHOT"

repositories { mavenCentral() }

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
    implementation("io.ktor:ktor-client-content-negotiation:3.2.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.0")
    implementation("io.ktor:ktor-client-cio:3.2.0")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-mock:3.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "emailverifier-kt"
            pom {
                name = project.name
                description =
                    "EmailVerifier is a composable, pluggable Kotlin library for validating email addresses beyond just their syntax. It's built with a clear focus: help developers reliably assess whether a given email is real, meaningful, and worth accepting."
                url = "https://mbalatsko.github.io/emailverifier-kt/"
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/licenses/MIT"
                        distribution = "repo"
                    }
                }
                developers {
                    developer {
                        id = "mbalatsko"
                        name = "Maksym Balatsko"
                        email = "mbalatsko@gmail.com"
                    }
                }
                scm {
                    connection = "scm:git:https://github.com/mbalatsko/emailverifier-kt.git"
                    developerConnection = "scm:git:ssh://github.com/mbalatsko/emailverifier-kt.git"
                    url = "https://github.com/mbalatsko/emailverifier-kt"
                }
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
