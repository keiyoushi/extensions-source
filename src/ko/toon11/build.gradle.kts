import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "11toon"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "ko"
        baseUrl {
            mirrors(
                "11toon" to "https://www.11toon.com",
                "K만화" to "https://kmana10.net",
            )
        }
    }

    deeplink {
        host("www.11toon.com")
        host("11toon.com")
        path("/bbs/board.php")
    }

    // K만화: series (/episode/<id>/<page>/1) and episodes (/detail/<id>/<episode>)
    deeplink {
        host("kmana10.net")
        path("/episode/..*")
        path("/detail/..*")
    }
}
