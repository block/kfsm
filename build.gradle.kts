repositories {
  mavenCentral()
  google()
}

plugins {
  alias(libs.plugins.kotlinGradlePlugin) apply false
  alias(libs.plugins.dokka)
  alias(libs.plugins.versionsGradlePlugin)
  alias(libs.plugins.versionCatalogUpdateGradlePlugin)
  alias(libs.plugins.kotlinBinaryCompatibilityPlugin) apply false
}

subprojects {
  apply(plugin = "org.jetbrains.dokka")

  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "11"
    }
  }
  
  tasks.withType<Test> {
    useJUnitPlatform()
  }

  // Configure Dokka for each subproject
  tasks.withType<org.jetbrains.dokka.gradle.DokkaTask>().configureEach {
    dokkaSourceSets {
      named("main") {
        includes.from("module.md")
        moduleName.set(project.name)
        sourceLink {
          localDirectory.set(file("src/main/kotlin"))
          remoteUrl.set(uri("https://github.com/block/kfsm/tree/main/${project.name}/src/main/kotlin").toURL())
          remoteLineSuffix.set("#L")
        }
      }
    }
  }
}

// Configure Dokka multi-module task
tasks.dokkaHtmlMultiModule {
  outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
  includes.from("dokka-docs/module.md")
  moduleName.set("kfsm")
  moduleVersion.set(project.version.toString())
}

task("publishToMavenCentral") {
  group = "publishing"
  dependsOn(
    ":lib:publishToMavenCentral",
    ":lib-guice:publishToMavenCentral",
  )
}
