plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "RavenManga"
    versionCode = 7
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://raventard.xyz"
    }
}
