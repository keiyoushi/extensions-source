import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MintManga"
    versionCode = 47
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "grouple"

    source {
        baseUrl {
            custom("https://2.mintmanga.one")
        }
        lang = "ru"
        id = 6L
    }
}
