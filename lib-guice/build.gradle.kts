plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    api(project(":lib"))
    
    // Guice for dependency injection
    api("com.google.inject:guice:7.0.0")
    
    // Reflections library for classpath scanning
    implementation("org.reflections:reflections:0.10.2")
    
    // Kotlin reflection (usually provided by the Kotlin plugin, but explicitly declared for clarity)
    implementation(kotlin("reflect"))
    
    // Test dependencies
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("io.mockk:mockk:1.13.10")
}

tasks.test {
    useJUnitPlatform()
}