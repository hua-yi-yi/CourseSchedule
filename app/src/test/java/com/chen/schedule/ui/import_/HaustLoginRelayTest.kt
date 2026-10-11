package com.chen.schedule.ui.import_

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HaustLoginRelayTest {
    @Test
    fun `capture keeps last valid pair and clear removes it`() {
        val relay = HaustLoginRelay()
        assertNull(relay.peek())
        relay.capture("2023xxxx", "p1")
        assertEquals("2023xxxx", relay.peek()?.username)
        relay.capture("2023xxxx", "p2")
        assertEquals("p2", relay.peek()?.password)
        relay.clear()
        assertNull(relay.peek())
    }

    @Test
    fun `blank username or password is ignored`() {
        val relay = HaustLoginRelay()
        relay.capture("", "p")
        assertNull(relay.peek())
        relay.capture("u", "   ")
        assertNull(relay.peek())
        relay.capture("u", "p")
        assertEquals("u", relay.peek()?.username)
        // 之前的有效凭据不会被空输入覆盖
        relay.capture("", "")
        assertEquals("p", relay.peek()?.password)
    }
}
