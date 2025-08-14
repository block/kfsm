plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish") version "0.33.0"
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
  implementation(libs.kotlinReflect)

  testImplementation(libs.kotestProperty)
  testImplementation(libs.kotestAssertions)
  testImplementation(libs.kotestJunitRunnerJvm)
  testImplementation(libs.mockk)
}
