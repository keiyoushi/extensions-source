import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comics Tracker"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "fr"
        baseUrl = "https://comics-tracker.net"
    }

    deeplink {
        host("comics-tracker.net")
        host("www.comics-tracker.net")
        path("/comics/..*")
        path("/read")
        path("/vf/..*")
        path("/recent")
        path("/search")
    }
}
