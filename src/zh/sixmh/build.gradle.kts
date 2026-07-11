plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "6Manhua"
    versionCode = 8
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mccms"

    source {
        name = "六漫画"
        lang = "zh"
        baseUrl = "https://www.liumanhua.com"
        versionId = 3
    }
}
