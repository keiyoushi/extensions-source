import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SilentQuill"
    pkgName = "en.armageddon"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
<<<<<<< HEAD:src/en/silentquill/build.gradle.kts
=======
    theme = "mangathemesia"
>>>>>>> bdf338cb2 (1.6):src/en/armageddon/build.gradle.kts

    source {
        lang = "en"
        baseUrl = "https://silentquill.net"
    }
}
