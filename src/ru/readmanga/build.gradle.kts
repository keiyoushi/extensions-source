import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ReadManga"
    versionCode = 48
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "grouple"

    source {
        baseUrl {
            custom("https://a.zazaza.me")
        }
        lang = "ru"
        id = 5L
    }
}
