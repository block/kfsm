plugins {
    kotlin("jvm")
    `java-library`
    id("com.vanniktech.maven.publish.base") version "0.25.3"
}

dependencies {
    // Core KFSM library
    api(project(":lib"))
    
    // Guice for dependency injection
    api(rootProject.libs.guice)
    
    // Reflections library for classpath scanning
    api(rootProject.libs.reflections)
    
    // Kotlin reflection (usually provided by the Kotlin plugin, but explicitly declared for clarity)
    implementation(kotlin("reflect"))
    
    // Test dependencies
    testImplementation(kotlin("test"))
    testImplementation(rootProject.libs.junitApi)
    testImplementation(rootProject.libs.kotestAssertions)
    testImplementation(rootProject.libs.kotestJunitRunnerJvm)
    testImplementation(rootProject.libs.kotestProperty)
    testImplementation("io.mockk:mockk:1.13.10")
    
    testRuntimeOnly(rootProject.libs.slf4jSimple)
    testRuntimeOnly(rootProject.libs.junitEngine)
}

tasks.test {
    useJUnitPlatform()
}

// Configure Java compatibility
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
        allWarningsAsErrors = true
    }
}
