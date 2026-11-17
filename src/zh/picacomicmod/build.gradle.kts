import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Picacomic Mod"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "哔咔漫画（自用）"
        lang = "zh"
        baseUrl = "https://manhuabika.com"
    }
}
