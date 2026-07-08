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
        baseUrl {
            mirrors(
                "https://manhwaread.com",
                "https://manhwaread.org",
            )
        }
    }

    deeplink {
        host("manhwaread.com")
        host("manhwaread.org")
        path("/manhwa/..*")
    }
}
