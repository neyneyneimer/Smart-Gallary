package ai.sakana.aura.gallery.design

import androidx.compose.ui.graphics.ColorMatrix

/**
 * Non-destructive preview matrix. Order follows the design handoff:
 * saturation -> contrast -> warmth, with brightness as the final translation.
 */
object AuraToneColorMatrix {
    fun create(brightness: Float, contrast: Float, saturation: Float, warmth: Float): ColorMatrix {
        fun identity() = Array(5) { row -> FloatArray(5) { col -> if (row == col) 1f else 0f } }
        fun multiply(a: Array<FloatArray>, b: Array<FloatArray>) = Array(5) { row ->
            FloatArray(5) { col -> (0..4).sumOf { k -> (a[row][k] * b[k][col]).toDouble() }.toFloat() }
        }

        val sat = identity()
        val s = (1f + saturation * 0.4f).coerceAtLeast(0f)
        val r = 0.213f * (1f - s)
        val g = 0.715f * (1f - s)
        val b = 0.072f * (1f - s)
        sat[0][0] = r + s; sat[0][1] = g; sat[0][2] = b
        sat[1][0] = r; sat[1][1] = g + s; sat[1][2] = b
        sat[2][0] = r; sat[2][1] = g; sat[2][2] = b + s

        val ctr = identity()
        val c = (1f + contrast * 0.45f).coerceAtLeast(0.05f)
        for (i in 0..2) {
            ctr[i][i] = c
            ctr[i][4] = 0.5f * (1f - c) * 255f
        }

        val warm = identity()
        warm[0][0] = 1f + warmth * 0.12f
        warm[2][2] = 1f - warmth * 0.12f

        val bright = identity()
        val offset = brightness * 0.16f * 255f
        for (i in 0..2) bright[i][4] = offset

        val matrix = multiply(bright, multiply(warm, multiply(ctr, sat)))
        val values = FloatArray(20)
        for (row in 0..3) for (col in 0..4) values[row * 5 + col] = matrix[row][col]
        return ColorMatrix(values)
    }
}
