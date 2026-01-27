plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
}

repositories {
  mavenCentral()
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(11))
  }
}

dependencies {
  implementation(project(":lib"))
  implementation(project(":lib-jooq"))
  implementation(libs.coroutinesCore)
  implementation(libs.hikariCp)
  implementation(libs.mysql)
  implementation(libs.jooq)
  implementation(libs.jooqKotlin)
  implementation(libs.jacksonCore)
  implementation(libs.jacksonKotlin)
  implementation(libs.jacksonJsr310)

  testImplementation(libs.kotestProperty)
  testImplementation(libs.kotestAssertions)
  testImplementation(libs.kotestJunitRunnerJvm)
  testImplementation(libs.testcontainersCore)
  testImplementation(libs.testcontainersJunit)
  testImplementation(libs.testcontainersMysql)
}
