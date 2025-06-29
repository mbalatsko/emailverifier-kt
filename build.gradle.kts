plugins {
    kotlin("jvm") version "2.1.20"
    id("com.diffplug.spotless") version "7.0.4"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0"

    id("org.jreleaser") version "1.18.0"
    signing
    `maven-publish`
    `java-library`
}

group = "io.github.mbalatsko"
version = "0.1"
description = "EmailVerifier is a composable, pluggable Kotlin library for validating email addresses beyond just their syntax."
val licenseName = "MIT"
val authorUsername = "mbalatsko"
val authorName = "Maksym Balatsko"

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

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name = rootProject.name
                description = project.description
                url = "https://github.com/$authorUsername/${rootProject.name}"
                licenses {
                    license {
                        name = licenseName
                        url = "https://opensource.org/licenses/$licenseName"
                    }
                }
                developers {
                    developer {
                        id = authorUsername
                        name = authorName
                    }
                }
                scm {
                    url = "https://github.com/$authorUsername/${rootProject.name}"
                }
            }
        }
    }

    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

jreleaser {
    project {
        name = rootProject.name
        version = rootProject.version.toString()
        description = rootProject.description
        authors = listOf(authorName)
        license = licenseName
    }

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
                    url = "https://maven.pkg.github.com/$authorUsername/${rootProject.name}"
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

tasks.test {
    useJUnitPlatform()
}
