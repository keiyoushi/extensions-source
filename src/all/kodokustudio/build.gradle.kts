plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kodoku Studio"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "all"
        baseUrl = "https://kodokustudio.com"
    }
}
