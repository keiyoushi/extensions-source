plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiArchive"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "it"
        baseUrl = "https://www.hentai-archive.com"
    }
}
