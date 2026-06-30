plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BacaKomik"
    versionCode = 15
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "id"
        baseUrl = "https://bacakomik.my"
    }
}
