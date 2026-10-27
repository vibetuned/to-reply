package com.vibetuned.to_reply

import com.vibetuned.to_reply.data.repo.NaturalOrder
import com.vibetuned.to_reply.data.repo.PlayZipPlanner
import com.vibetuned.to_reply.data.repo.ZipPlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayZipPlannerTest {

    @Test
    fun `drive download of one play folder`() {
        // A Drive "Download" of a producer-pipeline output folder.
        val plan = PlayZipPlanner.plan(listOf("proposal/The Proposal.m4b", "proposal/play.json"))
        assertEquals(listOf(ZipPlay("proposal/The Proposal.m4b", "proposal/play.json")), plan.plays)
        assertEquals(emptyList<String>(), plan.unpaired)
    }

    @Test
    fun `flat zip with one play`() {
        val plan = PlayZipPlanner.plan(listOf("The Proposal.m4b", "play.json"))
        assertEquals(listOf(ZipPlay("The Proposal.m4b", "play.json")), plan.plays)
    }

    @Test
    fun `one folder per play makes one play each, in natural order`() {
        val plan = PlayZipPlanner.plan(
            listOf(
                "plays/Act 10/x.m4b", "plays/Act 10/play.json",
                "plays/Act 2/y.m4b", "plays/Act 2/play.json",
                "plays/Act 1/z.m4b", "plays/Act 1/play.json"
            )
        )
        assertEquals(
            listOf("plays/Act 1/z.m4b", "plays/Act 2/y.m4b", "plays/Act 10/x.m4b"),
            plan.plays.map { it.audio }
        )
        assertEquals("plays/Act 2/play.json", plan.plays[1].script)
    }

    @Test
    fun `several plays in one folder pair by name`() {
        val plan = PlayZipPlanner.plan(listOf("Trifles.m4b", "Angel.m4b", "Angel.json", "Trifles.json"))
        assertEquals(
            listOf(ZipPlay("Angel.m4b", "Angel.json"), ZipPlay("Trifles.m4b", "Trifles.json")),
            plan.plays
        )
    }

    @Test
    fun `a recording with no script is reported, not guessed`() {
        val plan = PlayZipPlanner.plan(listOf("A.m4b", "B.m4b", "x.json", "y.json"))
        assertEquals(emptyList<ZipPlay>(), plan.plays)
        assertEquals(listOf("A.m4b", "B.m4b"), plan.unpaired)
    }

    @Test
    fun `the one recording left over takes play json`() {
        val plan = PlayZipPlanner.plan(listOf("A.m4b", "A.json", "B.m4b", "notes.json", "play.json"))
        assertEquals(listOf(ZipPlay("A.m4b", "A.json"), ZipPlay("B.m4b", "play.json")), plan.plays)
    }

    @Test
    fun `a script alone is not a play`() {
        val plan = PlayZipPlanner.plan(listOf("play.json"))
        assertEquals(emptyList<ZipPlay>(), plan.plays)
        assertEquals(emptyList<String>(), plan.unpaired)
    }

    @Test
    fun `mac resource forks and hidden files are skipped`() {
        assertFalse(PlayZipPlanner.isCandidate("__MACOSX/play/._The Proposal.m4b"))
        assertFalse(PlayZipPlanner.isCandidate("play/._play.json"))
        assertFalse(PlayZipPlanner.isCandidate("play/.DS_Store"))
        assertFalse(PlayZipPlanner.isCandidate("play/"))
        assertFalse(PlayZipPlanner.isCandidate("play/cover.jpg"))
        assertTrue(PlayZipPlanner.isCandidate("play/Play.M4B"))
        val plan = PlayZipPlanner.plan(listOf("__MACOSX/p/._p.m4b", "__MACOSX/p/._play.json", "p/p.m4b", "p/play.json"))
        assertEquals(listOf(ZipPlay("p/p.m4b", "p/play.json")), plan.plays)
    }

    @Test
    fun `windows separators are understood`() {
        val plan = PlayZipPlanner.plan(listOf("plays\\one\\a.m4b", "plays\\one\\play.json"))
        assertEquals(listOf(ZipPlay("plays/one/a.m4b", "plays/one/play.json")), plan.plays)
    }

    @Test
    fun `a shared play's own zip imports back`() {
        // The layout prepareShare writes.
        val plan = PlayZipPlanner.plan(listOf("The Proposal/The Proposal.m4b", "The Proposal/play.json"))
        assertEquals(listOf(ZipPlay("The Proposal/The Proposal.m4b", "The Proposal/play.json")), plan.plays)
    }

    @Test
    fun `natural order compares numbers as numbers`() {
        val sorted = listOf("Act 10", "act 2", "Act 1", "Act 02b").sortedWith(NaturalOrder)
        assertEquals(listOf("Act 1", "act 2", "Act 02b", "Act 10"), sorted)
    }
}
