import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Danbooru"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://danbooru.donmai.us"
    }

    deeplink {
        host("danbooru.donmai.us")
        path("/pools/..*")
    }
}
