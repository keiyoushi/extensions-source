import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Wurmz"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl = "https://wurmz.net"
    }

    deeplink {
        host("wurmz.net")
        host("www.wurmz.net")
        path("/detail/.*")
    }
}
