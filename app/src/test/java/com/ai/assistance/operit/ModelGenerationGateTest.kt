package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.ModelGenerationGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelGenerationGateTest {
    @Test fun onlyTheOwningRequestCanReleaseTheProcessGate() {
        val first="gate-first"
        val second="gate-second"
        assertTrue(ModelGenerationGate.acquire(first))
        try {
            assertTrue(ModelGenerationGate.busy())
            assertFalse(ModelGenerationGate.acquire(second))
            ModelGenerationGate.release(second)
            assertTrue(ModelGenerationGate.busy())
        } finally {
            ModelGenerationGate.release(first)
        }
        assertFalse(ModelGenerationGate.busy())
        assertTrue(ModelGenerationGate.acquire(second))
        ModelGenerationGate.release(second)
    }
}
