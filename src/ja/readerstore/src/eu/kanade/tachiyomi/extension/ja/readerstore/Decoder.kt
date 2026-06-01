package eu.kanade.tachiyomi.extension.ja.readerstore

import java.nio.ByteBuffer
import java.nio.ByteOrder

class Decoder {
    private val schedule = IntArray(SCHEDULE_SIZE)

    fun decrypt(key: IntArray, iv: IntArray, data: ByteArray): ByteArray {
        val words = data.toLeIntArray()
        setKeys(key, iv)
        decryptOnMemory(words)
        return words.toLeByteArray(data.size)
    }

    private fun setKeys(key: IntArray, iv: IntArray) {
        var o = key[0]
        var f = key[1]
        var v = key[2]
        var q = key[3]
        val x = (q ushr 24) xor (q shl 8)
        var z = t0[x and 255] xor t1[(x ushr 8) and 255] xor t2[(x ushr 16) and 255] xor
            t3[(x ushr 24) and 255] xor o xor 16777216
        var c = z xor f
        var fAcc = c xor v
        var g = fAcc xor q
        val h = (g ushr 24) xor (g shl 8)
        var i = t0[h and 255] xor t1[(h ushr 8) and 255] xor t2[(h ushr 16) and 255] xor
            t3[(h ushr 24) and 255] xor z xor 33554432
        var j = i xor c
        var k = j xor fAcc
        var n = k xor g
        var q2 = iv[0]
        var r = iv[1]
        var s = iv[2]
        var xAcc = iv[3]
        var y = 0
        var z2 = 0
        var d = 0
        var rr = 0
        repeat(KEY_ROUNDS) {
            val e = (rr + fAcc) xor d xor z
            val t = (z2 + k) xor y xor o
            val nr = i + z2
            val er = c + rr
            z2 = t0[y and 255] xor t1[(y ushr 8) and 255] xor t2[(y ushr 16) and 255] xor t3[(y ushr 24) and 255]
            rr = t0[d and 255] xor t1[(d ushr 8) and 255] xor t2[(d ushr 16) and 255] xor t3[(d ushr 24) and 255]
            y = t0[er and 255] xor t1[(er ushr 8) and 255] xor t2[(er ushr 16) and 255] xor t3[(er ushr 24) and 255]
            d = t0[nr and 255] xor t1[(nr ushr 8) and 255] xor t2[(nr ushr 16) and 255] xor t3[(nr ushr 24) and 255]
            val tr = (z shl 8) xor t4[(z ushr 24) and 255] xor f xor t
            val carry = (v ushr 22) and 256
            val mask = 255 * ((v ushr 31) and 1)
            val ir = ((k ushr 24) and 255) or carry
            val ur = (g shl (8 and mask)) xor t6[(g ushr 24) and mask]
            val vr = t5[ir] xor (k shl 8) xor ur xor n xor s xor e
            z = q
            q = v
            v = f
            f = o
            o = tr
            k = n
            n = q2
            q2 = r
            r = i
            i = j
            j = s
            s = xAcc
            xAcc = g
            g = c
            c = fAcc
            fAcc = vr
        }
        schedule[0] = z
        schedule[1] = q
        schedule[2] = v
        schedule[3] = f
        schedule[4] = o
        schedule[5] = k
        schedule[6] = n
        schedule[7] = q2
        schedule[8] = r
        schedule[9] = i
        schedule[10] = j
        schedule[11] = s
        schedule[12] = xAcc
        schedule[13] = g
        schedule[14] = c
        schedule[15] = fAcc
        schedule[16] = y
        schedule[17] = z2
        schedule[18] = d
        schedule[19] = rr
    }

    private fun decryptOnMemory(words: IntArray) {
        var a = schedule[0]
        var n = schedule[1]
        var e = schedule[2]
        var t = schedule[3]
        var o = schedule[4]
        var f = schedule[5]
        var v = schedule[6]
        var w = schedule[7]
        var q = schedule[8]
        var x = schedule[9]
        var z = schedule[10]
        var c = schedule[11]
        var fAcc = schedule[12]
        var g = schedule[13]
        var h = schedule[14]
        var i = schedule[15]
        var j = schedule[16]
        var k = schedule[17]
        var n2 = schedule[18]
        var q2 = schedule[19]

        var index = 0
        val size = words.size
        while (index < size) {
            words[index] = words[index] xor ((q2 + i) xor n2 xor a)
            if (size != index + 1) words[index + 1] = words[index + 1] xor ((k + f) xor j xor o)

            val er = x + k
            val tr = h + q2
            k = t0[j and 255] xor t1[(j ushr 8) and 255] xor t2[(j ushr 16) and 255] xor t3[j ushr 24]
            q2 = t0[n2 and 255] xor t1[(n2 ushr 8) and 255] xor t2[(n2 ushr 16) and 255] xor t3[n2 ushr 24]
            j = t0[tr and 255] xor t1[(tr ushr 8) and 255] xor t2[(tr ushr 16) and 255] xor t3[tr ushr 24]
            n2 = t0[er and 255] xor t1[(er ushr 8) and 255] xor t2[(er ushr 16) and 255] xor t3[er ushr 24]

            val mixed = (a shl 8) xor t4[(a ushr 24) and 255] xor t
            val carry = (e ushr 22) and 256
            val mask = 255 * ((e ushr 31) and 1)
            val ur = ((f ushr 24) and 255) or carry
            val vr = (g shl (8 and mask)) xor t6[(g ushr 24) and mask]
            val yr = t5[ur] xor (f shl 8) xor vr xor v xor c

            a = n
            n = e
            e = t
            t = o
            o = mixed
            f = v
            v = w
            w = q
            q = x
            x = z
            z = c
            c = fAcc
            fAcc = g
            g = h
            h = i
            i = yr

            index += 2
        }
    }

    private fun ByteArray.toLeIntArray(): IntArray {
        val result = IntArray((size + 3) / 4)
        val buffer = ByteBuffer.wrap(copyOf(result.size * 4)).order(ByteOrder.LITTLE_ENDIAN)
        for (i in result.indices) result[i] = buffer.int
        return result
    }

    private fun IntArray.toLeByteArray(byteSize: Int): ByteArray {
        val result = ByteArray(byteSize)
        val full = byteSize / 4
        val buffer = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until full) buffer.putInt(this[i])
        val rem = byteSize and 3
        if (rem != 0) {
            val last = this[full]
            val base = full * 4
            for (b in 0 until rem) result[base + b] = (last ushr (b * 8)).toByte()
        }
        return result
    }

    private companion object {
        const val SCHEDULE_SIZE = 20
        const val KEY_ROUNDS = 24

        val t0 = IntArray(256)
        val t1 = IntArray(256)
        val t2 = IntArray(256)
        val t3 = IntArray(256)
        val t4 = IntArray(256)
        val t5 = IntArray(512)
        val t6 = IntArray(256)

        init {
            val r = intArrayOf(
                3054005530L.toInt(),
                2937117236L.toInt(),
                2636150632L.toInt(),
                4181782224L.toInt(),
                830480227,
                1656962758,
                3292888143L.toInt(),
                1267398814,
            )
            val a = intArrayOf(
                2700475438L.toInt(),
                1841812828,
                3668150200L.toInt(),
                2573935453L.toInt(),
                534136506,
                1048677465,
                2083468722,
                4166937161L.toInt(),
            )
            val n = intArrayOf(
                1543012243,
                3065904747L.toInt(),
                557298134,
                1114517473,
                2229034639L.toInt(),
                1173732435,
                2326214054L.toInt(),
                1493395969,
            )
            val e = intArrayOf(
                1163482763,
                2326965363L.toInt(),
                1895906790,
                3791813289L.toInt(),
                2701456439L.toInt(),
                654871918,
                1309704156,
                2619408093L.toInt(),
            )

            for (row in 0 until 256) {
                for (bit in 0 until 8) {
                    if ((row and (1 shl bit)) != 0) {
                        t4[row] = t4[row] xor r[bit]
                        t5[row] = t5[row] xor n[bit]
                        t5[256 + row] = t5[256 + row] xor a[bit]
                        t6[row] = t6[row] xor e[bit]
                    }
                }
            }

            val pow = IntArray(256)
            for (row in 1 until 256) {
                val bits = IntArray(8)
                bits[0] = row
                for (bit in 1 until 8) {
                    bits[bit] = bits[bit - 1] shl 1
                    if ((bits[bit] and 256) != 0) bits[bit] = bits[bit] xor 283
                }
                var acc = 1
                repeat(254) {
                    var next = 0
                    for (bit in 0 until 8) {
                        if ((acc and (1 shl bit)) != 0) next = next xor bits[bit]
                    }
                    acc = next
                }
                pow[row] = acc
            }

            val sbox = IntArray(256)
            for (row in 0 until 256) {
                var value = pow[row] xor (pow[row] shl 1) xor (pow[row] shl 2) xor
                    (pow[row] shl 3) xor (pow[row] shl 4)
                value = (value ushr 8) xor (value and 255) xor 99
                sbox[row] = value
            }

            for (row in 0 until 256) {
                val k = sbox[row]
                var l = k shl 1
                if ((l and 256) != 0) l = l xor 283
                val dv = l xor k

                t0[row] = (dv shl 24) xor (k shl 16) xor (k shl 8) xor l
                t1[row] = (k shl 24) xor (k shl 16) xor (l shl 8) xor dv
                t2[row] = (k shl 24) xor (l shl 16) xor (dv shl 8) xor k
                t3[row] = (l shl 24) xor (dv shl 16) xor (k shl 8) xor k
            }
        }
    }
}
