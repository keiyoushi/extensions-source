plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comiplex"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "gigaviewer"

    source {
        lang = "ja"
        baseUrl = "https://viewer.heros-web.com"
    }
}
