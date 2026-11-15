import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "I Think I Like You"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "https://ithinkilikeyou.net"
    }

    deeplink {
        host("ithinkilikeyou.net")
        host("www.ithinkilikeyou.net")
        path("/series/..*")
        path("/comic/..*")
    }
}
