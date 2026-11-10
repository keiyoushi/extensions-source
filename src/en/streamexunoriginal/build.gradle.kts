import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "StreameX (unoriginal)"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://streamex.hn"
    }

    deeplink {
        host("streamex.hn")
        host("www.streamex.hn")
        host("streamex.net")
        host("www.streamex.net")
        host("streamex.sh")
        host("www.streamex.sh")
        path("/manga/..*")
    }
}
