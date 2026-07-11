plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Danbooru"
    versionCode = 4
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://danbooru.donmai.us"
    }

    deeplink {
        host("danbooru.donmai.us")
        path("/pools/..*")
    }
}
