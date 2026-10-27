package com.vibetuned.to_reply.data.repo

/** One play found in a zip, as paths inside it: its recording and its script. */
internal data class ZipPlay(val audio: String, val script: String)

internal data class ZipPlan(
    /** In natural order of their recordings' paths, so "Act 2" comes before "Act 10". */
    val plays: List<ZipPlay>,
    /** Recordings no script could be paired with — reported, since a play can't import alone. */
    val unpaired: List<String>
)

/**
 * Decides which files in a zip make which plays. Pure: works on the paths alone, so it is tested
 * without a zip in sight (`PlayZipPlannerTest`). Ported from ln-reader's `ZipBookPlanner`, minus
 * EPUBs and collections: a play is always a recording plus its timed script.
 *
 * Files are grouped by the folder they sit in, and each folder is paired up on its own:
 * - a .json with the same name as an .m4b goes with it;
 * - failing that, a folder's only unpaired .m4b takes its only unpaired .json, or the one called
 *   `play.json` — the producer pipeline's layout, where the recording is named after the play and
 *   the script is always `play.json`;
 * - an .m4b left without a script lands in [ZipPlan.unpaired].
 *
 * Callers pass only play scripts among the .json files; telling a script from any other JSON
 * needs the file's contents, which the planner never sees.
 */
internal object PlayZipPlanner {

    private val wantedExtensions = setOf("m4b", "json")

    /**
     * Whether a zip entry could be part of a play. Skips folders, macOS's `__MACOSX` resource-fork
     * copies — which carry the very same names and extensions as the real files — and anything
     * hidden, such as `.DS_Store` and `._` AppleDouble files.
     */
    fun isCandidate(path: String): Boolean {
        val clean = normalize(path)
        if (clean.isEmpty() || clean.endsWith("/")) return false
        val segments = clean.split('/')
        if (segments.any { it == "__MACOSX" || it.startsWith(".") }) return false
        return extensionOf(clean) in wantedExtensions
    }

    /** Zip paths written on Windows can use backslashes; everything here works on forward ones. */
    fun normalize(path: String): String = path.replace('\\', '/').trimStart('/')

    fun plan(paths: List<String>): ZipPlan {
        val files = paths.map(::normalize).filter(::isCandidate).distinct()
        val plays = mutableListOf<ZipPlay>()
        val unpaired = mutableListOf<String>()
        for (folder in files.groupBy(::folderOf).values) {
            val (folderPlays, folderUnpaired) = planFolder(folder)
            plays += folderPlays
            unpaired += folderUnpaired
        }
        return ZipPlan(
            plays = plays.sortedWith(compareBy(NaturalOrder) { it.audio }),
            unpaired = unpaired.sortedWith(NaturalOrder)
        )
    }

    private fun planFolder(files: List<String>): Pair<List<ZipPlay>, List<String>> {
        val audios = files.filter { extensionOf(it) == "m4b" }.sortedWith(NaturalOrder)
        val scripts = files.filter { extensionOf(it) == "json" }.sortedWith(NaturalOrder).toMutableList()

        class Draft(val audio: String, var script: String? = null)

        val drafts = audios.map { audio ->
            val stem = stemOf(audio)
            Draft(
                audio = audio,
                script = scripts.firstOrNull { stemOf(it).equals(stem, ignoreCase = true) }
                    ?.also { scripts.remove(it) }
            )
        }
        drafts.filter { it.script == null }.singleOrNull()?.let { draft ->
            val pick = scripts.singleOrNull()
                ?: scripts.firstOrNull { fileNameOf(it).equals(SCRIPT_NAME, ignoreCase = true) }
            pick?.let { draft.script = it; scripts.remove(it) }
        }
        val paired = drafts.mapNotNull { d -> d.script?.let { ZipPlay(d.audio, it) } }
        return paired to drafts.filter { it.script == null }.map { it.audio }
    }

    private fun folderOf(path: String) = path.substringBeforeLast('/', missingDelimiterValue = "")
    private fun fileNameOf(path: String) = path.substringAfterLast('/')
    private fun stemOf(path: String) = fileNameOf(path).substringBeforeLast('.')
    private fun extensionOf(path: String) =
        fileNameOf(path).substringAfterLast('.', missingDelimiterValue = "").lowercase()

    private const val SCRIPT_NAME = "play.json"
}

/**
 * Orders names the way people number things: digit runs compare as numbers ("Act 2" < "Act 10"),
 * everything else case-insensitively.
 */
internal object NaturalOrder : Comparator<String> {
    private val chunk = Regex("\\d+|\\D+")

    override fun compare(a: String, b: String): Int {
        val ca = chunk.findAll(a).map { it.value }.toList()
        val cb = chunk.findAll(b).map { it.value }.toList()
        for (i in 0 until minOf(ca.size, cb.size)) {
            val x = ca[i]
            val y = cb[i]
            val result = if (x[0].isDigit() && y[0].isDigit()) {
                val nx = x.trimStart('0')
                val ny = y.trimStart('0')
                if (nx.length != ny.length) nx.length - ny.length else nx.compareTo(ny)
            } else {
                x.compareTo(y, ignoreCase = true)
            }
            if (result != 0) return result
        }
        return ca.size - cb.size
    }
}
