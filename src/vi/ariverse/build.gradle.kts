import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ariverse"
    versionCode = 54
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://arigl.xyz")
        }
        id = 4480433466073326866
    }

    deeplink {
        path("/comic/story/..*")
    }
}
