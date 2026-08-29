import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MeHentai"
    versionCode = 7
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "manhwaz"

    source {
        lang = "vi"
        baseUrl = "https://mehentai.live"
    }
}
