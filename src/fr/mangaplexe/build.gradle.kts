import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangaplexe"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "fr"
        baseUrl = "https://www.mangaplexe.com"
    }

    deeplink {
        host("www.mangaplexe.com")
        host("mangaplexe.com")
        path("/category/..*")
        path("/heroines-hentai/.*")
        path("/parodies-hentai/.*")
        path("/porno-hentai/.*")
    }
}
