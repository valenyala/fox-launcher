package dev.foxlauncher.core.data.settings

import kotlin.test.assertEquals
import org.junit.Test

class IdListCodecTest {

    @Test
    fun idsSurviveARoundTrip() {
        val ids = listOf(3L, 1L, 2L, 9_000_000_000L)

        assertEquals(ids, IdListCodec.decode(IdListCodec.encode(ids)))
    }

    @Test
    fun nothingStoredIsNoIds() {
        assertEquals(emptyList(), IdListCodec.decode(null))
        assertEquals(emptyList(), IdListCodec.decode(""))
    }

    @Test
    fun unreadablePartsAreSkipped() {
        assertEquals(listOf(1L, 3L), IdListCodec.decode("1,x,,3"))
    }
}
