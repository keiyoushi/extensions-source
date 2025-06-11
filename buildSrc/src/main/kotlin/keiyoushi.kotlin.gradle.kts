plugins {
    kotlin("android")
    id("kotlinx-serialization")
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjvm-default=all-compatibility",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}
