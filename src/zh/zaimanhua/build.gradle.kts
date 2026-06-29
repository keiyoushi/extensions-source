plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Zaimanhua"
    versionCode = 18
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "再漫画"
        lang = "zh"
        baseUrl = "https://manhua.zaimanhua.com"
    }
}
