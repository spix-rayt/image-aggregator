package io.spixy.imageaggregator.imghash

import mu.KotlinLogging
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOException
import javax.imageio.ImageIO
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

private val log = KotlinLogging.logger {}

class ImageHashUtil(private val floatArray: FloatArray, val width: Int, val height: Int) {

    fun hash(size: Int, downsample: Int): UByteArray {
        require(size % downsample == 0) { "$size is not divisible by $downsample" }

        val result = UByteArray(size * size / downsample / downsample)

        val maxSide = max(width, height) / 2

        val maxDist = (maxSide.toDouble() / size).pow(2.0) + (maxSide.toDouble() / size).pow(2.0)

        val precalculatedCircle = arrayListOf<OffsetCoef>()

        for(offsetX in ((-maxSide.toDouble() / size).toInt())..((maxSide.toDouble() / size).toInt())) {
            for (offsetY in ((-maxSide.toDouble() / size).toInt())..((maxSide.toDouble() / size).toInt())) {
                val squaredDist = offsetX * offsetX + offsetY * offsetY
                val k = (squaredDist / maxDist).coerceIn(0.0, 1.0)
                precalculatedCircle.add(OffsetCoef(offsetX, offsetY, 1.0 - k * k * k * k))
            }
        }

        for (hashX in 0 until (size / downsample)) {
            for(hashY in 0 until (size / downsample)) {
                val centerX = ((width.toDouble() - (width.toDouble() / size)) / size) * hashX * downsample + (width.toDouble() / size) / 2.0
                val centerY = ((height.toDouble() - (height.toDouble() / size)) / size) * hashY * downsample + (height.toDouble() / size) / 2.0

                val centerXInt = centerX.roundToInt()
                val centerYInt = centerY.roundToInt()

                var count = 0
                var sum = 0.0

                precalculatedCircle.forEach { offsetCoef ->
                    val imgX = centerXInt + offsetCoef.x
                    val imgY = centerYInt + offsetCoef.y
                    if(imgX in 0 until width && imgY in 0 until height) {
                        val imgColorR = floatArray[imgY * width * 3 + imgX * 3 + 0]
                        val imgColorG = floatArray[imgY * width * 3 + imgX * 3 + 1]
                        val imgColorB = floatArray[imgY * width * 3 + imgX * 3 + 2]

                        val imgColorY = imgColorR * 0.299 + imgColorG * 0.587 + imgColorB * 0.114

                        count += 1
                        sum += (imgColorY * offsetCoef.k).coerceIn(0.0..1.0)
                    }
                }
                var color = sum / count
                if(color <= 0.0031308) {
                    color *= 12.92
                } else {
                    color = (color.pow(1 / 2.4)) * 1.055 - 0.055
                }

                result[hashY * (size / downsample) + hashX] = (color * 255.0).roundToInt().coerceIn(0..255).toUByte()
            }
        }

        return result
    }

    fun write(file: File) {
        val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR)
        val newFloatArray = floatArray.map { (it.pow(1 / 2.2f) * 255.0f) }.toFloatArray()
        bufferedImage.raster.setPixels(0, 0, width, height, newFloatArray)
        ImageIO.write(bufferedImage, "jpg", file)
    }

    class OffsetCoef(val x: Int, val y: Int, val k: Double)

    companion object {
        fun read(file: File): ImageHashUtil? {
            val bufferedImage = try {
                ImageIO.read(file) ?: return null
            } catch (e: IIOException) {
                log.error("Read image error $file", e)
                return null
            }

            if(bufferedImage.type != BufferedImage.TYPE_3BYTE_BGR) {
                log.error { "image $file type is not TYPE_3BYTE_BGR" }
                return null
            }

            val arr = FloatArray(bufferedImage.width * bufferedImage.height * 3)

            bufferedImage.raster.getPixels(0, 0, bufferedImage.width, bufferedImage.height, arr)

            arr.forEachIndexed { i, v ->
                var color = v / 255.0f
                if(color <= 0.04045f) {
                    color /= 12.92f
                } else {
                    color = ((color + 0.055f) / 1.055f).pow(2.4f)
                }
                arr[i] = color
            }

            return ImageHashUtil(arr, bufferedImage.width, bufferedImage.height)
        }

        fun writeHashAsImage(file: File, hash: UByteArray, width: Int, height: Int) {
            val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)

            bufferedImage.raster.setPixels(0, 0, width, height, hash.map { ((it.toDouble() / 255.0).pow(1 / 2.2) * 255.0).toInt() }.toIntArray())
            ImageIO.write(bufferedImage, "jpg", file)
        }

        fun distance(arr1: UByteArray, arr2: UByteArray): Int {
            require(arr1.size == arr2.size)

            return arr1.zip(arr2) { b1, b2 -> (b1.toInt() - b2.toInt()).absoluteValue }.sum()
        }

        fun getImageDimensionsAndSize(file: File): Triple<Int, Int, Long>? {
            val bufferedImage = try {
                ImageIO.read(file) ?: return null
            } catch (e: IIOException) {
                log.error("Read image error $file", e)
                return null
            }

            return Triple(bufferedImage.width, bufferedImage.height, file.length())
        }
    }
}