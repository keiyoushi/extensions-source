plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Xiao Si"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "zh"
        baseUrl = "https://www.jjmhw2.top"
    }
}
