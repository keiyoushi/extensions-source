plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaRead"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl("https://manhwaread.com") {
            mirrors = listOf("https://manhwaread.org")
        }
    }

    deeplink {
        host("manhwaread.com")
        host("manhwaread.org")
        path("/manhwa/..*")
    }
}
