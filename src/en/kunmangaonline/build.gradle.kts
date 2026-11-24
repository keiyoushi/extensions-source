import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kun Manga Online"
    versionCode = 64
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "zinmangaart"

    source {
        lang = "en"
        baseUrl = "https://www.kunmanga.online"
    }
}
