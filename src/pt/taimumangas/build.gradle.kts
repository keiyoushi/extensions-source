import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TaimuMangas"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        name = "Taimu Mangas"
        lang = "pt-BR"
        baseUrl = "https://beta.taimumangas.com"
    }
}
