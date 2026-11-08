import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "JabraScan"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://jabrascan.net"
    }

    // Works and chapters are pages of the home page: /#<work> and /#<work>/Chapter<number>
    deeplink {
        path("/")
    }
}
