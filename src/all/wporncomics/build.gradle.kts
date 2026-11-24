import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "W Porn Comics"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://wporncomics.com"
    }

    source {
        lang = "es"
        baseUrl = "https://es.wporncomics.com"
    }

    source {
        lang = "de"
        baseUrl = "https://de.wporncomics.com"
    }

    source {
        lang = "pt-BR"
        baseUrl = "https://pt.wporncomics.com"
    }

    deeplink {
        path("/comix1/..*")
        path("/content_comix/..*")
    }
}
