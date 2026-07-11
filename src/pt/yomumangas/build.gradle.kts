plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yomu Mangás"
    versionCode = 5
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://yomumangas.com"
    }
}
