plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Solar and Sundry"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://sas-api.fly.dev"
    }
}
