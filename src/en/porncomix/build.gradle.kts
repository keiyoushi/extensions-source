plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "PornComix"
    versionCode = 49
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://bestporncomix.com"
        versionId = 2
    }
}
