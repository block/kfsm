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

tasks.jar {
  archiveBaseName.set("kfsm")
}

java {
  withSourcesJar()
  withJavadocJar()
}

configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
  publishToMavenCentral(automaticRelease = true)
  signAllPublications()
  pom {
    name.set("kFSM")
    description.set("Finite State Machinery for Kotlin")
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
