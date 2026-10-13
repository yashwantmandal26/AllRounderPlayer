package com.example.ymediaplayer.ui

import kotlin.math.ln

/** Map the five reference gains to any hardware EQ layout in logarithmic frequency space. */
internal fun interpolateVideoEqGain(frequencyHz: Float, gains: List<Float>): Float {
    require(gains.size == 5)
    val frequencies = floatArrayOf(50f, 250f, 1000f, 4000f, 12000f)
    if (frequencyHz <= frequencies.first()) return gains.first()
    if (frequencyHz >= frequencies.last()) return gains.last()
    val upper = frequencies.indexOfFirst { it >= frequencyHz }
    val lower = upper - 1
    val fraction = ln(frequencyHz / frequencies[lower]) / ln(frequencies[upper] / frequencies[lower])
    return gains[lower] + (gains[upper] - gains[lower]) * fraction
}
