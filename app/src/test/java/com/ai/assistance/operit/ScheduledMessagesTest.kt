package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import org.junit.Assert.*
import org.junit.Test

class ScheduledMessagesTest {
    private val created=1_000_000L
    private fun item(
        mode:String=ScheduledMessages.MODE_TEXT,
        content:String="晚上好，记得喝水。",
        configId:String="",
        configVersion:Long=0,
        dueAt:Long=created+3_600_000L,
        state:String=ScheduledMessages.STATE_PENDING
    )=ScheduledMessage("s1","role","space","chat",configId,configVersion,dueAt,mode,content,state,"assistant",createdAt=created)

    @Test fun textModeNeedsNoModelConfig() {
        ScheduledMessages.validate(item())
    }

    @Test fun generateModeRequiresConfigSnapshot() {
        assertThrows(IllegalArgumentException::class.java){ScheduledMessages.validate(item(mode=ScheduledMessages.MODE_GENERATE))}
        ScheduledMessages.validate(item(mode=ScheduledMessages.MODE_GENERATE,configId="cfg",configVersion=2))
    }

    @Test fun contentAndLeadTimeAreBounded() {
        assertThrows(IllegalArgumentException::class.java){ScheduledMessages.validate(item(content="x".repeat(ScheduledMessages.MAX_TEXT+1)))}
        assertThrows(IllegalArgumentException::class.java){ScheduledMessages.validate(item(content="  "))}
        assertThrows(IllegalArgumentException::class.java){ScheduledMessages.validate(item(dueAt=created+ScheduledMessages.MAX_LEAD_MS+1))}
    }

    @Test fun unknownModeOrStateIsRejected() {
        assertThrows(IllegalArgumentException::class.java){ScheduledMessages.validate(item(mode="broadcast"))}
        assertThrows(IllegalArgumentException::class.java){ScheduledMessages.validate(item(state="SENT"))}
    }

    @Test fun pendingListKeepsOnlyPendingInDueOrder() {
        val later=item(dueAt=created+2000L)
        val earlier=item(dueAt=created+1000L)
        val done=item(dueAt=created+500L,state=ScheduledMessages.STATE_COMPLETE)
        assertEquals(listOf(earlier,later),ScheduledMessages.pending(listOf(later,earlier,done)))
    }
}
