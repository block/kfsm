plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm") // version "1.9.20"
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
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.20")
  implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.20")
  
  // Test dependencies
  testImplementation("io.kotest:kotest-assertions-core:5.8.0")
  testImplementation("io.kotest:kotest-runner-junit5-jvm:5.8.0")
  testImplementation("io.kotest:kotest-property:5.8.0")
  testImplementation("io.mockk:mockk:1.13.10")
}
