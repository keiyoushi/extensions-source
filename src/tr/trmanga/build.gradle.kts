import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tr Manga"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        name = "TrManga"
        lang = "tr"
        baseUrl = "https://trmanga.com"
    }
}
