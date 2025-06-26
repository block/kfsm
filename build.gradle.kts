plugins {
  alias(libs.plugins.kotlinGradlePlugin) apply false
  alias(libs.plugins.kotlinBinaryCompatibilityPlugin) apply false
  alias(libs.plugins.dokka)
  alias(libs.plugins.versionsGradlePlugin)
  alias(libs.plugins.versionCatalogUpdateGradlePlugin)
  `maven-publish`
  signing
}

version = providers.gradleProperty("VERSION_NAME").get()

allprojects {
  group = "app.cash.kfsm"
  
  repositories {
    mavenCentral()
  }
}

subprojects {
  apply(plugin = "org.jetbrains.kotlin.jvm")
  apply(plugin = "org.jetbrains.kotlinx.binary-compatibility-validator")
  apply(plugin = "org.jetbrains.dokka")
  apply(plugin = "maven-publish")
  apply(plugin = "signing")
  
  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "11"
    }
  }
  
  tasks.withType<Test> {
    useJUnitPlatform()
  }
  
  publishing {
    repositories {
      maven {
        name = "MavenCentral"
        url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
        credentials {
          username = providers.gradleProperty("mavenCentralUsername").orNull
          password = providers.gradleProperty("mavenCentralPassword").orNull
        }
      }
    }
  }
}

// Configure Dokka multi-module task
tasks.dokkaHtmlMultiModule {
  outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
}
