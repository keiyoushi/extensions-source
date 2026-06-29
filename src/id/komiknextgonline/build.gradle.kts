plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Komik Next G Online"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "id"
        baseUrl = "https://komiknextgonline.com"
    }
}
