package io.spixy.imageaggregator.web

import com.google.gson.Gson
import io.spixy.imageaggregator.*
import io.spixy.imageaggregator.imghash.ImageHashUtil
import kotlinx.coroutines.*
import mu.KotlinLogging
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private val log = KotlinLogging.logger {}

object ImageDeduplicationService {

    private const val SIMILARITY_TRESHOLD = 500

    private val gson = Gson()

    private val files = (ImagePaths.DOWNLOAD_DIR.toFile().walk() + ImagePaths.PASS_DIR.toFile().walk())
        .filter { it.isFile }
        .filter { it.hasImageExtension() }
        .toMutableList()
        .also { it.shuffle() }

    private val avgHashes = mutableListOf<ImageDigest>()

    private var currentImages: LeftAndRightImage? = null

    private val skippedImagePairsDataFile = File("data/skipped_image_pairs.txt").alsoCreateIfNotExists()
    private val skippedImagePairs = skippedImagePairsDataFile.readLines().map { line ->
        gson.fromJson(line, SkippedImagePair::class.java)
    }.toMutableSet()


    suspend fun start(coroutineScope: CoroutineScope) = coroutineScope.launch {
        log.info { "ImageDeduplication started".paintGreen() }

        while (true) {
            val imageFile = files.removeFirstOrNull()
            if(imageFile != null && imageFile.exists()) {
                val image = ImageHashUtil.read(imageFile)
                if(image != null) {
                    val avgHash = withContext(Dispatchers.Default) {
                        image.hash(8, 1)
                    }
                    avgHashes.add(ImageDigest(imageFile, avgHash))
                }
                if(avgHashes.size % 100 == 0) {
                    log.info { "Image perceptual hashes count now = ${avgHashes.size}" }
                }
                yield()
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
                        test(imgDigestLeft, imgDigestRight)?.let { return it }
                    }
                }
            } else {
                error("Something wrong happened. need to debug this. ${currentNotDeleted.file.toPath()}")
            }
        }

        //Then trying to find random pair of similar images
        shuffled.subList(0, shuffled.lastIndex).forEachIndexed { index, imgDigest1 ->
            shuffled.subList(index + 1, shuffled.size).forEach { imgDigest2 ->
                test(imgDigest1, imgDigest2)?.let { return it }
            }
        }
        return null
    }

    private fun test(imgDigest1: ImageDigest, imgDigest2: ImageDigest): LeftAndRightImage? {
        if(notSkippedBefore(imgDigest1, imgDigest2)) {
            val dist = ImageHashUtil.distance(imgDigest1.avgHash, imgDigest2.avgHash)
            if(dist < SIMILARITY_TRESHOLD) {
                if(imgDigest1.file.exists() && imgDigest2.file.exists()) {
                    val image1Dimensions = ImageHashUtil.getImageDimensions(imgDigest1.file)
                    val image2Dimensions = ImageHashUtil.getImageDimensions(imgDigest2.file)
                    if(image1Dimensions != null && image2Dimensions != null) {
                        return LeftAndRightImage(
                            Image(imgDigest1.file, image1Dimensions.first, image1Dimensions.second),
                            Image(imgDigest2.file, image2Dimensions.first, image2Dimensions.second),
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
                deleteRight()
                currentImages = findNextSimilars()
            } else {
                break
            }
        }
    }

    private fun notSkippedBefore(a: ImageDigest, b: ImageDigest): Boolean {
        return !skippedImagePairs.contains(SkippedImagePair(a.file.normalize().path, b.file.normalize().path))
    }

    class LeftAndRightImage(val left: Image, val right: Image, val dist: Int)

    class Image(val file: File, val width: Int, val height: Int)

    private class ImageDigest(val file: File, val avgHash: UByteArray)

    data class SkippedImagePair(val path1: String, val path2: String)
}