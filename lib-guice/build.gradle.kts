plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm") version "1.9.20"
  id("com.vanniktech.maven.publish") version "0.30.0"
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

dependencies {
  api(project(":lib"))
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.20")
  implementation("com.google.inject:guice:5.1.0")
  implementation("org.reflections:reflections:0.10.2")
  
  // Test dependencies
  testImplementation("io.kotest:kotest-assertions-core:5.8.0")
  testImplementation("io.kotest:kotest-runner-junit5-jvm:5.8.0")
  testImplementation("io.kotest:kotest-property:5.8.0")
  testImplementation("io.mockk:mockk:1.13.10")
  testImplementation("jakarta.inject:jakarta.inject-api:2.0.1")
}

configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
  pom {
    name.set("kfsm-guice")
    description.set("Guice integration for kfsm")
    url.set("https://github.com/cashapp/kfsm")
    licenses {
      license {
        name.set("The Apache License, Version 2.0")
        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
      }
    }
    developers {
      developer {
        id.set("cashapp")
        name.set("Cash App")
      }
    }
    scm {
      url.set("https://github.com/cashapp/kfsm")
      connection.set("scm:git:https://github.com/cashapp/kfsm.git")
      developerConnection.set("scm:git:ssh://git@github.com/cashapp/kfsm.git")
    }
  }
}
