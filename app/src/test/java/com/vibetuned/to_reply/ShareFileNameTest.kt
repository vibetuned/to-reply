package com.vibetuned.to_reply

import com.vibetuned.to_reply.data.repo.shareFileName
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareFileNameTest {
    @Test
    fun `plain titles are kept`() {
        assertEquals("The Proposal", shareFileName("The Proposal"))
    }

    @Test
    fun `characters file systems reject become spaces`() {
        assertEquals("Trifles A Play in One Act", shareFileName("Trifles: A Play in One Act"))
        assertEquals("AC DC Who What", shareFileName("AC/DC\\\\Who?\"What\""))
    }

    @Test
    fun `leading and trailing dots and spaces go`() {
        assertEquals("Hidden", shareFileName("  ..Hidden.. "))
    }

    @Test
    fun `very long titles are cut`() {
        assertEquals(120, shareFileName("x".repeat(400)).length)
    }

    @Test
    fun `nothing usable falls back to Play`() {
        assertEquals("Play", shareFileName("///"))
        assertEquals("Play", shareFileName(""))
    }
}
