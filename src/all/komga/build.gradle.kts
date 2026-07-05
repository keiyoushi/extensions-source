plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Komga"
    versionCode = 66
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "Komga"
        lang = "all"
        baseUrl = "https://127.0.0.1"
        skipCodeGen = true
    }
}

dependencies {
    implementation("org.apache.commons:commons-text:1.11.0")
}
