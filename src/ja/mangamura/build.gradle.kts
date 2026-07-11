plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Mura"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangareader"

    source {
        lang = "ja"
        baseUrl = "https://mangamura.me"
    }
}
