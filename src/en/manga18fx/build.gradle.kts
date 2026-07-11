plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga18fx"
    versionCode = 5
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://manga18fx.com"
        id = 3157287889751723714L
    }
}
