import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "6Manhua"
    versionCode = 0
    contentWarning = ContentWarning.NSFW // or MIXED, please confirm
    libVersion = "1.6"
    theme = "mccms"

    source {
        name = "六漫画"
        lang = "zh"
        baseUrl = "https://www.liumanhua.com"
        versionId = 3
    }
}
