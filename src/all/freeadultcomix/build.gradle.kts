import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Free Adult Comix"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://freeadultcomix.com"
    }

    source {
        lang = "es"
        baseUrl = "https://freeadultcomix.com"
    }

    deeplink {
        path("/..*")
    }
}
