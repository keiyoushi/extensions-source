import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangaworld"
    versionCode = 9
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangaworld"

    source {
        lang = "it"
        baseUrl = "https://www.mangaworld.mx"
    }
}
