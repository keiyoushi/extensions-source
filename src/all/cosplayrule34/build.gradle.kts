import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "CosplayRule34"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        baseUrl = "https://cosplayrule34.com"
        lang = "all"
    }

    deeplink {
        host("cosplayrule34.com")
        host("www.cosplayrule34.com")
        path("/post/..*")
        path("/model/..*")
        path("/cosplay/..*")
        path("/fandom/..*")
        path("/category/..*")
        path("/..*/post/..*")
        path("/..*/model/..*")
        path("/..*/cosplay/..*")
        path("/..*/fandom/..*")
        path("/..*/category/..*")
    }
}
