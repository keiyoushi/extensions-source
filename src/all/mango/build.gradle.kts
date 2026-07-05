plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mango"
    versionCode = 11
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl("http://127.0.0.1:9000") {
            withCustom = true
        }
    }
}

dependencies {
    implementation("info.debatty:java-string-similarity:2.0.0")
}
