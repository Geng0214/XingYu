package com.ai.assistance.operit

import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.ui.main.*
import org.junit.Assert.*
import org.junit.Test

class HomeUiTest {
    private fun changed(row:HomeRow,id:String,a:HomeAction):HomeReceipt {
        val change=row.value().apply(row.scope(),row.version,row.updatedAt,a)
        return HomeReceipt(row.changed(change.state),HomeCommandRow(id,row.id,row.version,HomeActionCodec.encode(a),change.description,row.updatedAt),false)
    }
    @Test fun aLateReceiptCannotReplaceNewerSharedWorldState() {
        val first=HomeRow.from(HomeState.create("家园",100,listOf(HomeTestData.a)))
        val planted=changed(first,"plant",HomeAction.Plant(0,HomeCrop.CARROT))
        val watered=changed(planted.home,"water",HomeAction.Water(0))
        val pending=HomePending("plant",first.scope(),1,planted.command.action())
        val ui=HomeUi("page",pending=pending).received(HomeView(watered.home,listOf(watered.command,planted.command)))
        val delivered=ui.completed(planted)
        assertEquals(watered.home.version,delivered.view!!.home.version);assertNull(delivered.pending)
        assertTrue(delivered.view.home.value().plots[0]!!.watered)
    }
    @Test fun transferCapacityUsesTheChosenResident() {
        val (row,_)=HomeTestData.garden();val h=row.value()
        assertEquals(3,homeTransferLimit(h,HomeItem.CARROT,true,"role-b"))
        assertEquals(0,homeTransferLimit(h,HomeItem.CARROT,true,"missing"))
        assertEquals(0,homeTransferLimit(h,HomeItem.CARROT_SEED,true,"role-b"))
    }
}
