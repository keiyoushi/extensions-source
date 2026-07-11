import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SeiManga"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "grouple"

    source {
        baseUrl {
            custom("https://1.seimanga.me")
        }
        lang = "ru"
    }
}
