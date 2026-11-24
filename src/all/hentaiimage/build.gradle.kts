import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai Image"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "hentaicosplayxxx"

    source {
        lang = "all"
        baseUrl = "https://hentai-img.com"
    }
}
