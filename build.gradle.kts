plugins {
  kotlin("jvm") version "2.1.20"
  id("com.ncorti.ktfmt.gradle") version "0.22.0"
  id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0"
}

group = "io.github.mbalatsko.emailverifier"

version = "1.0-SNAPSHOT"

repositories { mavenCentral() }

dependencies {
  implementation("io.ktor:ktor-client-core:3.2.0")
  implementation("io.ktor:ktor-client-content-negotiation:3.2.0")
  implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.0")
  implementation("io.ktor:ktor-client-cio:3.2.0")

  testImplementation(kotlin("test"))
  testImplementation("io.ktor:ktor-client-mock:3.2.0")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")
}

tasks.test {
  useJUnitPlatform()
}
