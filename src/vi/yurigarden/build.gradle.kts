import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "YuriGarden"
    versionCode = 12
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl = "https://yurigarden.moe"
    }

    deeplink {
        path("/comic/..*")
    }
}

dependencies {
    implementation(project(":lib:cryptoaes"))
}
