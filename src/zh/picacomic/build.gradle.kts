import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Picacomic"
    versionCode = 8
    contentWarning = ContentWarning.NSFW // or MIXED, please confirm
    libVersion = "1.4"

    source {
        name = "哔咔漫画"
        lang = "zh"
        baseUrl = "https://picaapi.picacomic.com"
    }
}
