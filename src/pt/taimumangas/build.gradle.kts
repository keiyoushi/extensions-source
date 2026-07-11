plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TaimuMangas"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        name = "Taimu Mangas"
        lang = "pt-BR"
        baseUrl = "https://beta.taimumangas.com"
    }
}
