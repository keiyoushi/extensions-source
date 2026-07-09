plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Rose Squad Scans"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://rosesquadscans.aishiteru.org"
    }
}
