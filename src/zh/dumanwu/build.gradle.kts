plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dumanwu"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mmlook"

    source {
        name = "读漫屋"
        lang = "zh"
        baseUrl = "https://m.dumanwu1.com"
    }
}
