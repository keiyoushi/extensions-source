import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Multi"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "tr"
        baseUrl = "https://www.mangamulti.com"
    }

    source {
        lang = "en"
        baseUrl = "https://www.mangamulti.com"
    }

    source {
        lang = "fr"
        baseUrl = "https://www.mangamulti.com"
    }

    source {
        lang = "es"
        baseUrl = "https://www.mangamulti.com"
    }

    source {
        lang = "ru"
        baseUrl = "https://www.mangamulti.com"
    }

    source {
        lang = "ar"
        baseUrl = "https://www.mangamulti.com"
    }

    source {
        lang = "pt-BR"
        baseUrl = "https://www.mangamulti.com"
    }

    deeplink {
        host("www.mangamulti.com")
        host("mangamulti.com")
        path("/tr/..*/..*")
        path("/en/..*/..*")
        path("/fr/..*/..*")
        path("/es/..*/..*")
        path("/ru/..*/..*")
        path("/ar/..*/..*")
        path("/pt/..*/..*")
    }
}
