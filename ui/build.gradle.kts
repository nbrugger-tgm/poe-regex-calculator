plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

group = "poeregex"
version = "0.1"

repositories {
    mavenCentral()
}

dependencies {
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
