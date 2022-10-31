package io.spixy.imageaggregator.web

import io.spixy.imageaggregator.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.io.File

class ImageComparisons(coroutineScope: CoroutineScope) {
    private val images = ImagePaths.PASS_DIR.toFile().walk()
        .filter { it.isFile }
        .filter { it.hasImageExtension() }
        .map { Image(it) }
        .toMutableList()
    private var currentImages: LeftAndRightImage? = null
    private val comparisonsFile = File(ImagePaths.PASS_DIR.toFile(), "comparisons.txt").also {
        if(!it.exists()) {
            it.parentFile.mkdirs()
            it.createNewFile()
        }
    }

    private val recordedComparisons = comparisonsFile.readLines().mapNotNull { line ->
        val split = line.split(" ")
        val leftImg = images.find { it.hash == split[0] }
        val rightImg = images.find { it.hash == split[2] }
        val comparisonResult = ComparisonResult.fromSign(split[1])
        if(leftImg != null && rightImg != null) {
            RecordedComparison(leftImg, rightImg, comparisonResult)
        } else {
            null
        }
    }.toMutableList()



    init {
        coroutineScope.launch {
            ImageChangedEventBus.events
                .filter { it.toPath().normalize().startsWith(ImagePaths.PASS_DIR) }
                .collect { file ->
                    images.add(Image(file))
                }
        }
    }

    fun getCurrent(): LeftAndRightImage? {
        if (currentImages == null) {
            setNextCurrentImage()
        }
        return currentImages
    }

    fun leftBetter() {
        currentImages?.let { currentImages ->
            comparisonsFile.appendText("${currentImages.left.hash} > ${currentImages.right.hash}\n")
            recordedComparisons.add(RecordedComparison(currentImages.left, currentImages.right, ComparisonResult.LEFT))
        }
        setNextCurrentImage()
    }

    fun rightBetter() {
        currentImages?.let { currentImages ->
            comparisonsFile.appendText("${currentImages.left.hash} < ${currentImages.right.hash}\n")
            recordedComparisons.add(RecordedComparison(currentImages.left, currentImages.right, ComparisonResult.RIGHT))
        }
        setNextCurrentImage()
    }

    fun skip() {
        setNextCurrentImage()
    }

    fun getTop(): List<Image> {
        images.forEach { it.rating = 0.0 }

        repeat(100000) {
            recordedComparisons.forEach {
                if(it.result == ComparisonResult.LEFT) {
                    val eloChangeForLeft = EloRating.calculate(it.left.rating, it.right.rating)
                    it.left.rating += eloChangeForLeft
                    it.right.rating -= eloChangeForLeft
                }
                if(it.result == ComparisonResult.RIGHT) {
                    val eloChangeForRight = EloRating.calculate(it.right.rating, it.left.rating)
                    it.right.rating += eloChangeForRight
                    it.left.rating -= eloChangeForRight
                }
            }
        }
        return images.sortedByDescending { it.rating }.take(20)
    }

    private fun setNextCurrentImage(): LeftAndRightImage? {
        if(images.size < 2) {
            currentImages = null
            return null
        }
        for(left in images.shuffled()) {
            for(right in images.shuffled()) {
                if(left === right) {
                    break
                }
                if(recordedComparisons.any { it.left.hash == left.hash && it.right.hash == right.hash }) {
                    break
                }
                if(recordedComparisons.any { it.left.hash == right.hash && it.right.hash == left.hash }) {
                    break
                }
                currentImages = LeftAndRightImage(left, right)
                return currentImages
            }
        }
        currentImages = null
        return null
    }
}

data class LeftAndRightImage(val left: Image, val right: Image)

data class Image(val file: File, var rating: Double = 0.0) {
    val hash = file.md5()
}

data class RecordedComparison(val left: Image, val right: Image, val result: ComparisonResult)

enum class ComparisonResult {
    LEFT, RIGHT;

    companion object {
        fun fromSign(str: String): ComparisonResult {
            return when(str) {
                ">" -> LEFT
                "<" -> RIGHT
                else -> error("unknown sign $str")
            }
        }
    }
}