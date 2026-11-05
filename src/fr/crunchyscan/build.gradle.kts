import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "CrunchyScan (unoriginal)"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "fr"
        baseUrl {
            custom("https://crunchyscan.st")
        }
        id = 5122789490740525400L
    }

    deeplink {
        path("/..*")
    }
}
