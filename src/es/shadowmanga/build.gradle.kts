import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Shadow Manga"
    versionCode = 4
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "Shadow Manga"
        lang = "es"
        baseUrl = "https://shademanga.com"
        id = 5649269152264667286L
    }

    source {
        name = "Shadow Manga (+18)"
        lang = "es"
        baseUrl = "https://shademanga.com"
    }

    deeplink {
        host("shademanga.com")
        path("/serie/local/.*")
        path("/adultos/manga/.*")
    }
}
