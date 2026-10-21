import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "PandaChaika"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf(
        "all",
        "en",
        "zh",
        "ja",
        "id",
    ).forEach {
        source {
            lang = it
            baseUrl = "https://panda.chaika.moe"
        }
    }

    deeplink {
        host("panda.chaika.moe")
        path("/archive/..*")
    }
}
