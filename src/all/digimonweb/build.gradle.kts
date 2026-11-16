import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Digimon Web"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "ja"
        baseUrl = "https://digimon.net"
    }

    source {
        lang = "en"
        baseUrl = "https://digimon.net"
    }

    source {
        lang = "zh"
        baseUrl = "https://digimon.net"
    }

    deeplink {
        host("digimon.net")
        host("www.digimon.net")
        path("/digimoncomic/.*")
        path("/digimondreamers/.*")
        path("/digimon_comicaward2023/.*")
    }
}
