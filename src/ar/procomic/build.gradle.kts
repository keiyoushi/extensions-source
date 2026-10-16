import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ProComic"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        baseUrl = "https://procomic.pro"
        lang = "ar"
    }

    deeplink {
        host("procomic.pro")
        host("procomic.net")
        path("/..*-..*")
    }
}

dependencies {
    compileOnlyApi("ca.mpreg:imagedecoder:16")
    compileOnlyApi("com.github.tachiyomiorg:image-decoder:e08e9be535")
}
