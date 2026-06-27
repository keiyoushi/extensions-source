plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Cubari"
    className = "CubariFactory"
    versionCode = 26
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("cubari.moe")
        host("*.cubari.moe")
        path("/read/..*")
        path("/proxy/..*")
    }

    deeplink {
        host("imgur.com")
        host("*.imgur.com")
        path("/a/..*")
        path("/gallery/..*")
    }

    deeplink {
        host("reddit.com")
        host("*.reddit.com")
        path("/gallery/..*")
    }

    deeplink {
        host("imgchest.com")
        host("*.imgchest.com")
        path("/p/..*")
    }

    deeplink {
        host("catbox.moe")
        host("*.catbox.moe")
        path("/c/..*")
    }
}
