import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "The Wandering Inn"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl = "https://webcomic.wanderinginn.com"
        lang = "en"
    }

    deeplink {
        path("/table-of-contents/")
        path("/chapter/..*")
        path("/chapter-..*")
        path("/boc-x-twi-..*")
        path("/tag/..*")
        path("/category/mini-comics/")
        path("/collection/..*")
    }
}
