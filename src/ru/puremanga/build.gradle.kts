import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "PureManga"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "inkstory"

    source {
        lang = "ru"
        baseUrl = "https://v1.puremanga.me"
    }
}
