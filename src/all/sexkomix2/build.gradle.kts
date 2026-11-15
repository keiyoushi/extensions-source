import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SexKomix2"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf("en", "es", "pt", "de", "ru").forEach {
        source {
            lang = it
            baseUrl = "https://sexkomix2.com"
        }
    }

    deeplink {
        path("/comicsx/..*")
        path("/comicsx_en/..*")
        path("/comicsx_es/..*")
        path("/comicsx_pt/..*")
        path("/comicsx_de/..*")
    }
}
