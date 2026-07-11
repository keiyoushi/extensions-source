import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaZone"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://manhwazone.com"
    }

    deeplink {
        host("manhwazone.com")
        host("www.manhwazone.com")
        path("/series/..*")
    }
}
