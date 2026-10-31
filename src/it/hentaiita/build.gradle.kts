import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai ITA"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "Hentai ITA"
        lang = "it"
        baseUrl = "https://hentai-ita.net"
        id = 5279259667822182743L
    }

    deeplink {
        path("/..*")
    }
}
