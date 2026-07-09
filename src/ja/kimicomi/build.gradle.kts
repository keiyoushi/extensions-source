plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KimiComi"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "comiciviewer"

    source {
        lang = "ja"
        baseUrl = "https://kimicomi.com"
    }
}
