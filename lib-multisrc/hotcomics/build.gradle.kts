import keiyoushi.gradle.extensions.baseVersionCode

plugins {
    alias(kei.plugins.multisrc)
}

baseVersionCode = 2

dependencies {
    api(project(":lib:cookieinterceptor"))
}
