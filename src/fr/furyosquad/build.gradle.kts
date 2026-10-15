import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "FuryoSquad"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "fr"
        baseUrl = "https://www.furyosociety.com"
    }

    deeplink {
        host("www.furyosociety.com")
        host("furyosociety.com")
        path("/series/.*")
        path("/read/.*")
    }
}
