import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangakuri"
    versionCode = 35
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl = "https://lc2.mangakuri.online"
        versionId = 2
    }

    deeplink {
        path("/comic/..*")
    }
}
