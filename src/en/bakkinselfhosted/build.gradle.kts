plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Bakkin Self-hosted"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "bakkin"

    source {
        lang = "en"
        baseUrl("http://127.0.0.1/") {
            withCustom = true
        }
    }
}
