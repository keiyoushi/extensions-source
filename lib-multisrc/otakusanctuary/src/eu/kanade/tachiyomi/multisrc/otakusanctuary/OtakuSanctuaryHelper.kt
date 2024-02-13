package eu.kanade.tachiyomi.multisrc.otakusanctuary

import okhttp3.HttpUrl.Companion.toHttpUrl

class OtakuSanctuaryHelper(private val lang: String) {

    fun otakusanLang() = when (lang) {
        "vi" -> "vn"
        "en" -> "us"
        else -> lang
    }

    fun processUrl(url: String, vi: String = ""): String {
        var url = url.replace("_h_", "http")
            .replace("_e_", "/extendContent/Manga")
            .replace("_r_", "/extendContent/MangaRaw")

        if (url.startsWith("//")) {
            url = "https:$url"
        }
        if (url.contains("drive.google.com")) {
            return url
        }

        url = when (url.slice(0..4)) {
            "[GDP]" -> url.replace("[GDP]", "https://drive.google.com/uc?export=view&id=")
            "[GDT]" -> if (otakusanLang() == "us") {
                url.replace("image2.otakuscan.net", "image3.shopotaku.net")
                    .replace("image2.otakusan.net", "image3.shopotaku.net")
            } else {
                url
            }
            "[IS1]" -> {
                val url = url.replace("[IS1]", "https://imagepi.otakuscan.net/")
                if (url.contains("vi") && url.contains("otakusan.net_")) {
                    url
                } else {
                    url.toHttpUrl().newBuilder().apply {
                        addQueryParameter("vi", vi)
                    }.build().toString()
                }
            }
            "[IS3]" -> url.replace("[IS3]", "https://image3.otakusan.net/")
            "[IO3]" -> url.replace("[IO3]", "http://image3.shopotaku.net/")
            else -> url
        }

        if (url.contains("/Content/Workshop") || url.contains("otakusan") || url.contains("myrockmanga")) {
            return url
        }

        if (url.contains("file-bato-orig.anyacg.co")) {
            url = url.replace("file-bato-orig.anyacg.co", "file-bato-orig.bato.to")
        }

        if (url.contains("file-comic")) {
            if (url.contains("file-comic-1")) {
                url = url.replace("file-comic-1.anyacg.co", "z-img-01.mangapark.net")
            }
            if (url.contains("file-comic-2")) {
                url = url.replace("file-comic-2.anyacg.co", "z-img-02.mangapark.net")
            }
            if (url.contains("file-comic-3")) {
                url = url.replace("file-comic-3.anyacg.co", "z-img-03.mangapark.net")
            }
            if (url.contains("file-comic-4")) {
                url = url.replace("file-comic-4.anyacg.co", "z-img-04.mangapark.net")
            }
            if (url.contains("file-comic-5")) {
                url = url.replace("file-comic-5.anyacg.co", "z-img-05.mangapark.net")
            }
            if (url.contains("file-comic-6")) {
                url = url.replace("file-comic-6.anyacg.co", "z-img-06.mangapark.net")
            }
            if (url.contains("file-comic-9")) {
                url = url.replace("file-comic-9.anyacg.co", "z-img-09.mangapark.net")
            }
            if (url.contains("file-comic-10")) {
                url = url.replace("file-comic-10.anyacg.co", "z-img-10.mangapark.net")
            }
            if (url.contains("file-comic-99")) {
                url = url.replace("file-comic-99.anyacg.co/uploads", "file-bato-0001.bato.to")
            }
        }

        if (url.contains("cdn.nettruyen.com")) {
            url = url.replace(
                "cdn.nettruyen.com/Data/Images/",
                "truyen.cloud/data/images/",
            )
        }
        if (url.contains("url=")) {
            url = url.substringAfter("url=")
        }
        if (url.contains("blogspot") || url.contains("fshare")) {
            url = url.replace("http:", "https:")
        }
        if (url.contains("blogspot") && !url.contains("http")) {
            url = "https://$url"
        }
        if (url.contains("app/manga/uploads/") && !url.contains("http")) {
            url = "https://lhscan.net$url"
        }
        url = url.replace("//cdn.adtrue.com/rtb/async.js", "")

        if (url.contains(".webp")) {
            url = "https://otakusan.net/api/Value/ImageSyncing?ip=34512351".toHttpUrl().newBuilder()
                .apply {
                    addQueryParameter("url", url)
                }.build().toString()
        } else if (
            (
                url.contains("merakiscans") ||
                    url.contains("mangazuki") ||
                    url.contains("ninjascans") ||
                    url.contains("anyacg.co") ||
                    url.contains("mangakatana") ||
                    url.contains("zeroscans") ||
                    url.contains("mangapark") ||
                    url.contains("mangadex") ||
                    url.contains("uptruyen") ||
                    url.contains("hocvientruyentranh") ||
                    url.contains("ntruyen.info") ||
                    url.contains("chancanvas") ||
                    url.contains("bato.to")
                ) &&
            (
                !url.contains("googleusercontent") &&
                    !url.contains("otakusan") &&
                    !url.contains("otakuscan") &&
                    !url.contains("shopotaku")
                )
        ) {
            url =
                "https://images2-focus-opensocial.googleusercontent.com/gadgets/proxy?container=focus&gadget=a&no_expand=1&resize_h=0&rewriteMime=image%2F*".toHttpUrl()
                    .newBuilder().apply {
                        addQueryParameter("url", url)
                    }.build().toString()
        } else if (url.contains("imageinstant.com")) {
            url = "https://images.weserv.nl/".toHttpUrl().newBuilder().apply {
                addQueryParameter("url", url)
            }.build().toString()
        } else if (!url.contains("otakusan.net")) {
            url = "https://otakusan.net/api/Value/ImageSyncing?ip=34512351".toHttpUrl().newBuilder()
                .apply {
                    addQueryParameter("url", url)
                }.build().toString()
        }

        return if (url.contains("vi=") && !url.contains("otakusan.net_")) {
            url
        } else {
            url.toHttpUrl().newBuilder().apply {
                addQueryParameter("vi", vi)
            }.build().toString()
        }
    }
}
