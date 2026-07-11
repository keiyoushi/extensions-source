import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiDex"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"

    source {
        lang = "en"
        baseUrl = "https://dexhentai.com"
    }
}
