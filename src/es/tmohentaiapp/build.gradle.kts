import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TMOHentai App"
    versionCode = 16
    libVersion = "1.6"
    contentWarning = ContentWarning.NSFW
    source {
        name = "TMOHentai App"
        lang = "es"
        baseUrl = "https://tmohentai.app"
    }
}
