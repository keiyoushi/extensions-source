import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai Cosplay"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "hentaicosplayxxx"

    source {
        lang = "all"
        baseUrl = "https://hentai-cosplay-xxx.com"
    }
}
