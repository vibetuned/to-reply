package com.vibetuned.to_reply.player

/**
 * Pure seek-target math for the previous/next-scene transport, shared by the mini-player and
 * the media-notification buttons so both behave identically. Scene starts come from the play's
 * script (the timing source of truth) via PlayRepository.sceneStartsMs, sorted ascending.
 */
object SceneSkip {

    /**
     * "Previous" follows the audio-player chapter convention: more than [RESTART_THRESHOLD_MS]
     * into the current scene restarts it; near its start jumps to the scene before. Positions
     * before the first scene start clamp to 0. Null when there's nothing to seek to.
     */
    fun previousTargetMs(sceneStarts: List<Long>, positionMs: Long): Long? {
        if (sceneStarts.isEmpty()) return null
        val idx = lastIndexAtOrBefore(sceneStarts, positionMs)
        if (idx < 0) return 0L
        val currentStart = sceneStarts[idx]
        return if (positionMs - currentStart > RESTART_THRESHOLD_MS || idx == 0) currentStart
        else sceneStarts[idx - 1]
    }

    /** Start of the scene after the current one, or null when already in the last scene. */
    fun nextTargetMs(sceneStarts: List<Long>, positionMs: Long): Long? {
        if (sceneStarts.isEmpty()) return null
        return sceneStarts.getOrNull(lastIndexAtOrBefore(sceneStarts, positionMs) + 1)
    }

    private fun lastIndexAtOrBefore(sceneStarts: List<Long>, positionMs: Long): Int {
        var lo = 0
        var hi = sceneStarts.size - 1
        var result = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (sceneStarts[mid] <= positionMs) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }

    private const val RESTART_THRESHOLD_MS = 3_000L
}
