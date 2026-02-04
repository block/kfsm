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

  dokka {
    moduleName.set(project.name)
    dokkaPublications.html {
      suppressInheritedMembers.set(true)
      failOnWarning.set(true)
    }
    dokkaSourceSets.configureEach {
      includes.from("module.md")
      sourceLink {
        localDirectory.set(file("src/main/kotlin"))
        remoteUrl.set(uri("https://github.com/block/kfsm/tree/main/${project.name}/src/main/kotlin"))
        remoteLineSuffix.set("#L")
      }
    }
  }
}

// Configure Dokka multi-module task
dokka {
  moduleName.set("kfsm")
  moduleVersion.set(project.version.toString())
  dokkaPublications {
    register("multiModule") {
      outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
      includes.from("dokka-docs/module.md")
    }
  }
}

task("publishToMavenCentral") {
  group = "publishing"
  dependsOn(
    ":lib:publishToMavenCentral",
    ":lib-jooq:publishToMavenCentral"
  )
}
