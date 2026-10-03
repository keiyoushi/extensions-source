import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kiutaku"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "https://kiutaku.com"
        id = 3040035304874076216L
    }

    deeplink {
        host("kiutaku.com")
        path("/..*")
    }
}
