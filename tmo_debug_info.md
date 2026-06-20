# TMOHentai Extension Analysis Report

This report details the URL parameters, HTML selectors, and API endpoints for the new TMOHentai site (`https://tmohentai.app/`).

---

## 1. Search Query Parameters (`/biblioteca`)

* **Base URL:** `https://tmohentai.app/biblioteca`
* **Search Query:** `title` (e.g., `?title=Wonderful+Life`)
* **Content Category:** `content`
  * *Supported Values:*
    * `yaoi` (Yaoi ♂♂)
    * `yuri` (Yuri ♀♀)
    * `futanari` (Futanari ⚧)
    * `sole-female` (Solo Femenino ♀)
    * `sole-male` (Solo Masculino ♂)
    * `vanilla` (Vanilla ♡)
    * `ntr` (NTR / Netorare)
    * `uncensored` (Uncensored)
* **Sorting Field:** `order_item`
  * *Supported Values:*
    * `likes_count` (Más populares - Default)
    * `score` (Mejor valorados)
    * `alphabetically` (Alfabético)
    * `creation` (Más recientes)
    * `release_date` (Fecha estreno)
* **Sorting Direction:** `order_dir`
  * *Supported Values:*
    * `desc` (↓ - Default)
    * `asc` (↑)
* **Tags (Genres) Filter:** `tags[]` (can be passed multiple times)
  * *Example:* `?tags[]=2108&tags[]=16`

### Full Tags Map (ID to Name)
Here is the extracted list of tag IDs to names for the extension filters:
```kotlin
val tags = mapOf(
    "2108" to "2b",
    "68" to "3d",
    "6" to "adultery",
    "1633" to "afro",
    "424" to "age progression",
    "508" to "agnes tachyon",
    "7" to "ahegao",
    "1422" to "alice margatroid",
    "1003" to "amputee",
    "16" to "anal",
    "159" to "anal intercourse",
    "498" to "android 18",
    "1216" to "android 21",
    "372" to "apron",
    "258" to "asphyxiation",
    "252" to "aunt",
    "161" to "bald",
    "415" to "ball sucking",
    "162" to "bbm",
    "23" to "bbw",
    "421" to "bdsm",
    "123" to "beauty mark",
    "51" to "bestiality",
    "109" to "big areolae",
    "27" to "big ass",
    "471" to "big balls",
    "28" to "big boobs",
    "75" to "big breast",
    "5" to "big breasts",
    "510" to "big clit",
    "708" to "big lips",
    "676" to "big muscles",
    "292" to "big nipples",
    "156" to "big penis",
    "762" to "big vagina",
    "110" to "bikini",
    "72" to "bisexual",
    "253" to "blackmail",
    "245" to "blood",
    "189" to "bloomers",
    "8" to "blowjob",
    "201" to "blowjob face",
    "591" to "body modification",
    "395" to "bodysuit",
    "60" to "bondage",
    "118" to "breast feeding",
    "163" to "bride",
    "474" to "brother",
    "52" to "bukkake",
    "485" to "bulma briefs",
    "677" to "bunny boy",
    "259" to "caelus",
    "445" to "catboy",
    "84" to "catgirl",
    "9" to "cheating",
    "318" to "cheerleader",
    "613" to "chi chi",
    "190" to "chikan",
    "333" to "chinese dress",
    "2054" to "chloe",
    "522" to "chloroform",
    "254" to "clit stimulation",
    "523" to "clothed female nude male",
    "689" to "clothed paizuri",
    "165" to "collar",
    "31" to "colour",
    "71" to "comedy",
    "148" to "comic",
    "119" to "condom",
    "800" to "corset",
    "284" to "cosplaying",
    "423" to "cousin",
    "234" to "cowgirl",
    "38" to "creampie",
    "215" to "crossdressing",
    "379" to "crotch tattoo",
    "1035" to "cum bath",
    "136" to "cunnilingus",
    "943" to "curren chan",
    "992" to "daiwa scarlet",
    "29" to "dark skin",
    "1562" to "darkness",
    "166" to "daughter",
    "42" to "deepthroat",
    "101" to "defloration",
    "346" to "demon",
    "277" to "demon girl",
    "285" to "dickgirl on dickgirl",
    "209" to "dickgirl on female",
    "265" to "dickgirl on male",
    "85" to "digital",
    "124" to "dilf",
    "1161" to "doctor",
    "151" to "dog",
    "679" to "dog boy",
    "30" to "domination",
    "78" to "domination loss",
    "1032" to "double anal",
    "600" to "double blowjob",
    "49" to "double penetration",
    "289" to "drill hair",
    "183" to "drugs",
    "184" to "drunk",
    "220" to "elf",
    "1160" to "ellen joe",
    "175" to "emotionless sex",
    "34" to "exhibitionism",
    "359" to "exposed clothing",
    "167" to "eye-covering bang",
    "221" to "facial hair",
    "50" to "fantasy",
    "142" to "farting",
    "137" to "females only",
    "59" to "femdom",
    "204" to "feminization",
    "61" to "fetish",
    "138" to "fff threesome",
    "46" to "ffm threesome",
    "65" to "filming",
    "139" to "fingering",
    "297" to "firefly",
    "356" to "first person perspective",
    "104" to "focus anal",
    "357" to "focus blowjob",
    "86" to "foot licking",
    "76" to "footjob",
    "39" to "forced",
    "477" to "fox girl",
    "177" to "full censorship",
    "132" to "full color",
    "210" to "full-packaged futanari",
    "32" to "furry",
    "19" to "futa",
    "44" to "futanari",
    "683" to "gag",
    "364" to "gang rape",
    "509" to "garter belt",
    "129" to "gender change",
    "130" to "gender morph",
    "295" to "ghost",
    "111" to "glasses",
    "106" to "gloves",
    "398" to "goblin",
    "654" to "grandmother",
    "26" to "group",
    "571" to "growth",
    "45" to "gyaru",
    "131" to "gyaru-oh",
    "120" to "hairy",
    "168" to "hairy armpits",
    "321" to "handjob",
    "13" to "harem",
    "1300" to "headphones",
    "1306" to "hero",
    "2672" to "hestia",
    "152" to "hidden sex",
    "729" to "hinata hyuga",
    "389" to "hood",
    "240" to "horns",
    "62" to "horror",
    "504" to "horse",
    "316" to "horse girl",
    "480" to "hotpants",
    "133" to "huge breasts",
    "426" to "huge penis",
    "430" to "human cattle",
    "197" to "human on furry",
    "36" to "humiliation",
    "1649" to "hypno",
    "2745" to "ia",
    "1481" to "idoll sun",
    "3" to "impregnation",
    "12" to "incest",
    "222" to "inflation",
    "290" to "inverted nipples",
    "2701" to "kanojo",
    "88" to "kemonomimi",
    "616" to "kimono",
    "43" to "kissing",
    "937" to "kitasan black",
    "1787" to "knotted penis",
    "489" to "kunoichi",
    "263" to "lactation",
    "112" to "large tattoo",
    "235" to "leash",
    "121" to "leg lock",
    "89" to "lingerie",
    "57" to "loli",
    "231" to "long tongue",
    "1669" to "low smegma",
    "377" to "magical girl",
    "301" to "maid",
    "376" to "male on dickgirl",
    "353" to "males only",
    "446" to "masked face",
    "125" to "masturbation",
    "10" to "mature",
    "371" to "mesuiki",
    "487" to "midget",
    "2" to "milf",
    "363" to "military",
    "431" to "milking",
    "20" to "mind break",
    "98" to "mind control",
    "47" to "mmf threesome",
    "452" to "monkey",
    "560" to "monster",
    "232" to "monster girl",
    "35" to "monsters",
    "549" to "moral degeneration",
    "126" to "mosaic censorship",
    "54" to "mother",
    "256" to "mouth mask",
    "90" to "multi-work series",
    "247" to "multiple orgasms",
    "416" to "multiple paizuri",
    "113" to "muscle",
    "91" to "nakadashi",
    "730" to "naruto uzumaki",
    "4" to "netorare",
    "73" to "netorase",
    "481" to "niece",
    "589" to "nipple piercing",
    "255" to "nipple stimulation",
    "143" to "no penetration",
    "1" to "ntr",
    "320" to "nun",
    "344" to "nurse",
    "40" to "nympho",
    "223" to "old man",
    "466" to "onahole",
    "241" to "oni",
    "517" to "onsen",
    "354" to "oppai loli",
    "48" to "orgy",
    "53" to "oyakodon",
    "266" to "painted nails",
    "157" to "paizuri",
    "122" to "pantyhose",
    "66" to "parody",
    "417" to "pasties",
    "673" to "pegging",
    "690" to "penis enlargement",
    "303" to "phimosis",
    "512" to "piercing",
    "339" to "pig",
    "693" to "piss drinking",
    "700" to "policewoman",
    "171" to "ponytail",
    "224" to "possession",
    "33" to "pregnant",
    "200" to "prostitution",
    "205" to "public use",
    "328" to "ranma saotome",
    "21" to "rape",
    "1131" to "rias gremory",
    "1491" to "rider",
    "193" to "rimjob",
    "64" to "romance",
    "107" to "rough translation",
    "302" to "ryona",
    "559" to "scar",
    "667" to "scat",
    "191" to "school gym uniform",
    "178" to "school swimsuit",
    "182" to "schoolboy uniform",
    "22" to "schoolgirl",
    "140" to "schoolgirl uniform",
    "268" to "sensei",
    "114" to "sex toys",
    "447" to "shared senses",
    "1159" to "shark girl",
    "448" to "sheep girl",
    "267" to "shemale",
    "553" to "shibari",
    "988" to "shirou emiya",
    "37" to "shota",
    "150" to "sister",
    "172" to "slave",
    "92" to "sleeping",
    "399" to "slime",
    "67" to "small boobs",
    "93" to "small breasts",
    "206" to "small penis",
    "227" to "smalldom",
    "482" to "smegma",
    "198" to "smell",
    "2049" to "snake",
    "211" to "sole dickgirl",
    "25" to "sole female",
    "24" to "sole male",
    "145" to "solo action",
    "648" to "son gohan",
    "337" to "son goku",
    "752" to "spanking",
    "1141" to "spider",
    "77" to "sport",
    "192" to "squirting",
    "115" to "stockings",
    "94" to "stomach deformation",
    "116" to "story arc",
    "207" to "strap-on",
    "58" to "student",
    "179" to "sweating",
    "117" to "swimsuit",
    "272" to "tail",
    "413" to "tail plug",
    "56" to "tall girl",
    "236" to "tall man",
    "276" to "tankoubon",
    "462" to "tanlines",
    "80" to "teacher",
    "14" to "tentacles",
    "202" to "thick eyebrows",
    "55" to "tomboy",
    "228" to "tomgirl",
    "41" to "toys",
    "280" to "transformation",
    "146" to "tribadism",
    "595" to "tsunade",
    "74" to "tsundere",
    "95" to "twintails",
    "63" to "uncensored",
    "2404" to "uncle",
    "1675" to "unusual insertions",
    "153" to "unusual pupils",
    "286" to "urination",
    "194" to "vaginal birth",
    "15" to "vanilla",
    "154" to "variant set",
    "96" to "very long hair",
    "69" to "virgin",
    "81" to "virginity",
    "804" to "vivlos",
    "366" to "vtuber",
    "400" to "warrior",
    "1404" to "western cg",
    "216" to "wise",
    "609" to "witch",
    "214" to "wolf boy",
    "199" to "wolf girl",
    "97" to "x-ray",
    "70" to "yandere",
    "18" to "yaoi",
    "17" to "yuri"
)
```

---

## 2. Manga Card Selectors (Search Results)

* **Card Element Selector:** `a.manga-card`
* **Title:** `a.manga-card` attribute `title` OR `a.manga-card h3` text content.
* **Thumbnail Image URL:** `a.manga-card img` attribute `src`.
* **Detail Page URL:** `a.manga-card` attribute `href`.

---

## 3. Manga Details Selectors

* **Title:** `#md-title` text content.
* **Cover Image URL:** `.md-cover-card__image-wrap img` attribute `src`.
* **Status:** `.md-cover-card__status` or `.md-cover-card__status .md-badge` text content (e.g. `Ongoing`, `Completed`).
* **Author:** `.md-badge--author` or `.label.md-badge--author` text content.
* **Description/Synopsis:** `.md-info-row--synopsis .md-info-row__value` (or inner `<p>` tag text content).
* **Genres (Tags):** `#md-tags-list a span` or `#md-tags-list a` text content.

---

## 4. Chapters / Uploads Selectors

On TMOHentai, manga items are single-chapter doujinshis/uploads.
* **Chapter Link Selector:** `.md-preview-read-btn` or `.md-preview-card__header a` attribute `href` (points to the reader page, e.g., `/view_uploads/{id}`).
* **Chapter Title:** Can default to the manga title or a static string (like "Leer" or "Capítulo 1").
* **Chapter Upload Date:** Found in `.md-info-row--date` (specifically the row containing the text `Creado:`).

---

## 5. Reader / Image Retrieval

Page images can be retrieved in two ways:

### Option A: Using the Preview API (Recommended for simplicity)
* **Endpoint:** `https://tmohentai.app/api/manga/{id}/preview-pages`
* **Query Parameters:** `offset` (defaults to `0`, pages are returned in batches of `12`).
* **JSON Response Structure:**
  ```json
  {
    "cap_reached": false,
    "has_more": true,
    "max_items": 60,
    "next_offset": 12,
    "offset": 0,
    "pages": [
      {
        "page_number": 1,
        "proxied_url": "https://storage.tmohentai.app/mangas/14464/1.webp"
      },
      ...
    ],
    "total": 52
  }
  ```
  Iterating with incrementing `offset` values until `has_more` is `false` retrieves all page images.

### Option B: Parsing the Reader HTML
* **Reader URL:** `https://tmohentai.app/view_uploads/{id}`
* **Image Selector:** `#reader-wrap .reader-img-wrap img`
* **Source Attributes:**
  * For the first few pages (typically 1-3): `src` attribute.
  * For the remaining pages (lazy loaded): `data-src` attribute.
  The extension should first try reading `data-src`, and if it is null/empty, fallback to `src`.
