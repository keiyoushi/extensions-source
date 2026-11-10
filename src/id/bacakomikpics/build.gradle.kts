import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BacaKomik.pics"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl = "https://bacakomik.pics"
    }

    deeplink {
        path("/series/..*")
    }
}
