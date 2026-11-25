package com.dayora.app.data.local.entity

enum class DayState(
    val label: String,
    val defaultCountsAsGood: Boolean,
    val defaultRoutesToRainRoom: Boolean
) {
    BRIGHT("Bright", defaultCountsAsGood = true, defaultRoutesToRainRoom = false),
    WARM("Warm", defaultCountsAsGood = true, defaultRoutesToRainRoom = false),
    ORDINARY("Ordinary", defaultCountsAsGood = false, defaultRoutesToRainRoom = false),
    HEAVY("Heavy", defaultCountsAsGood = false, defaultRoutesToRainRoom = true);

    companion object {
        val DEFAULT_SPARKS = listOf(
            "Friends",
            "Family",
            "Peace",
            "Good Food",
            "Small Win",
            "Outdoors",
            "Learning",
            "Just Felt Good"
        )
    }
}
