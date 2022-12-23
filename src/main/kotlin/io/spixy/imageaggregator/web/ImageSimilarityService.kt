package io.spixy.imageaggregator.web

import com.google.gson.Gson
import io.spixy.imageaggregator.*
import io.spixy.imageaggregator.imghash.ImageHashUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import mu.KotlinLogging
import org.apache.commons.codec.binary.Base64
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val log = KotlinLogging.logger {}

object ImageSimilarityService {

    var similarityTreshold = 50
        private set

    private val gson = Gson()

    private val files = (ImagePaths.DOWNLOAD_DIR.toFile().walk() + ImagePaths.PASS_DIR.toFile().walk())
        .filter { it.isFile }
        .filter { it.hasImageExtension() }
        .toMutableList()
        .also { it.shuffle() }

    private val avgHashes = mutableListOf<ImageDigest>()

    private var currentImages: LeftAndRightImage? = null

    private val imageHashDataFile = File("data/image_hash.txt").alsoCreateIfNotExists()
    private val imageHashDict = imageHashDataFile.readLines().map {
        val (md5, avgHashBase64) = it.split(" ")
        md5 to Base64.decodeBase64(avgHashBase64).toUByteArray()
    }.toMap()
    private val skippedImagePairsDataFile = File("data/skipped_image_pairs.txt").alsoCreateIfNotExists()
    private val skippedImagePairs = skippedImagePairsDataFile.readLines().map { line ->
        gson.fromJson(line, SkippedImagePair::class.java)
    }.toMutableSet()


    suspend fun start(coroutineScope: CoroutineScope) = coroutineScope.launch {
        log.info { "ImageDeduplication started".paintGreen() }

        files.asFlow().collect { imageFile ->
            if(imageFile.exists()) {
                val md5 = withContext(Dispatchers.IO) { imageFile.md5() }
                val uByteArray = imageHashDict[md5]
                if(uByteArray != null) {
                    avgHashes.add(ImageDigest(imageFile, uByteArray))
                } else {
                    val image = withContext(Dispatchers.IO) { ImageHashUtil.read(imageFile) }
                    if(image != null) {
                        val avgHash = withContext(Dispatchers.Default) {
                            image.hash(8, 1)
                        }
                        imageHashDataFile.appendText("$md5 ${Base64.encodeBase64String(avgHash.toByteArray())}\n")
                        avgHashes.add(ImageDigest(imageFile, avgHash))
                    }
                }


                if(avgHashes.size % 100 == 0) {
                    log.info { "Count of images ready for find similar = ${avgHashes.size}" }
                }
            }
        }
    }

    fun getCurrent(): LeftAndRightImage? {
        if (currentImages == null) {
            next()
        }
        return currentImages
    }

    fun deleteRightAndNext() {
        deleteRight()
        next()
    }

    private fun deleteRight() {
        currentImages?.let { currentImage ->
            val newPath = rebasePathWithOldBaseAutodetect(
                listOf(ImagePaths.DOWNLOAD_DIR, ImagePaths.PASS_DIR), ImagePaths.TRASH_DIR, currentImage.right.file)
            newPath.parentFile.mkdirs()
            if(currentImage.right.file.exists()) {
                log.info { "Move image ${currentImage.right.file} to $newPath" }
                if(newPath.exists()) {
                    log.warn { "$newPath already exists. overwriting..." }
                }
                Files.move(currentImage.right.file.toPath(), newPath.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } else {
                log.warn { "Image ${currentImage.right.file} not exists" }
            }
        }
    }

    fun deleteLeftAndNext() {
        deleteLeft()
        next()
    }

    private fun deleteLeft() {
        currentImages?.let { currentImage ->
            val newPath = rebasePathWithOldBaseAutodetect(
                listOf(ImagePaths.DOWNLOAD_DIR, ImagePaths.PASS_DIR), ImagePaths.TRASH_DIR, currentImage.left.file)
            newPath.parentFile.mkdirs()
            if(currentImage.left.file.exists()) {
                log.info { "Move image ${currentImage.left.file} to $newPath" }
                if(newPath.exists()) {
                    log.warn { "$newPath already exists. overwriting..." }
                }
                Files.move(currentImage.left.file.toPath(), newPath.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } else {
                log.warn { "Image ${currentImage.left.file} not exists" }
            }
        }
    }

    fun deleteBothAndNext() {
        deleteLeft()
        deleteRight()
        next()
    }

    fun skipAndNext() {
        currentImages?.left?.file?.normalize()?.path?.let { path1 ->
            currentImages?.right?.file?.normalize()?.path?.let { path2 ->
                val pair = SkippedImagePair(path1, path2)
                skippedImagePairs.add(pair)
                skippedImagePairsDataFile.appendText("${gson.toJson(pair)}\n")
                val reversedPair = SkippedImagePair(path2, path1)
                skippedImagePairs.add(reversedPair)
                skippedImagePairsDataFile.appendText("${gson.toJson(reversedPair)}\n")
            }
        }
        next()
    }

    private fun findNextSimilars(): LeftAndRightImage? {
        val shuffled = avgHashes.shuffled()

        //First of all using not deleted image from current state
        val currentNotDeleted = currentImages?.left?.takeIf { it.file.exists() }
            ?: currentImages?.right?.takeIf { it.file.exists() }

        if(currentNotDeleted != null) {
            val imgDigestLeft = shuffled.find { currentNotDeleted.file == it.file }
            if(imgDigestLeft != null) {
                shuffled.forEach { imgDigestRight ->
                    if(imgDigestLeft !== imgDigestRight) {
                        test(imgDigestLeft, imgDigestRight)?.let { return it.swappedLeftAndRight() }
                    }
                }
            } else {
                error("Something wrong happened. need to debug this. ${currentNotDeleted.file.toPath()}")
            }
        }

        //Then trying to find random pair of similar images
        shuffled.subList(0, shuffled.lastIndex).forEachIndexed { index, imgDigest1 ->
            shuffled.subList(index + 1, shuffled.size).forEach { imgDigest2 ->
                test(imgDigest1, imgDigest2)?.let { return it.swappedLeftAndRight() }
            }
        }
        if(similarityTreshold < 700) {
            similarityTreshold += 50
            findNextSimilars()
        }

        return null
    }

    private fun test(imgDigest1: ImageDigest, imgDigest2: ImageDigest): LeftAndRightImage? {
        if(notSkippedBefore(imgDigest1, imgDigest2)) {
            val dist = ImageHashUtil.distance(imgDigest1.avgHash, imgDigest2.avgHash)
            if(dist < similarityTreshold) {
                if(imgDigest1.file.exists() && imgDigest2.file.exists()) {
                    val i1DimSize = ImageHashUtil.getImageDimensionsAndSize(imgDigest1.file)
                    val i2DimSize = ImageHashUtil.getImageDimensionsAndSize(imgDigest2.file)
                    if(i1DimSize != null && i2DimSize != null) {
                        return LeftAndRightImage(
                            Image(imgDigest1.file, i1DimSize.first, i1DimSize.second, i1DimSize.third),
                            Image(imgDigest2.file, i2DimSize.first, i2DimSize.second, i2DimSize.third),
                            dist
                        )
                    }
                }
            }
        }
        return null
    }

    private fun next() {
        currentImages = findNextSimilars()
        while (true) {
            val curr = currentImages ?: break
            if(curr.dist == 0 && curr.left.width == curr.right.width && curr.left.height == curr.right.height) {
                if(curr.left.size > curr.right.size) {
                    deleteRight()
                } else {
                    deleteLeft()
                }
                currentImages = findNextSimilars()
            } else {
                break
            }
        }
    }

    private fun notSkippedBefore(a: ImageDigest, b: ImageDigest): Boolean {
        return !skippedImagePairs.contains(SkippedImagePair(a.file.normalize().path, b.file.normalize().path))
    }

    class LeftAndRightImage(val left: Image, val right: Image, val dist: Int) {
        fun swappedLeftAndRight(): LeftAndRightImage {
            return LeftAndRightImage(right, left, dist)
        }
    }

    class Image(val file: File, val width: Int, val height: Int, val size: Long)

    private class ImageDigest(val file: File, val avgHash: UByteArray)

    data class SkippedImagePair(val path1: String, val path2: String)
}