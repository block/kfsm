dependencies {
  implementation(libs.kotlinReflect)
  testImplementation(libs.bundles.kotest)
  testImplementation(libs.mockk)
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(11))
  }
}
