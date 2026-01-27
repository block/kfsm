pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "kfsm"

include(":lib")
include(":lib-jooq")
include(":example")
