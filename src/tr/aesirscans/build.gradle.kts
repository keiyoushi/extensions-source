import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AesirScans"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "zeistmanga"

    source {
        lang = "tr"
        baseUrl = "https://www.aesirscans.site"
    }

    deeplink {
        host("aesirscans.site")
        path("/..*/..*/..*.html")
    }
}
