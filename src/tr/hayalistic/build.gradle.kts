plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hayalistic"
    versionCode = 5
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "tr"
        baseUrl = "https://hayalistic.blog"
    }
}
