import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Softkomik"
    versionCode = 5
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl {
            mirrors(
                "https://softkomik.app",
                "https://softkomik.online",
            )
        }
    }

    deeplink {
        host("softkomik.app")
        host("www.softkomik.app")
        host("softkomik.online")
        path("/..*")
    }
}
