import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Jinman Tiantang"
    versionCode = 57
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "禁漫天堂"
        lang = "zh"
        baseUrl = "https://18comic.vip"
    }

    deeplink {
        host("18comic.vip")
        host("18comic.ink")
        host("jmcomic-zzz.one")
        host("jmcomic-zzz.org")
        path("/album/..*")
    }
}

dependencies {

    implementation(project(":lib:randomua"))
}
