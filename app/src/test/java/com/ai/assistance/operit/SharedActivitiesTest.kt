package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import org.junit.Assert.*
import org.junit.Test

class SharedActivitiesTest {
    private val text="甲🙂乙\n第二段"
    private val book=ReadingDocument("book","role","space","一本书","text/plain",text,
        ReadingDocuments.encodeChapters(listOf(ReadingChapter(0,"开篇",0,text.length))),sha256=ReadingDocuments.sha256(text),createdAt=1)
    private val activity=SharedActivity("activity","role","space","chat","book",createdAt=2)
    private fun invalid(item:SharedActivity) {try {SharedActivities.validate(item,book);fail()}catch(_:IllegalArgumentException){}}

    @Test fun independentActivitiesDoNotReuseTheBooksPersonalProgress() {
        val next=activity.copy(id="other",chatId="other-chat",offset=3)
        SharedActivities.validate(activity,book);SharedActivities.validate(next,book)
        assertEquals(0,activity.offset);assertEquals(0,book.currentOffset)
        assertNotEquals(activity.id,next.id)
    }
    @Test fun crossRoleSpaceAndDocumentReferencesAreRejected() {
        invalid(activity.copy(roleId="other"));invalid(activity.copy(spaceId="other"));invalid(activity.copy(readingDocumentId="other"))
    }
    @Test fun invalidPositionsSurrogateSplitAndOversizedNotesAreRejected() {
        invalid(activity.copy(offset=2));invalid(activity.copy(offset=text.length));invalid(activity.copy(chapter=1))
        invalid(activity.copy(note="x".repeat(SharedActivities.NOTE_LIMIT+1)));invalid(activity.copy(version=Long.MAX_VALUE))
    }
    @Test fun fiftyPauseResumeCyclesAndTerminalStateRejectUnexpectedActions() {
        var state="active"
        repeat(50){state=SharedActivities.transition(state,"pause");assertEquals("paused",state);state=SharedActivities.transition(state,"resume")}
        state=SharedActivities.transition(state,"complete");assertEquals("completed",state)
        for(action in listOf("resume","pause","complete"))try {SharedActivities.transition(state,action);fail()}catch(e:ChatFailure){assertEquals("ACTIVITY_STATE",e.code)}
        try {SharedActivities.transition("active","resume");fail()}catch(e:ChatFailure){assertEquals("ACTIVITY_STATE",e.code)}
    }
    @Test fun summaryContainsOnlyChosenActivityMetadataAndUserNote() {
        val summary=SharedActivities.summary(activity.copy(note="想聊聊这个角色",offset=3),book)
        assertTrue(summary.contains("一本书"));assertTrue(summary.contains("想聊聊这个角色"));assertTrue(summary.contains("字符位置 3"))
        assertFalse(summary.contains(text));assertFalse(summary.contains(activity.id));assertFalse(summary.contains(activity.chatId))
    }
    @Test fun backwardPagingPreservesEmojiBoundary() {
        val long="a".repeat(3999)+"🙂"+"b".repeat(4000)
        assertEquals(3999,ReadingDocuments.previousPageStart(long,8000,0))
    }
}
