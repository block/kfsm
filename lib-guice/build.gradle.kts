plugins {
  id("com.vanniktech.maven.publish.base") version "0.33.0"
}

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

tasks.jar {
  archiveBaseName.set("kfsm-guice")
}

java {
  withSourcesJar()
  withJavadocJar()
}

configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
  publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
  signAllPublications()
  
  coordinates("app.cash.kfsm", "kfsm-guice", project.version.toString())
  
  pom {
    name.set("kFSM Guice Integration")
    description.set("Guice integration for kFSM")
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
        organization.set("Block, Inc.")
        organizationUrl.set("https://block.xyz")
      }
    }
    scm {
      connection.set("scm:git:git://github.com/cashapp/kfsm.git")
      developerConnection.set("scm:git:ssh://github.com/cashapp/kfsm.git")
      url.set("https://github.com/cashapp/kfsm")
    }
  }
}
