plugins {
    alias(kei.plugins.library)
}

dependencies {
    implementation("org.kotlincrypto.hash:blake2:0.8.0") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
}
