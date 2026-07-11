import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Astral-Manga"
    versionCode = 48
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "AstralManga"
        lang = "fr"
        baseUrl = "https://astral-manga.fr"
        versionId = 2
    }
}
