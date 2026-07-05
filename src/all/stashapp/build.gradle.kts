plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "StashApp"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl("http://localhost:9999") {
            withCustom = true
        }
    }
}
