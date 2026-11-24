import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yaoi Haven Reborn"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "https://yaoihavenreborn.com"
    }

    deeplink {
        host("yaoihavenreborn.com")
        host("www.yaoihavenreborn.com")
        path("/doujinshi/")
        path("/doujinshi/..*")
    }
}
