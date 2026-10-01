import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TaimuMangas"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "Taimu Mangas"
        lang = "pt-BR"
        baseUrl = "https://beta.taimumangas.com"
    }
}
