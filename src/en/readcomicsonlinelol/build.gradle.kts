import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ReadComicsOnline.lol"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://readcomicsonline.lol"
    }

    deeplink {
        host("readcomicsonline.lol")
        host("www.readcomicsonline.lol")
        path("/comic/..*")
        path("/comics")
        path("/search")
        path("/genre/..*")
        path("/publisher/..*")
        path("/character/..*")
    }
}
