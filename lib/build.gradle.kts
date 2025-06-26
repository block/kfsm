import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URL

plugins {
  `java-library`
  `maven-publish`
  id("com.bmuschko.docker-remote-api") version "9.3.0"
  id("com.vanniktech.maven.publish.base") version "0.25.3"
}

dependencies {
  implementation(kotlin("reflect"))
  implementation(libs.kotlinLoggingJvm)

  testImplementation(libs.junitApi)
  testImplementation(libs.kotestAssertions)
  testImplementation(libs.kotestJunitRunnerJvm)
  testImplementation(libs.kotestAssertions)
  testImplementation(libs.kotestJunitRunnerJvm)
  testImplementation(libs.kotestProperty)

  testRuntimeOnly(libs.slf4jSimple)
  testRuntimeOnly(libs.junitEngine)

  apply(plugin = libs.plugins.dokka.get().pluginId)
}

tasks.withType<DokkaTask>().configureEach {
  dokkaSourceSets {
    named("main") {
      moduleName.set("Kotlin FSM")

      // Includes custom documentation
      includes.from("module.md")

      // Points source links to GitHub
      sourceLink {
        localDirectory.set(file("src/main/kotlin"))
        remoteUrl.set(URL("https://github.com/block/kfsm/tree/main/lib/src/main/kotlin"))
        remoteLineSuffix.set("#L")
      }
    }
  }
}

// Configure publishing
publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      from(components["java"])
      
      // Optional: customize POM
      pom {
        name.set("Kotlin FSM")
        description.set("Finite State Machinery for Kotlin")
        url.set("https://github.com/block/kfsm/")
      }
    }
  }
}
