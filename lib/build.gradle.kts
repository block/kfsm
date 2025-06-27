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

publishing {
  publications {
    create<MavenPublication>("maven") {
      from(components["java"])
      
      groupId = "app.cash.kfsm"
      artifactId = "kfsm"
      version = project.version.toString()
      
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
  }
  
  repositories {
    maven {
      name = "OSSRH"
      url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
      credentials {
        username = providers.gradleProperty("ossrhUsername").orNull ?: ""
        password = providers.gradleProperty("ossrhPassword").orNull ?: ""
      }
    }
  }
}

signing {
  val signingKey = providers.gradleProperty("signingInMemoryKey").orNull
  val signingPassword = providers.gradleProperty("signingInMemoryKeyPassword").orNull
  
  if (signingKey != null && signingPassword != null) {
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["maven"])
  }
}
