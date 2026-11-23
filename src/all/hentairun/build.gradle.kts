import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiRun"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf("all", "en", "ja", "es", "fr", "ko", "de", "ru").forEach { language ->
        source {
            lang = language
            baseUrl = "https://hentairun.com"
        }
    }

    deeplink {
        path("/gallery/..*")
        path("/view/..*")
        path("/tag/..*")
        path("/parody/..*")
        path("/character/..*")
        path("/artist/..*")
        path("/group/..*")
        path("/category/..*")
        path("/language/..*")
    }
}
