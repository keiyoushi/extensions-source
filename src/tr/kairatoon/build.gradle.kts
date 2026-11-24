import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kairatoon"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "tr"
        baseUrl = "https://kairatoon.net"
    }

    deeplink {
        host("kairatoon.net")
        host("www.kairatoon.net")
        host("kairatoon.com")
        host("www.kairatoon.com")
        path("/webtoon/..*")
        path("/tur/..*")
        path("/katalog")
    }
}
