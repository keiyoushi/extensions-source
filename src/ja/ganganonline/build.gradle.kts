plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Gangan Online"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://www.ganganonline.com"
    }
}
