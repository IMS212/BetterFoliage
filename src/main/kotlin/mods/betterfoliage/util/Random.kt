package mods.betterfoliage.util

import java.util.Random

val random = Random(System.nanoTime())

fun randomB() = random.nextBoolean()
fun randomI(min: Int = 0, max: Int = Int.MAX_VALUE) = min + random.nextInt(max - min)
fun randomF(min: Double = 0.0, max: Double = 1.0) = randomD(min, max).toFloat()
fun randomD(min: Double = 0.0, max: Double = 1.0) = min + (max - min) * random.nextDouble()

fun semiRandom(x: Int, y: Int, z: Int, seed: Int): Int {
    var value = (x * x + y * y + z * z + x * y + y * z + z * x + (seed * seed))
    value = (3 * x * value + 5 * y * value + 7 * z * value + (11 * seed))
    return value shr 4
}

//fun BlockPos.semiRandom(seed: Int) = semiRandom(x, y, z, seed)