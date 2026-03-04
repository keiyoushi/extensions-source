package eu.kanade.tachiyomi.extension.ja.mangaraw

import android.graphics.Bitmap
import android.graphics.Canvas
import keiyoushi.lib.seedrandom.SeedRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.floor
import kotlin.math.roundToInt

object ImageUnscrambler {

    fun unscrambleImage(bitmap: Bitmap, chapterId: String, chapterUuid: String): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0 || chapterId.isBlank() || chapterUuid.isBlank()) {
            return bitmap
        }

        val hmacHash = hmacSha256(chapterUuid, chapterId)

        val baseRng = SeedRandom(hmacHash)
        val tiles = buildTiles(width, height, baseRng)
        if (tiles.isEmpty()) return bitmap

        val groupedByDimension = mutableMapOf<String, MutableList<Int>>()
        for (tile in tiles) {
            val key = "${tile.width}x${tile.height}"
            groupedByDimension.getOrPut(key) { mutableListOf() }.add(tile.index)
        }

        val mapping = mutableMapOf<Int, TileTransform>()
        val processed = mutableSetOf<String>()

        for ((dimensionKey, indices) in groupedByDimension) {
            if (processed.contains(dimensionKey)) continue

            val (tileW, tileH) = dimensionKey.split("x").map { it.toInt() }
            val isSquare = tileW == tileH
            val inverseKey = "${tileH}x$tileW"
            val hasPair = inverseKey != dimensionKey && groupedByDimension.containsKey(inverseKey)

            if (hasPair) {
                val pairIndices = groupedByDimension.getValue(inverseKey)
                processed.add(dimensionKey)
                processed.add(inverseKey)

                val pairRng = SeedRandom("$hmacHash|pair|$dimensionKey|$inverseKey")
                val aIndices = indices.toMutableList()
                val bIndices = pairIndices.toMutableList()
                shuffleInPlace(aIndices) { pairRng.nextDouble() }
                shuffleInPlace(bIndices) { pairRng.nextDouble() }

                val common = minOf(aIndices.size, bIndices.size)
                for (index in 0 until common) {
                    val tfRng = SeedRandom("$hmacHash|tf|AtoB|$dimensionKey|$index")
                    val transform = generateTransform(tfRng, allowSwap = true, isSquare = false)
                    mapping[aIndices[index]] = TileTransform(
                        destinationIndex = bIndices[index],
                        rotation = transform.rotation,
                        flipH = transform.flipH,
                        flipV = transform.flipV,
                    )
                }

                for (index in 0 until common) {
                    val tfRng = SeedRandom("$hmacHash|tf|BtoA|$inverseKey|$index")
                    val transform = generateTransform(tfRng, allowSwap = true, isSquare = false)
                    mapping[bIndices[index]] = TileTransform(
                        destinationIndex = aIndices[index],
                        rotation = transform.rotation,
                        flipH = transform.flipH,
                        flipV = transform.flipV,
                    )
                }

                if (aIndices.size > common) {
                    val remaining = aIndices.drop(common)
                    val permuted = remaining.toMutableList()
                    val permRng = SeedRandom("$hmacHash|perm|A|$dimensionKey")
                    shuffleInPlace(permuted) { permRng.nextDouble() }

                    for (index in remaining.indices) {
                        val tfRng = SeedRandom("$hmacHash|tf|A|$dimensionKey|rest|$index")
                        val transform = generateTransform(tfRng, allowSwap = false, isSquare = isSquare)
                        mapping[remaining[index]] = TileTransform(
                            destinationIndex = permuted[index],
                            rotation = transform.rotation,
                            flipH = transform.flipH,
                            flipV = transform.flipV,
                        )
                    }
                }

                if (bIndices.size > common) {
                    val remaining = bIndices.drop(common)
                    val permuted = remaining.toMutableList()
                    val permRng = SeedRandom("$hmacHash|perm|B|$inverseKey")
                    shuffleInPlace(permuted) { permRng.nextDouble() }

                    for (index in remaining.indices) {
                        val tfRng = SeedRandom("$hmacHash|tf|B|$inverseKey|rest|$index")
                        val transform = generateTransform(tfRng, allowSwap = false, isSquare = isSquare)
                        mapping[remaining[index]] = TileTransform(
                            destinationIndex = permuted[index],
                            rotation = transform.rotation,
                            flipH = transform.flipH,
                            flipV = transform.flipV,
                        )
                    }
                }
            } else {
                processed.add(dimensionKey)
                val permuted = indices.toMutableList()
                val permRng = SeedRandom("$hmacHash|perm|$dimensionKey")
                shuffleInPlace(permuted) { permRng.nextDouble() }

                for (index in indices.indices) {
                    val tfRng = SeedRandom("$hmacHash|tf|self|$dimensionKey|$index")
                    val transform = generateTransform(tfRng, allowSwap = false, isSquare = isSquare)
                    mapping[indices[index]] = TileTransform(
                        destinationIndex = permuted[index],
                        rotation = transform.rotation,
                        flipH = transform.flipH,
                        flipV = transform.flipV,
                    )
                }
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        for ((sourceIndex, transform) in mapping) {
            val destinationTile = tiles[transform.destinationIndex]
            val sourceTile = tiles[sourceIndex]

            val sourcePatch = Bitmap.createBitmap(
                bitmap,
                destinationTile.x,
                destinationTile.y,
                destinationTile.width,
                destinationTile.height,
            )

            val transformedPatch = applyTransformation(
                sourcePatch,
                transform.rotation,
                transform.flipH,
                transform.flipV,
            )

            canvas.drawBitmap(transformedPatch, sourceTile.x.toFloat(), sourceTile.y.toFloat(), null)
            transformedPatch.recycle()
            sourcePatch.recycle()
        }

        return result
    }

    private fun hmacSha256(key: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)
        val hashBytes = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun buildTiles(width: Int, height: Int, rng: SeedRandom): List<TileInfo> {
        val maxTile = 222
        val tileSizesX = calculateTileSizes(width, maxTile, rng)
        val tileSizesY = calculateTileSizes(height, maxTile, rng)

        val tiles = mutableListOf<TileInfo>()
        var index = 0
        var currentY = 0

        for (tileHeight in tileSizesY) {
            var currentX = 0
            for (tileWidth in tileSizesX) {
                tiles.add(TileInfo(index, currentX, currentY, tileWidth, tileHeight))
                currentX += tileWidth
                index++
            }
            currentY += tileHeight
        }

        return tiles
    }

    private fun calculateTileSizes(dimension: Int, maxTile: Int, rng: SeedRandom): List<Int> {
        var tileCount = maxOf(1, (dimension.toDouble() / maxTile).roundToInt())

        var averageTile = floor(dimension.toDouble() / tileCount).toInt()
        while (averageTile > 259) {
            tileCount += 1
            averageTile = floor(dimension.toDouble() / tileCount).toInt()
        }
        while (averageTile < 185 && tileCount > 1) {
            tileCount -= 1
            averageTile = floor(dimension.toDouble() / tileCount).toInt()
        }

        val baseSize = floor(dimension.toDouble() / tileCount).toInt()
        val remainder = dimension - (baseSize * tileCount)
        val sizes = MutableList(tileCount) { baseSize }
        for (index in 0 until remainder) {
            sizes[index] = baseSize + 1
        }

        shuffleInPlace(sizes) { rng.nextDouble() }
        return sizes
    }

    private fun <T> shuffleInPlace(list: MutableList<T>, random: () -> Double) {
        for (i in list.size - 1 downTo 1) {
            val j = floor(random() * (i + 1)).toInt()
            val temp = list[i]
            list[i] = list[j]
            list[j] = temp
        }
    }

    private fun generateTransform(rng: SeedRandom, allowSwap: Boolean, isSquare: Boolean): Transform {
        val rotation = if (allowSwap) {
            if (rng.nextDouble() < 0.5) 1 else 3
        } else if (isSquare) {
            listOf(0, 1, 2, 3)[(rng.nextDouble() * 4).toInt()]
        } else {
            if (rng.nextDouble() < 0.5) 0 else 2
        }

        val flipH = if (rng.nextDouble() < 0.5) 1 else 0
        val flipV = if (rng.nextDouble() < 0.5) 1 else 0

        return Transform(rotation, flipH, flipV)
    }

    private fun applyTransformation(bitmap: Bitmap, rotation: Int, flipH: Int, flipV: Int): Bitmap {
        val isSwapRotation = rotation % 2 == 1
        val outputWidth = if (isSwapRotation) bitmap.height else bitmap.width
        val outputHeight = if (isSwapRotation) bitmap.width else bitmap.height

        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.save()

        if (isSwapRotation) {
            if (rotation == 1) {
                canvas.translate(0f, outputWidth.toFloat())
                canvas.rotate(-90f)
            } else {
                canvas.translate(outputHeight.toFloat(), 0f)
                canvas.rotate(90f)
            }
            canvas.translate(
                if (flipH == 1) outputWidth.toFloat() else 0f,
                if (flipV == 1) outputHeight.toFloat() else 0f,
            )
            canvas.scale(if (flipH == 1) -1f else 1f, if (flipV == 1) -1f else 1f)
        } else {
            if (rotation == 2) {
                canvas.translate(outputWidth.toFloat(), outputHeight.toFloat())
                canvas.rotate(180f)
            }
            canvas.translate(
                if (flipH == 1) outputWidth.toFloat() else 0f,
                if (flipV == 1) outputHeight.toFloat() else 0f,
            )
            canvas.scale(if (flipH == 1) -1f else 1f, if (flipV == 1) -1f else 1f)
        }

        canvas.drawBitmap(bitmap, 0f, 0f, null)
        canvas.restore()
        return output
    }

    private data class TileInfo(
        val index: Int,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    )

    private data class TileTransform(
        val destinationIndex: Int,
        val rotation: Int,
        val flipH: Int,
        val flipV: Int,
    )

    private data class Transform(
        val rotation: Int,
        val flipH: Int,
        val flipV: Int,
    )
}
