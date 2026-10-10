import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lura Toon"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "pt-BR"
        baseUrl = "https://luratoons.net"
        versionId = 2
    }

    deeplink {
        path("/..*")
    }
}

dependencies {
    compileOnlyApi("ca.mpreg:imagedecoder:16")
    compileOnlyApi("com.github.tachiyomiorg:image-decoder:e08e9be535")
}
