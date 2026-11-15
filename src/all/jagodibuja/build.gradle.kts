import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Jago Dibuja"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://jagodibuja.com"
    }
    source {
        lang = "es"
        baseUrl = "https://jagodibuja.com"
    }

    deeplink {
        path("/living-with-hipster-and-gamer-girl-es/")
        path("/web-comic-living-with-hipstergirl-and-gamergirl-en/")
        path("/comic-lwhg/")
        path("/comic-lwhg-pag-1-english/")
        path("/diario-magico/")
    }
}
