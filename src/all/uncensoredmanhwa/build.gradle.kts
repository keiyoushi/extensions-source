plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Uncensored Manhwa"
    className = "UncensoredManhwaFactory"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"
    baseUrl = "https://uncensoredmanhwa.us"
}
