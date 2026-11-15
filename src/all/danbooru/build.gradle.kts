import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Danbooru"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "https://danbooru.donmai.us"
    }

    deeplink {
        host("danbooru.donmai.us")
        path("/pools/..*")
    }
}
