import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
  alias(libs.plugins.kotlinGradlePlugin) apply false
  alias(libs.plugins.kotlinBinaryCompatibilityPlugin) apply false
  alias(libs.plugins.versionsGradlePlugin)
  alias(libs.plugins.versionCatalogUpdateGradlePlugin)
  alias(libs.plugins.dokka)
  id("com.vanniktech.maven.publish.base") version "0.25.3" apply false
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

buildscript {
  repositories {
    mavenCentral()
  }
}

subprojects {
  buildscript {
    repositories {
      mavenCentral()
      gradlePluginPortal()
    }
  }

  repositories {
    mavenCentral()
  }

  apply(plugin = "java")
  apply(plugin = "kotlin")
  apply(plugin = rootProject.project.libs.plugins.kotlinBinaryCompatibilityPlugin.get().pluginId)
  apply(plugin = "com.vanniktech.maven.publish.base")
  plugins.withId("com.vanniktech.maven.publish.base") {
    configure<PublishingExtension> {
      repositories {
        maven {
          name = "testMaven"
          url = rootProject.layout.buildDirectory.dir("testMaven").get().asFile.toURI()
        }

        /*
         * Want to push to an internal repository for testing?
         * Set the following properties in ~/.gradle/gradle.properties.
         *
         * internalUrl=YOUR_INTERNAL_URL
         * internalUsername=YOUR_USERNAME
         * internalPassword=YOUR_PASSWORD
         *
         * Then run the following command to publish a new internal release:
         *
         * ./gradlew publishAllPublicationsToInternalRepository -DRELEASE_SIGNING_ENABLED=false
         */
        val internalUrl = providers.gradleProperty("internalUrl").orNull
        if (internalUrl != null) {
          maven {
            name = "internal"
            url = URI(internalUrl)
            credentials {
              username = providers.gradleProperty("internalUsername").get()
              password = providers.gradleProperty("internalPassword").get()
            }
          }
        }
      }
    }
    configure<MavenPublishBaseExtension> {
      publishToMavenCentral(automaticRelease = true)
      signAllPublications()
      pom {
        description.set("Adds parameters to tests")
        name.set(project.name)
        url.set("https://github.com/cashapp/burst/")
        licenses {
          license {
            name.set("The Apache Software License, Version 2.0")
            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            distribution.set("repo")
          }
        }
        developers {
          developer {
            id.set("cashapp")
            name.set("Cash App")
          }
        }
        scm {
          url.set("https://github.com/cashapp/burst/")
          connection.set("scm:git:https://github.com/cashapp/burst.git")
          developerConnection.set("scm:git:ssh://git@github.com/cashapp/burst.git")
        }
      }
    }
  }


  configure<JavaPluginExtension> {
    withSourcesJar()
    withJavadocJar()
  }

  apply(plugin = "version-catalog")

  // Only apply if the project has the kotlin plugin added:
  plugins.withType<KotlinPluginWrapper> {
    val compileKotlin by tasks.getting(KotlinCompile::class) {
      kotlinOptions {
        jvmTarget = "11"
        allWarningsAsErrors = true
      }
    }
    val compileTestKotlin by tasks.getting(KotlinCompile::class) {
      kotlinOptions {
        jvmTarget = "11"
        allWarningsAsErrors = true
      }
    }

    dependencies {
      add("testImplementation", project.rootProject.libs.junitApi)
      add("testRuntimeOnly", project.rootProject.libs.junitEngine)
    }

    tasks.withType<GenerateModuleMetadata> {
      suppressedValidationErrors.add("enforced-platform")
    }
  }

  tasks.withType<Test> {
    dependsOn("apiCheck")
    useJUnitPlatform()
    testLogging {
      events("started", "passed", "skipped", "failed")
      exceptionFormat = TestExceptionFormat.FULL
      showStandardStreams = false
    }
  }

  apply(plugin = "com.github.ben-manes.versions")

  tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
    revision = "release"
    resolutionStrategy {
      componentSelection {
        all {
          if (isNonStable(candidate.version) && !isNonStable(currentVersion)) {
            reject("Release candidate")
          }
        }
      }
    }
  }

}

fun isNonStable(version: String): Boolean {
  val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
  val regex = "^[0-9,.v-]+(-r)?$".toRegex()
  val isStable = stableKeyword || regex.matches(version)
  return isStable.not()
}

// this needs to be defined here for the versionCatalogUpdate
tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
  revision = "release"
  resolutionStrategy {
    componentSelection {
      all {
        if (isNonStable(candidate.version) && !isNonStable(currentVersion)) {
          reject("Release candidate")
        }
      }
    }
  }
}

versionCatalogUpdate {
  /**
   * Use @pin and @keep in gradle/lib.versions.toml instead of defining here
   */
  sortByKey.set(true)
}
