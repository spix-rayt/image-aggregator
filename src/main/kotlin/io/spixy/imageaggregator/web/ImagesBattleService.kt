package io.spixy.imageaggregator.web

import io.spixy.imageaggregator.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.io.File
import kotlin.random.Random
import kotlin.random.nextInt

private val log = KotlinLogging.logger {}

class ImagesBattleService(coroutineScope: CoroutineScope) {
    private val images = ImagePaths.PASS_DIR.toFile().walk()
        .filter { it.isFile }
        .filter { it.hasImageExtension() }
        .map { Image(it) }
        .toMutableList()
    private var currentImages: LeftAndRightImage? = null
    private val comparisonsDataFile = File("data/comparisons.txt").alsoCreateIfNotExists()

    private val recordedComparisons = comparisonsDataFile.readLines().mapNotNull { line ->
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
            NewImageEventBus.events
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
            comparisonsDataFile.appendText("${currentImages.left.hash} > ${currentImages.right.hash}\n")
            recordedComparisons.add(RecordedComparison(currentImages.left, currentImages.right, ComparisonResult.LEFT))
        }
        setNextCurrentImage()
    }

    fun rightBetter() {
        currentImages?.let { currentImages ->
            comparisonsDataFile.appendText("${currentImages.left.hash} < ${currentImages.right.hash}\n")
            recordedComparisons.add(RecordedComparison(currentImages.left, currentImages.right, ComparisonResult.RIGHT))
        }
        setNextCurrentImage()
    }

    fun skip() {
        setNextCurrentImage()
    }

    fun getTop(n: Int): List<Image> {
        contestAndSort(1000)
        return images.take(n)
    }

    private fun contestAndSort(times: Int) {
        repeat(times) {
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
        images.sortByDescending { it.rating }
    }

    private fun setNextCurrentImage(): LeftAndRightImage? {
        if(images.size < 2) {
            currentImages = null
            return null
        }
        contestAndSort(10)
        if(Random.nextBoolean()) {
            for(left in images.subList(0, images.size / 2).shuffled()) {
                for(right in images.subList(images.size / 2, images.size).shuffled()) {
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
        } else {
            currentImages = null
            val randomStart = Random.nextInt(images.indices)
            for(left in images.subList(randomStart, images.size)) {
                for(right in images.subList(randomStart, images.size)) {
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
            if(currentImages == null) {
                setNextCurrentImage()
                return currentImages
            }
        }

        currentImages = null
        return null
    }

    class LeftAndRightImage(val left: Image, val right: Image)

    class Image(val file: File, var rating: Double = 0.0) {
        val hash = file.md5()
    }

    class RecordedComparison(val left: Image, val right: Image, val result: ComparisonResult)

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
}