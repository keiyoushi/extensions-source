plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comic Growl"
    versionCode = 8
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "comiciviewer"

    source {
        lang = "all"
        baseUrl = "https://comic-growl.com"
    }
}
