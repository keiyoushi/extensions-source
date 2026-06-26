plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaNow"
    className = "MangaNow"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangareader"
    baseUrl = "https://manganow.to"
}
