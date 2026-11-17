import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NukiBooks"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "ヌキブックス"
        lang = "ja"
        baseUrl = "https://nukibooks.com"
    }

    deeplink {
        path("/articles/..*")
        path("/categories/..*")
        path("/tags/..*")
        path("/artists/..*")
        path("/groups/..*")
        path("/parodies/..*")
        path("/characters/..*")
    }
}
