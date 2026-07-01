plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Mura"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangareader"

    source {
        lang = "ja"
        baseUrl = "https://mangamura.me"
    }
}
