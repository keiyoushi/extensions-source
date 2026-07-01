plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HERO'S Web"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "comiciviewer"

    source {
        lang = "ja"
        baseUrl = "https://heros-web.com"
    }
}
