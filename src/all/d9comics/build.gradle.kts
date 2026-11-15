import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "D9 Comics"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://d9comics.com"
    }

    source {
        lang = "en"
        baseUrl = "https://d9comics.com"
    }

    source {
        lang = "pt-BR"
        baseUrl = "https://d9comics.com"
    }

    deeplink {
        host("d9comics.com")
        host("www.d9comics.com")
        path("/series/..*")
        path("/comics-online-.*")
        path("/private/comics-online-.*")
    }
}
