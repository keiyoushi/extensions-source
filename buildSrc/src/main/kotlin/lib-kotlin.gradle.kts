import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    `java-library`
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

val libs = the<LibrariesForLibs>()

dependencies {
    compileOnly(libs.kotlin.stdlib)
}
