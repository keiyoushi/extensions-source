plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga-shi"
    versionCode = 52
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "ru"
        baseUrl = "https://manga-shi.org"
    }

    deeplink {
        host("manga-shi.org")
        path("/manga/..*")
    }
}
