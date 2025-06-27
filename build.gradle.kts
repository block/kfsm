plugins {
  alias(libs.plugins.kotlinGradlePlugin) apply false
  alias(libs.plugins.dokka)
  alias(libs.plugins.versionsGradlePlugin)
  alias(libs.plugins.versionCatalogUpdateGradlePlugin)
  alias(libs.plugins.kotlinBinaryCompatibilityPlugin) apply false
}

allprojects {
  group = providers.gradleProperty("GROUP").get()
  version = providers.gradleProperty("VERSION").get()
}

subprojects {
  // Temporarily commented out until plugin resolution is fixed
  // apply(plugin = "org.jetbrains.kotlinx.binary-compatibility-validator")
  apply(plugin = "org.jetbrains.dokka")
  
  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "11"
    }
  }
  
  tasks.withType<Test> {
    useJUnitPlatform()
  }
}

// Configure Dokka multi-module task
tasks.dokkaHtmlMultiModule {
  outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
}
