import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BacaKomik"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "komikindo"

    source {
        lang = "id"
        baseUrl = "https://bacakomik.my"
    }
}
