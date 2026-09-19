package com.voicechoicer.core.audio

import kotlin.math.abs

/**
 * Groups fragments into an automatically-chosen number of "characters"
 * based on each fragment's estimated voice pitch (Hz) - a heuristic
 * stand-in for real speaker diarization. It has no idea about timbre or
 * phonetics, only fundamental frequency, so it works best when speakers
 * are noticeably different pitch-wise (e.g. an adult and a child, or a
 * markedly lower vs. higher voice) and is expected to lump similar-sounding
 * voices of the same register together. Users can always fix
 * misassignments by hand afterwards.
 *
 * Uses average-linkage agglomerative clustering with an *absolute* Hz
 * tolerance (grounded in typical natural pitch variation of a single
 * speaker across utterances), not a per-batch normalized statistic - with
 * only a handful of fragments, normalizing by the sample's own variance
 * would make even a single consistent voice look artificially "spread
 * out" and get needlessly split.
 */
object SpeakerClusterer {

    /** Returns one cluster index (0-based, ordered by first appearance) per input pitch. */
    fun cluster(
        pitchesHz: List<Double>,
        maxClusters: Int = 4,
        sameSpeakerToleranceHz: Double = 30.0,
    ): List<Int> {
        if (pitchesHz.isEmpty()) return emptyList()
        if (pitchesHz.size == 1) return listOf(0)

        // Each cluster: the indices of its members and their running mean pitch.
        data class Cluster(val members: MutableList<Int>, var meanPitch: Double)
        val clusters = pitchesHz.mapIndexed { index, pitch -> Cluster(mutableListOf(index), pitch) }.toMutableList()

        while (clusters.size > 1) {
            var bestI = -1
            var bestJ = -1
            var bestDistance = Double.MAX_VALUE
            for (i in clusters.indices) {
                for (j in i + 1 until clusters.size) {
                    val distance = abs(clusters[i].meanPitch - clusters[j].meanPitch)
                    if (distance < bestDistance) {
                        bestDistance = distance
                        bestI = i
                        bestJ = j
                    }
                }
            }

            val mustMergeToRespectCap = clusters.size > maxClusters
            if (bestDistance > sameSpeakerToleranceHz && !mustMergeToRespectCap) break

            val a = clusters[bestI]
            val b = clusters[bestJ]
            val mergedSize = a.members.size + b.members.size
            val mergedMean = (a.meanPitch * a.members.size + b.meanPitch * b.members.size) / mergedSize
            a.members.addAll(b.members)
            a.meanPitch = mergedMean
            clusters.removeAt(bestJ)
        }

        val labelOf = IntArray(pitchesHz.size)
        clusters.forEachIndexed { clusterIndex, cluster -> cluster.members.forEach { labelOf[it] = clusterIndex } }
        return relabelByFirstAppearance(labelOf.toList())
    }

    private fun relabelByFirstAppearance(assignment: List<Int>): List<Int> {
        val remap = HashMap<Int, Int>()
        return assignment.map { label -> remap.getOrPut(label) { remap.size } }
    }
}
