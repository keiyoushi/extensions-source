plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "StashApp"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl {
            custom("http://localhost:9999")
        }
    }
}
