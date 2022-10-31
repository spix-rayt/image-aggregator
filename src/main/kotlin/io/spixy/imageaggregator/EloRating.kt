package io.spixy.imageaggregator

import kotlin.math.pow


object EloRating {
    fun calculate(winner: Double, looser: Double): Double {
        val exponent = (looser - winner) / 400
        val expectedOutcome = 1 / (1 + 10.0.pow(exponent))

        val k = 32

        return k * (1.0 - expectedOutcome)
    }
}