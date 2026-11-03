import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Jestful"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        baseUrl = "https://jestful.net"
        lang = "ja"
    }

    deeplink {
        path("/hwms-..*")
        path("/bsaq-..*")
    }
}
