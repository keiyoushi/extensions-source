import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Narasi Ninja"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "NarasiNinja"
        lang = "id"
        baseUrl = "https://narasininja.net"
    }

    deeplink {
        host("narasininja.net")
        host("www.narasininja.net")
        path("/komik/.*")
    }
}
