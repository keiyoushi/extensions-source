import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lezhin"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://www.lezhin.es"
    }

    source {
        lang = "fr"
        baseUrl = "https://www.lezhinfr.com"
    }

    source {
        lang = "de"
        baseUrl = "https://www.lezhinde.com"
    }

    source {
        lang = "th"
        baseUrl = "https://www.lezhinth.com"
    }

    deeplink {
        path("/detail/..*")
        path("/viewer/..*")
    }
}

dependencies {
    implementation(project(":lib:i18n"))
}
