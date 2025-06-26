dependencies {
  api(project(":lib"))
  
  implementation(libs.guice)
  implementation(libs.reflections)
  implementation(libs.kotlinLoggingJvm)
  implementation(libs.slf4jSimple)
  
  testImplementation(libs.bundles.kotest)
  testImplementation(libs.mockk)
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(11))
  }
}
