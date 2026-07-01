plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Rumanhua"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mmlook"

    source {
        name = "如漫画"
        lang = "zh"
        baseUrl = "https://m.rumanhua2.com"
    }
}
