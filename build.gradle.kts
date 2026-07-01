import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.spring") version "2.3.20"
    kotlin("plugin.jpa") version "2.3.20"
    id("org.springframework.boot") version "4.1.0"

    // Review-loop gates: `./gradlew ktlintCheck detekt`.
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
    // detekt 2.x is published under the `dev.detekt` plugin id. The legacy
    // `io.gitlab.arturbosch.detekt` id only ships 1.23.x, which does not support
    // Gradle 9 / Kotlin 2.3; the 2.0.0-alpha line is the only one compatible with this stack.
    id("dev.detekt") version "2.0.0-alpha.5"
}

group = "xyz.stdiodh"
version = "0.0.1"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Spring Boot's BOM is imported as a Gradle platform (native BOM support),
// so the io.spring.dependency-management plugin is not required.
dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

    // Pinned to match the Kotlin plugin version so stdlib and reflect stay aligned.
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.3.20")

    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget = JvmTarget.JVM_21
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Produce a single, predictably-named boot jar for the Dockerfile.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("app.jar")
}

tasks.named<Jar>("jar") {
    enabled = false
}
