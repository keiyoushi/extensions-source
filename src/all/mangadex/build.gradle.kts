import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaDex"
    versionCode = 212
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    listOf(
        "af", "sq", "ar", "az", "eu", "be", "bn", "bg", "my", "ca", "zh-Hans", "zh-Hant",
        "cv", "hr", "cs", "da", "nl", "en", "eo", "et", "fil", "fi", "fr", "ka", "de", "el",
        "he", "hi", "hu", "ga", "id", "it", "ja", "jv", "kk", "ko", "la", "lt", "ms", "mn",
        "ne", "no", "fa", "pl", "pt-BR", "pt", "ro", "ru", "sr", "sk", "es-419", "es", "sv",
        "ta", "te", "th", "tr", "uk", "ur", "uz", "vi",
    ).forEach {
        source {
            lang = it
            baseUrl = "https://mangadex.org"
        }
    }

    deeplink {
        host("mangadex.org")
        host("canary.mangadex.dev")
        path("/title/..*")
        path("/manga/..*")
        path("/chapter/..*")
        path("/group/..*")
        path("/author/..*")
        path("/user/..*")
        path("/list/..*")
    }
}

dependencies {

    implementation(project(":lib:i18n"))
}
