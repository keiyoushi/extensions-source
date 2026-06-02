package eu.kanade.tachiyomi.extension.ja.readerstore

const val DOMAIN = "ebookstore.sony.jp"
const val BASE_URL = "https://$DOMAIN"
const val API_URL = "$BASE_URL/front-api"
const val VIEWER_URL = "https://viewer.$DOMAIN/viewer"

const val PATH_IMAGE_URL = "image_url"

const val PARAM_INDICES = "indices"
const val PARAM_CODE = "code"
const val PARAM_ACCEPT = "accept"
const val QUALITY_HIGH = "high"
const val ACCEPT_FORMATS = "webp,jpeg,png"

const val HEADER_NMR = "X-Nmr"
const val HEADER_TOKEN = "X-Token"
const val HEADER_UUID = "X-Uuid"
const val HEADER_USE_CACHE = "X-Use-Cache"
