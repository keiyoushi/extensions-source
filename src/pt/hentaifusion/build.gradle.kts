import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai Fusion"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    source {
        lang = "pt"
        baseUrl = "https://hentaifusion.me"
    }
}
