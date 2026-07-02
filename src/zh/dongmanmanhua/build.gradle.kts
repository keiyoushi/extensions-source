plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dongman Manhua"
    versionCode = 6
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "zh-Hans"
        baseUrl = "https://www.dongmanmanhua.cn"
        id = 4222375517460530289
    }
}
