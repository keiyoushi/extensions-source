import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Rule34Comic"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://rule34comic.party"
    }

    deeplink {
        host("rule34comic.party")
        host("www.rule34comic.party")
        path("/comic/..*")
        path("/tags/..*")
        path("/categories/..*")
        path("/models/..*")
        path("/search/..*")
    }
}
