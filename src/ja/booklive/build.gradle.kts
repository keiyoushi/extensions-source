import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BookLive"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "ja"
        baseUrl = "https://booklive.jp"
    }

    deeplink {
        host("booklive.jp")
        host("sp.handycomic.jp")
        path("/product/..*")
        path("/bviewer/.*")
    }
}

dependencies {
    implementation(project(":lib:speedbinb"))
}
