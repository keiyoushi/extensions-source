plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai3z.CC"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "manga18"

    source {
        lang = "en"
        baseUrl = "https://hentai3z.cc"
    }
}
