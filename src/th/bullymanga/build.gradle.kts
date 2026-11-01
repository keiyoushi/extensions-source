import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Bully Manga"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        baseUrl = "https://bully-manga.com"
        lang = "th"
    }

    deeplink {
        path("/..*")
    }
}
