package com.sonder.app.util

import com.sonder.app.data.local.entity.ChapterEntity
import com.sonder.app.data.local.entity.MomentEntity
import com.sonder.app.data.local.entity.MomentTagEntity

/**
 * Plain-text chapter recap. Deliberately not a baked image: the user can share
 * only the words they choose, and nothing is uploaded anywhere.
 */
object ChapterRecap {

    fun build(
        chapter: ChapterEntity,
        moments: List<MomentEntity>,
        tags: List<MomentTagEntity>
    ): String {
        val inRange = moments.filter { moment ->
            !moment.isUnsent &&
                moment.localDate >= chapter.startDate &&
                (chapter.endDate == null || moment.localDate <= chapter.endDate)
        }
        val ids = inRange.map { it.id }.toSet()
        val sunny = inRange.count { (it.weatherScore ?: 0f) > 0.24f || it.countsAsGood }
        val rainy = inRange.count { (it.weatherScore ?: 0f) <= -0.25f || it.isRainRoom }
        val quiet = (inRange.size - sunny - rainy).coerceAtLeast(0)

        val topTags = tags
            .filter { it.momentId in ids && it.userConfirmed }
            .groupBy { it.normalizedName to it.tagType }
            .map { (_, group) -> group.first().tagName to group.size }
            .sortedByDescending { it.second }
            .take(5)

        val start = DateDisplayFormatter.isoToDisplayString(chapter.startDate)
        val end = chapter.endDate?.let { DateDisplayFormatter.isoToDisplayString(it) }
            ?: "ongoing"
        return buildString {
            appendLine("Chapter: ${chapter.title}")
            appendLine("$start → $end")
            appendLine()
            appendLine(
                if (inRange.size == 1) "1 moment kept."
                else "${inRange.size} moments kept."
            )
            if (inRange.isNotEmpty()) {
                appendLine("Weather: $sunny sunlit · $rainy rainy · $quiet quiet")
            }
            if (topTags.isNotEmpty()) {
                appendLine(
                    "Recurring threads: " +
                        topTags.joinToString(", ") { (name, count) -> "$name ($count)" }
                )
            }
            appendLine()
            append("A stretch of life kept quietly on Sonder.")
        }
    }
}
