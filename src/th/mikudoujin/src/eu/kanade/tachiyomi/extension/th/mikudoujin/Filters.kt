package eu.kanade.tachiyomi.extension.th.mikudoujin

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class GenreFilter :
    UriPartFilter(
        "ประเภท",
        arrayOf(
            Pair("ทั้งหมด", ""),
            Pair("โดจิน แปลไทย", "category/โดจิน แปลไทย"),
            Pair("Ahegao", "genre/Ahegao"),
            Pair("NTR", "genre/NTR"),
            Pair("SM", "genre/SM"),
            Pair("Uncensored", "genre/Uncensored"),
            Pair("Yaoi", "genre/Yaoi"),
            Pair("Yuri", "genre/Yuri"),
            Pair("ครอบครัว", "genre/ครอบครัว"),
            Pair("คอมดี้", "genre/คอมดี้"),
            Pair("ซิสเตอร์", "genre/ซิสเตอร์"),
            Pair("ดราม่า", "genre/ดราม่า"),
            Pair("ตำรวจ", "genre/ตำรวจ"),
            Pair("นมปานกลาง", "genre/นมปานกลาง"),
            Pair("นมเล็ก", "genre/นมเล็ก"),
            Pair("นมใหญ่", "genre/นมใหญ่"),
            Pair("นักเรียน", "genre/นักเรียน"),
            Pair("บังคับ", "genre/บังคับ"),
            Pair("ปีศาจ นางฟ้า แวมไพร์", "genre/ปีศาจ นางฟ้า แวมไพร์"),
            Pair("ผี-ซอมบี้", "genre/ผี-ซอมบี้"),
            Pair("พยาบาล", "genre/พยาบาล"),
            Pair("พี่สาว น้องสาว", "genre/พี่สาว น้องสาว"),
            Pair("ฟุตะนาริ", "genre/ฟุตะนาริ"),
            Pair("ภาพสี", "genre/ภาพสี"),
            Pair("มิโกะ", "genre/มิโกะ"),
            Pair("ลักหลับ", "genre/ลักหลับ"),
            Pair("สลับร่าง ชาย หญิง", "genre/สลับร่าง ชาย หญิง"),
            Pair("สะกดจิต", "genre/สะกดจิต"),
            Pair("สาวกีฬา", "genre/สาวกีฬา"),
            Pair("สาวดุ้น", "genre/สาวดุ้น"),
            Pair("สาวผิวแทน", "genre/สาวผิวแทน"),
            Pair("สาวมอนสเตอร์", "genre/สาวมอนสเตอร์"),
            Pair("สาวหูสัตว์", "genre/สาวหูสัตว์"),
            Pair("สาวออฟฟิศ", "genre/สาวออฟฟิศ"),
            Pair("สาวเกล", "genre/สาวเกล"),
            Pair("สาวแว่น", "genre/สาวแว่น"),
            Pair("สาวใหญ่/แม่บ้าน", "genre/สาวใหญ่/แม่บ้าน"),
            Pair("หนวด-สัตว์", "genre/หนวด-สัตว์"),
            Pair("หยุดเวลา", "genre/หยุดเวลา"),
            Pair("อาจารย์", "genre/อาจารย์"),
            Pair("ฮาร์ดคอร์", "genre/ฮาร์ดคอร์"),
            Pair("ฮาเร็ม", "genre/ฮาเร็ม"),
            Pair("เมด สาวใช้ สาวคาเฟ่", "genre/เมด สาวใช้ สาวคาเฟ่"),
            Pair("เอลฟ์", "genre/เอลฟ์"),
            Pair("แฟนตาซี", "genre/แฟนตาซี"),
            Pair("โซ", "genre/โซ"),
            Pair("โดนรุม", "genre/โดนรุม"),
            Pair("โรแมนติก", "genre/โรแมนติก"),
            Pair("โล", "genre/โล"),
            Pair("ไอดอล", "genre/ไอดอล"),
        ),
    )
