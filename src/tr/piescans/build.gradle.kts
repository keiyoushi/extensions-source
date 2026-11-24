import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Piescans"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "tr"
        baseUrl = "https://piescans.com"
    }

    deeplink {
        host("piescans.com")
        host("www.piescans.com")
        path("/..*")
    }
}
