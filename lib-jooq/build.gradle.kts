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
  api(project(":lib"))
  api(libs.jooq)
  implementation(libs.jooqKotlin)
  implementation(libs.jacksonCore)
  implementation(libs.jacksonKotlin)
  implementation(libs.jacksonJsr310)

  testImplementation(libs.kotestAssertions)
  testImplementation(libs.kotestJunitRunnerJvm)
  testImplementation(libs.kotestProperty)
  testImplementation(libs.mockk)
  testImplementation(libs.testcontainersCore)
  testImplementation(libs.testcontainersJunit)
  testImplementation(libs.testcontainersMysql)
  testImplementation(libs.mysql)
  testImplementation(libs.hikariCp)
}

tasks.withType<Test> {
  useJUnitPlatform()
}
