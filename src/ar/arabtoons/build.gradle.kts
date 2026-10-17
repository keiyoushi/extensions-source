import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Arab Toons"
    versionCode = 57
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "عرب تونز"
        lang = "ar"
        baseUrl = "https://arabtoons.net"
    }

    deeplink {
        path("/manga/..*")
    }
}
