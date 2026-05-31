package eu.kanade.tachiyomi.extension.ar.ariatoon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    Filter.Header("ملاحظة: لا يمكن استخدام البحث النصي مع الفلاتر"),
    Filter.Separator(),
    GenreFilter(),
)

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class GenreFilter :
    UriPartFilter(
        "التصنيفات",
        arrayOf(
            Pair("الكل", ""),
            Pair("تجسيد", "a8dbb6c9-93bb-46f3-a4ea-d8ffcf126096"),
            Pair("اعادة إحياء", "c4a7ed08-d9a3-4e95-8b58-e6fc67e56004"),
            Pair("فن معجبين", "87fda931-9ffe-496a-8ce2-df163524834f"),
            Pair("رياضي", "1e693af2-33da-4a9d-abc2-b9f5b7ce1e9c"),
            Pair("قصة قصيرة", "7b2b3b97-4ec8-49d0-b0db-ac5518cb43d3"),
            Pair("نفسي", "2ba91d6e-a106-440c-be5b-0f2e46d5fffa"),
            Pair("مغامرة", "16faa1ee-a809-4acc-97a7-ae6f8cbe6137"),
            Pair("مصاص دماء", "28d84eba-de60-4748-ae04-42e9b5776d46"),
            Pair("قوة خارقة", "b4676795-e440-4d3a-ac51-475303a9d2b5"),
            Pair("فنون الدفاع عن النفس", "16481cd5-3ad7-4dda-9737-50c8f607b6a7"),
            Pair("عنف", "8bdf8691-6873-4a26-8966-f69b6c218ba2"),
            Pair("طبي", "28820879-ee69-40fe-a338-7b604033aa47"),
            Pair("شوجو", "d5db894b-c01b-42ae-913d-2ad1197a95e8"),
            Pair("شرطة", "fd998a81-fbba-4a3f-8332-473eee861972"),
            Pair("سينين", "5aec5131-f069-43ab-8714-a65532a8f757"),
            Pair("سحر", "abd7edd1-a6a4-4285-b253-92e885ae7255"),
            Pair("زمكاني", "994afa00-00eb-49cd-a77e-851243134f19"),
            Pair("دموى", "b6b2f032-d9d0-4366-9b71-dce24466156b"),
            Pair("خيال", "c7a8a731-1ec3-4241-a4fa-6067389ac195"),
            Pair("حياة مدرسية", "59042779-6c1a-454d-8595-c9ae9f9356ac"),
            Pair("تاريخي", "485a4351-f66c-481b-9f25-dcd038723bff"),
            Pair("إنتقام", "b430ba29-00b2-40f1-a314-f0ad9042b9a0"),
            Pair("ألعاب", "2e810cf2-cb4d-4bf6-aa40-25df91712043"),
            Pair("شريحة من الحياة", "28d98105-419a-4f33-89ca-0f040b890caf"),
            Pair("غموض", "ad930486-f830-4953-8be0-d3a070955922"),
            Pair("خيال علمي", "30f8cbc6-788d-467d-9dc8-86d3c774c00e"),
            Pair("كوميديا", "819cb8f1-31d3-4ec9-a653-a06d0717df66"),
            Pair("رعب", "4db48a10-acf5-441e-a9c2-b7a59dfe0d4b"),
            Pair("إثارة", "ac9b5657-0ea2-46c3-a99e-a987fe356395"),
            Pair("أكشن", "38c19662-9798-4c60-8f24-5c8d5331fa57"),
            Pair("فانتازيا", "61dd5a30-258c-45b0-bca5-0e78786dfa2b"),
            Pair("دراما", "1ee85204-43fb-460c-8709-9399b4b81d61"),
            Pair("رومانسية", "d47a8924-f0fe-4ef4-b1a7-cd893df59f84"),
            Pair("شونين", "4d8b9e77-890c-46c3-96c0-861bf38e98be"),
        ),
    )
