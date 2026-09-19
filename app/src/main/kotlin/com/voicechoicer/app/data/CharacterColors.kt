package com.voicechoicer.app.data

/** A fixed, readable palette so auto-detected characters get distinct colors without any user input. */
object CharacterColors {
    private val palette = listOf(
        0xFFE57373.toInt(), // red
        0xFF64B5F6.toInt(), // blue
        0xFF81C784.toInt(), // green
        0xFFFFB74D.toInt(), // orange
        0xFFBA68C8.toInt(), // purple
        0xFF4DB6AC.toInt(), // teal
        0xFFF06292.toInt(), // pink
        0xFFA1887F.toInt(), // brown
    )

    fun forIndex(index: Int): Int = palette[index % palette.size]
}
