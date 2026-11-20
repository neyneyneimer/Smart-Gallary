package ai.sakana.aura.gallery.data.deduplication

/** Bit-difference helpers for 64-bit perceptual hashes. */
object HammingDistance {
    const val DUPLICATE_MAX: Int = 5
    const val SIMILAR_MAX: Int = 10

    enum class Match { DUPLICATE, SIMILAR, UNRELATED }

    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /** distance <= 5 duplicate, <= 10 similar, otherwise unrelated. */
    fun classify(distance: Int): Match = when {
        distance <= DUPLICATE_MAX -> Match.DUPLICATE
        distance <= SIMILAR_MAX -> Match.SIMILAR
        else -> Match.UNRELATED
    }
}
