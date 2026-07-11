plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Picacomic"
    versionCode = 8
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        name = "哔咔漫画"
        lang = "zh"
        baseUrl = "https://picaapi.picacomic.com"
    }
}
