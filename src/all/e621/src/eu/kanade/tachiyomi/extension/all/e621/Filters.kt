package eu.kanade.tachiyomi.extension.all.e621

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

internal val tagFilter = hashSetOf(
    "accipitrid", "accipitriform", "ailurid", "alien_humanoid", "alligatorid",
    "ambiguous_species", "amphibian", "anatid", "animal_humanoid", "animate_inanimate",
    "anseriform", "aquatic", "aquatic_humanoid", "arachnid", "arthropod", "asinus",
    "avian", "boss_monster_(undertale)", "bovid", "bovid_humanoid", "bovine", "canid",
    "canid_demon", "canid_humanoid", "canine", "canine_humanoid", "canis", "caprine",
    "cattle", "cat_humanoid", "cephalopod", "cephalopod_humanoid", "cervine", "cetacean",
    "corvid", "crocodilian", "demon_humanoid", "domestic_ferret", "draconcopode",
    "dromaeosaurid", "earth_pony", "eeveelution", "elemental_creature",
    "elemental_humanoid", "equid", "equine", "eulipotyphlan", "felid", "feline",
    "feline_humanoid", "felis", "flora_fauna", "fox_humanoid", "galliform",
    "generation_1_pokemon", "generation_2_pokemon", "generation_3_pokemon",
    "generation_4_pokemon", "generation_5_pokemon", "generation_6_pokemon",
    "generation_7_pokemon", "generation_8_pokemon", "generation_9_pokemon", "giraffid",
    "haplorhine", "horned_humanoid", "humanoid", "hunting_dog", "hymenopteran",
    "lagomorph", "lagomorph_humanoid", "legendary_pokemon", "lepidopteran", "leporid",
    "leporid_humanoid", "macropod", "mammal", "mammal_humanoid", "mammal_taur",
    "marsupial", "mega_evolution", "mollusk", "mollusk_humanoid", "monotreme", "murid",
    "murine", "mustelid", "musteline", "mythological_avian", "mythological_canine",
    "mythological_creature", "mythological_equine", "mythological_scalie",
    "ornithischian", "oryctolagus", "oscine", "pantherine", "passerine", "phasianid",
    "pinscher", "prehistoric_species", "primate", "procyonid", "rabbit_humanoid",
    "regional_form_(pokemon)", "reptile", "retriever", "robot_humanoid", "rodent",
    "saurischian", "scalie", "sciurid", "shiba_inu", "shiny_pokemon", "spitz", "suid",
    "suine", "tailed_humanoid", "taur", "true_musteline", "werecanine", "werecreature",
    "yokai",
    "3_toes", "4_fingers", "4_toes", "5_fingers", "accessory", "animal_penis", "areola",
    "armwear", "barefoot", "beak", "bed", "belly", "biceps", "black_body",
    "black_clothing", "black_eyes", "black_fur", "black_hair", "black_nose",
    "blonde_hair", "blue_body", "blue_eyes", "blue_fur", "blue_hair", "blush",
    "blush_lines", "bodily_fluids", "border", "bottomwear", "brown_body", "brown_eyes",
    "brown_fur", "brown_hair", "butt", "canine_genitalia", "canine_penis", "clitoris",
    "clothing", "collar", "container", "countershading", "cutie_mark",
    "detailed_background", "dipstick_tail", "ear_piercing", "ear_ring", "electronics",
    "equine_penis", "eyebrows", "eyelashes", "eyes_closed", "facial_hair",
    "facial_piercing", "feathered_wings", "fingers", "finger_claws", "footwear",
    "front_view", "furniture", "genitals", "genital_fluids", "gesture", "glans", "gloves",
    "grass", "green_body", "green_eyes", "grey_background", "grey_body", "grey_fur",
    "grin", "hair", "hair_accessory", "half-closed_eyes", "handwear", "happy", "hat",
    "headwear", "heart_symbol", "holding_object", "holidays", "hooves", "horn",
    "humanoid_hands", "humanoid_penis", "inside", "jewelry", "kneeling", "legwear",
    "long_hair", "looking_at_another", "looking_back", "lying", "machine",
    "membrane_(anatomy)", "membranous_wings", "multicolored_hair", "muscular_anthro",
    "narrowed_eyes", "navel", "necklace", "nude_anthro", "one_eye_closed", "on_bed",
    "orange_body", "orange_fur", "pants", "pawpads", "paws", "pecs", "penile",
    "penile_penetration", "pillow", "pink_body", "pink_hair", "pink_nose", "plant",
    "pointy_ears", "pupils", "purple_body", "purple_eyes", "purple_hair", "rear_view",
    "red_body", "red_eyes", "red_hair", "scales", "sheath", "shirt", "shoes", "shorts",
    "short_hair", "simple_background", "sitting", "skirt", "sky", "smile", "soles",
    "sound_effects", "speech_bubble", "spikes", "spots", "standing", "stripes", "tail",
    "tan_fur", "teeth", "text", "toe_claws", "topwear", "translucent", "tree",
    "two_tone_body", "two_tone_fur", "vaginal", "vaginal_fluids", "water", "whiskers",
    "white_background", "white_body", "white_clothing", "white_fur", "white_hair",
    "yellow_body", "yellow_eyes", "yellow_fur",
    "conditional_dnp", "sound_warning",
    "underwear", "sniffing", "animal_genitalia", "erection", "tongue", "page_number",
    "countershade_torso", "motion_lines", "tunic", "panel_skew", "interior_background",
    "headgear", "blue_sky", "5_toes", "4_toes", "3_toes", "onomatopoeia", "color_coded",
    "color_coded_speech_bubble", "patreon", "polygonal_speech_bubble", "blockage_(layout)",
)

fun getE621FilterList(categoryPref: String): FilterList = FilterList(
    Filter.Header("Note: You will need to be logged into E621 via WebView to see certain posts (e.g., 'No Image')"),
    ModeFilter(),
    Filter.Separator(),
    PoolGroupFilter("Pool Search Options", categoryPref),
    Filter.Separator(),
    TagGroupFilter("Tag Search Options"),
)

class PoolGroupFilter(displayName: String, categoryPref: String) :
    Filter.Group<Filter<*>>(
        displayName,
        listOf(
            Header("(Pools Search Mode only)"),
            DescriptionFilter(),
            OrderFilter(),
            CategoryFilter(getDefaultCategoryIndex(categoryPref)),
            ActiveOnlyFilter(),
        ),
    ) {
    fun getDescription() = (state[1] as DescriptionFilter).state.trim()
    fun getOrder() = (state[2] as OrderFilter).toUriPart()
    fun getCategory() = (state[3] as CategoryFilter).toUriPart()
    fun getActiveOnly() = (state[4] as ActiveOnlyFilter).state
}

class TagGroupFilter(displayName: String) :
    Filter.Group<Filter<*>>(
        displayName,
        listOf(
            Header("(Tags Search Mode only)"),
            OrderTagFilter(),
            DateFilter(),
            TagsFilter(),
            Header("e.g.,  `anthro  -mammal  order:random  date:month  score:>100`"),
            Header("Negative tags may not filter everything"),
            FirstPageFilter(),
            EndPageFilter(),
            Header("Warning: will filter out pools that don't use the `first_page` or `end_page` tags."),
        ),
    ) {
    fun getOrderTag() = (state[1] as OrderTagFilter).toUriPart()
    fun getDate() = (state[2] as DateFilter).toUriPart()
    fun getTags() = (state[3] as TagsFilter).state.trim()
    fun getFirstPage() = (state[6] as FirstPageFilter).state
    fun getEndPage() = (state[7] as EndPageFilter).state
}

fun getDefaultModeIndex(modePref: String): Int = when (modePref) {
    "pools" -> 0
    "tags" -> 1
    else -> 0
}

fun getDefaultCategoryIndex(categoryPref: String): Int = when (categoryPref) {
    "series" -> 1
    "collection" -> 2
    else -> 0
}

fun getDefaultOrderIndex(orderPref: String): Int = when (orderPref) {
    "updated_at" -> 0
    "post_count" -> 1
    "name" -> 2
    "created_at" -> 3
    else -> 0
}

class ModeFilter(defaultIndex: Int = 0) :
    UriPartFilter(
        "Search Mode",
        arrayOf(
            Pair("Pools", "pools.json"),
            Pair("Tags", "posts.json"),
        ),
        defaultIndex,
    )

class OrderFilter(defaultIndex: Int = 0) :
    UriPartFilter(
        "Order by",
        arrayOf(
            Pair("Recently Updated", "updated_at"),
            Pair("Most Posts", "post_count"),
            Pair("Name (A-Z)", "name"),
            Pair("Newest First", "created_at"),
        ),
        defaultIndex,
    )

class OrderTagFilter(defaultIndex: Int = 0) :
    UriPartFilter(
        "Order by",
        arrayOf(
            Pair("Default", ""),
            Pair("Newest", "id_desc"),
            Pair("Oldest", "id"),
            Pair("Score", "score"),
            Pair("Hot", "hot"),
            Pair("Favorites", "favcount"),
            Pair("Random", "random"),
        ),
        defaultIndex,
    )

class DateFilter(defaultIndex: Int = 0) :
    UriPartFilter(
        "Filter Date by",
        arrayOf(
            Pair("All Time", ""),
            Pair("Day", "day"),
            Pair("Week", "week"),
            Pair("Month", "month"),
            Pair("Year", "year"),
        ),
        defaultIndex,
    )

class CategoryFilter(defaultIndex: Int = 0) :
    UriPartFilter(
        "Category",
        arrayOf(
            Pair("Any", ""),
            Pair("Series", "series"),
            Pair("Collection", "collection"),
        ),
        defaultIndex,
    )

class TagsFilter(defaultTags: String = "") : Filter.Text("Space Separated Tags", defaultTags)

class ActiveOnlyFilter : Filter.CheckBox("Active pools only", false)

class FirstPageFilter : Filter.CheckBox("Search tags by first pages", false)

class EndPageFilter : Filter.CheckBox("Search tags by end pages", false)

class DescriptionFilter : Filter.Text("Description contains")

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>, defaultIndex: Int = 0) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultIndex) {
    fun toUriPart() = vals[state].second
}
