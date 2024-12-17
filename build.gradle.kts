import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.intellij") version "1.13.3"
    kotlin("jvm") version "1.8.20"
}

group = "org.beeender"
version = "0.1.5-SNAPSHOT"

repositories { 
    mavenCentral()
    maven { url = uri("https://www.jetbrains.com/intellij-repository/releases") }
    maven { url = uri("https://cache-redirector.jetbrains.com/intellij-dependencies") }
}

dependencies {
    implementation("org.msgpack:msgpack-core:0.9.5")
    implementation("org.msgpack:jackson-dataformat-msgpack:0.9.5")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.0")
    implementation("org.scala-sbt.ipcsocket:ipcsocket:1.6.1")
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0")
    implementation("com.kohlschutter.junixsocket:junixsocket-core:2.6.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.5")
}

intellij {
    version.set("2023.1")
    type.set("IC") // IntelliJ IDEA Community Edition
    updateSinceUntilBuild.set(false)
    instrumentCode.set(false)
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
        }
    }

    buildSearchableOptions {
        enabled = false
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
