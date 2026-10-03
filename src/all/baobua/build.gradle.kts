import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BaoBua"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "https://baobua.net"
    }

    deeplink {
        host("baobua.net")
        path("/category/..*")
        path("/spot/..*")
    }
}
