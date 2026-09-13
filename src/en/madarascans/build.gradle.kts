import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Madara Scans"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "mangathemesia"

    source {
        lang = "en"
        baseUrl {
            mirrors(
                "https://madarascans.org",
                "https://madarascans.com",
            )
        }
    }
}
