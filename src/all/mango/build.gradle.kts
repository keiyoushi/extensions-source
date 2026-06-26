plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mango"
    className = "Mango"
    versionCode = 11
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation("info.debatty:java-string-similarity:2.0.0")
}
