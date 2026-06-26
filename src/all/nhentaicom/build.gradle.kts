plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "nHentai.com (unoriginal)"
    className = "NHentaiComFactory"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "hentaihand"
    baseUrl = "https://nhentai.com"
}
