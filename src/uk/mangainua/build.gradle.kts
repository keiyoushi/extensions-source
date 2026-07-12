import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaInUa"
    versionCode = 12
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        name = "MANGA/in/UA"
        lang = "uk"
        baseUrl = "https://manga.in.ua"
    }
}
