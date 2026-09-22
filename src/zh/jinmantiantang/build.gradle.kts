import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Jinman Tiantang"
    // [FIX-AI] 58 -> 61: 合并 PR#19104(账号登录) + 镜像自动更新网址 + GIF 修复后升版本号，保证可覆盖安装
    versionCode = 61
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "禁漫天堂"
        lang = "zh"
        // [FIX-AI] 原版: baseUrl { custom("https://18comic.vip") } —— 官方 custom 模式只支持手动输入网址。
        // 修复后: 改回 static 模式，让源类自己 override val baseUrl（框架允许 static 模式下类内接管 baseUrl），
        // 在类内实现"手动输入优先、留空回退旧版镜像列表+自动更新"的双轨逻辑（见 Jinmantiantang.kt / Preferences.kt）。
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
