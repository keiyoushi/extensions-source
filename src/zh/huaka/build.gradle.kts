import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Huaka Comic"
    versionCode = 23
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    source {
        name = "花咔漫画"
        lang = "zh"
        baseUrl = "https://app.huakacomic.com"
    }
}
