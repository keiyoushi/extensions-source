import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MirrorKomik"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl {
            mirrors(
                "https://mirrorkomik.info",
                "https://mirrorinkomik.my.id",
                "https://mirrorkomik.click",
            )
        }
    }

    deeplink {
        path("/manga/..*")
        path("/manhwa/..*")
        path("/manhua/..*")
    }
}
