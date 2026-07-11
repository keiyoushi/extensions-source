plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "InkStory"
    versionCode = 4
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "ru"
        baseUrl = "https://inkstory.net"
    }
}
