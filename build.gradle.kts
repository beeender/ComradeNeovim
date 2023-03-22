import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "org.beeender"
version = "0.1.3-SNAPSHOT"

buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath(kotlin("gradle-plugin", "1.3.21"))
    }
}

plugins {
    id("org.jetbrains.intellij") version "0.4.7"
    kotlin("jvm") version "1.3.21"
}

repositories { mavenCentral() }
dependencies {
    compile("org.msgpack", "msgpack-core", "0.8.16")
    compile("org.msgpack", "jackson-dataformat-msgpack", "0.8.16")
    compile("com.fasterxml.jackson.module", "jackson-module-kotlin", "2.9.8")
    compile("org.scala-sbt.ipcsocket", "ipcsocket", "1.0.0")
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.1.1")
    implementation("com.kohlschutter.junixsocket:junixsocket-core:2.6.2")
    testCompile("junit","junit", "4.12")
    testImplementation("io.mockk", "mockk", "1.9")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

intellij {
    updateSinceUntilBuild = false
    // If the instrumentCode is enabled, the Test cannot call internal method.
    // See https://github.com/JetBrains/gradle-intellij-plugin/issues/230
    instrumentCode = false
    version = "2019.1"
}
