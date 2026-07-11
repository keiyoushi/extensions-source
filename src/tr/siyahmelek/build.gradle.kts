plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Siyah Melek"
    versionCode = 62
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "initmanga"

    source {
        lang = "tr"
        baseUrl {
            custom("https://siyahmelek.fun")
        }
        versionId = 2
    }
}
