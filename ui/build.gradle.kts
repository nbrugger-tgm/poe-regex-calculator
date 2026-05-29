plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("com.google.devtools.ksp") version "2.2.0-2.0.2"
    id("io.micronaut.application") version "4.5.4"
    application
}

group = "poeregex"
version = "0.1"

repositories {
    mavenCentral()
}

dependencies {
    ksp("io.micronaut:micronaut-http-validation")
    ksp("io.micronaut.serde:micronaut-serde-processor")
    implementation(libs.kotlinx.serialization.json)
    implementation(project(":regex-builder"))
    implementation("eu.niton.lifewire:lifewire:0.1")
    implementation("eu.nitonfx.signaling:lib")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "poeregex.ui.MainKt"
}
