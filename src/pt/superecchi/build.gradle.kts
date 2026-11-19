import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Super Ecchi"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "pt-BR"
        baseUrl = "https://superecchi.com"
    }

    deeplink {
        path("/livro.php")
        path("/leitor.php")
    }
}
