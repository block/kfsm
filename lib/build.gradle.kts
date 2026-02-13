plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish") version "0.33.0"
  alias(libs.plugins.kotlinBinaryCompatibilityPlugin)
}

repositories {
  mavenCentral()
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(11))
  }
  withSourcesJar()
}

mavenPublishing {
  configure(com.vanniktech.maven.publish.KotlinJvm())
}

dependencies {
  implementation(libs.arrowCore)
  implementation(libs.kotlinLogging)
  implementation(libs.kotlinReflect)
  implementation(libs.quiver)
  implementation(libs.slf4jApi)
  implementation(libs.slf4jNop)

  testImplementation(libs.kotestProperty)
  testImplementation(libs.kotestAssertions)
  testImplementation(libs.kotestJunitRunnerJvm)
  testImplementation(libs.mockk)
}
