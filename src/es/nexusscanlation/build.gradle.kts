import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NexusScanlation"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://nexusscanlation.com"
    }

    deeplink {
        host("nexusscanlation.com")
        host("www.nexusscanlation.com")
        path("/series/.*")
    }
}
