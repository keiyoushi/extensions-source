import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Paradise Scans"
    versionCode = 23
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://paradisescans.com"
        id = 5928300995303689257L
    }

    deeplink {
        path("/series/..*")
    }
}
