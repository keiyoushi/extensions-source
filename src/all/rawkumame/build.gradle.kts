import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaMachina"
    versionCode = 4
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    listOf("en", "es").forEach { language ->
        source {
            lang = language
            baseUrl = "https://mangamachina.com"
            id = if (language == "en") 5509878196968851775L else 3962273475579482161L
        }
    }

    deeplink {
        host("mangamachina.com")
        host("rawkuma.me")
        path("/manga/..*")
        path("/es/manga/..*")
    }
}
