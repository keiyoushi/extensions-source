plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comic Gardo"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "gigaviewer"

    source {
        lang = "ja"
        baseUrl = "https://comic-gardo.com"
    }
}
