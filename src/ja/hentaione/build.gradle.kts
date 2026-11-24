import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai-One"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "ja"
        baseUrl = "https://hentai-one.com"
    }

    deeplink {
        path("/articles/..*")
        path("/viewer")
        path("/categories/..*")
        path("/tags/..*")
        path("/artists/..*")
        path("/groups/..*")
        path("/parodies/..*")
        path("/characters/..*")
        path("/languages/..*")
    }
}
