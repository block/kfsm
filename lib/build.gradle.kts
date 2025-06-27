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
  withJavadocJar()
}

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.20")
  implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.20")
  
  // Test dependencies
  testImplementation("io.kotest:kotest-assertions-core:5.8.0")
  testImplementation("io.kotest:kotest-runner-junit5-jvm:5.8.0")
  testImplementation("io.kotest:kotest-property:5.8.0")
  testImplementation("io.mockk:mockk:1.13.10")
}

configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
  // publishToMavenCentral()
  // signAllPublications()
  pom {
    name.set("kfsm")
    description.set("A Kotlin Finite State Machine library")
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
