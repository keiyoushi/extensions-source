plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Existential Comics"
    versionCode = 5
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://existentialcomics.com"
    }
}
