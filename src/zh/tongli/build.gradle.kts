plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tongli"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "東立"
        lang = "zh"
        baseUrl = "https://ebook.tongli.com.tw"
    }
}
