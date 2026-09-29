import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaRead"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

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
