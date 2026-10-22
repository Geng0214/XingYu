package com.ai.assistance.operit

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.data.backup.*
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.security.CredentialVault
import kotlinx.coroutines.*
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom
import java.util.UUID
import java.util.ArrayDeque

class HomeRewardAndroidTest {
    private suspend fun rejected(block:suspend ()->Unit){try{block();fail("Expected rejection")}catch(_:ChatFailure){}}
    private fun complete():AdventureGame {
        var game=AdventureGame.start()
        for(id in listOf("wood_a","wood_b","rope","bridge","forest_path","glass","ember","workbench","scarf","tower_path","gate_switch","crystal","forest_return","village_path","keeper","lighthouse")) {
            val target=AdventureWorld.objects.single {it.id==id}.position
            val queue=ArrayDeque<Pair<AdventurePosition,List<AdventureDirection>>>();queue.add(game.position to emptyList())
            val seen=mutableSetOf(game.position);var path:List<AdventureDirection>?=null
            while(queue.isNotEmpty()) {
                val (position,steps)=queue.removeFirst()
                if(position.distance(target)<=1){path=steps;break}
                AdventureDirection.entries.forEach {direction->val next=position.step(direction)
                    if(seen.add(next)&&AdventureWorld.groundOpen(next,game.flags)&&AdventureWorld.objects.none {it.position==next&&it.blocking&&game.active(it)})queue.add(next to steps+direction)
                }
            }
            requireNotNull(path).forEach {game=game.apply(AdventureAction.Move(it))}
            game=game.apply(AdventureAction.Interact(id))
        }
        return game
    }
    @Test fun encryptedClaimsAreAtomicAcrossTwentyRacesDeletionRestartAndBackup()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary("sqlcipher");val name="reward-test-${UUID.randomUUID()}.db"
        val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        fun open()=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
        var db=open()
        try {
            db.dao().saveConfig(ModelConfigData("cfg","test","openai","https://example.invalid/v1/chat/completions","model",60,"ref"))
            val roles=RoleRepository(db);val a=roles.saveRole(null,"同名","A 人设","","cfg");val b=roles.saveRole(null,"同名","B 人设","","cfg")
            val chat=roles.createChat(a.scope(),"cfg");val other=roles.createChat(b.scope(),"cfg")
            var homes=HomeRepository(db){100};val initial=homes.ensure()
            val completed=complete();val sources=mutableListOf<PlaySessionRow>();val previews=mutableListOf<HomeRewardPreview>()
            repeat(20){i->
                val scope=PlayAccessScope(a.scope(),chat.id,"source-$i")
                // Seed a fully replay-valid source without generating network dialogue.
                val row=PlaySessionRow.from(PlaySession(scope,"冒险 $i",AdventurePlayState(completed),completed.events.size+1L),100)
                db.play().insert(row);sources+=row
                val preview=homes.rewardPreview(scope,row.version,HomeAdventureReward.LIGHTHOUSE);previews+=preview
                val receipts=coroutineScope {List(2){n->async(Dispatchers.Default){homes.claimReward(preview,"race-$i-$n")}}.awaitAll()}
                assertEquals(1,receipts.count {!it.repeated});assertEquals(1,receipts.count {it.repeated})
                assertEquals(1,receipts.map {it.command.commandId}.distinct().size)
                assertEquals(i+1,homes.current()!!.home.value().inventory[HomeItem.HARBOR_BADGE])
                rejected {homes.claimReward(preview.copy(source=scope.copy(role=b.scope(),chatId=other.id)),"foreign-$i")}
                rejected {homes.apply(preview.home,preview.homeVersion,"generic-$i",preview.action)}
            }
            val before=homes.current()!!.home
            val pin=homes.rewardPreview(sources.last().scope(),sources.last().version,HomeAdventureReward.SCARF)
            val renamed=homes.apply(before.scope(),before.version,"rename",HomeAction.Rename("CHANGED-REWARD-HOME")).home
            rejected {homes.claimReward(pin,"stale")};assertEquals(renamed.version,homes.current()!!.home.version)
            assertNull(db.home().command("stale"))
            homes.apply(renamed.scope(),renamed.version,"discard",HomeAction.Discard(HomeItem.HARBOR_BADGE,20))
            assertTrue(homes.claimReward(previews.first(),"new-id-same-origin").repeated)
            assertNull(homes.current()!!.home.value().inventory[HomeItem.HARBOR_BADGE])
            PlayRepository(db).delete(sources.first().scope(),sources.first().version)
            db.close();db=open();homes=HomeRepository(db){100}
            assertTrue(homes.claimReward(previews.first(),"retry-after-deletion").repeated)
            val backup=BackupRepository(db,CredentialVault(context));val snapshot=backup.snapshot(false,{})
            val restored=backup.restore(snapshot,{},HomeRestoreMode.JOIN_CURRENT)
            assertEquals(0,restored.homes);assertEquals(19,restored.playSessions)
            assertEquals(24,homes.history(initial.scope()).recent.size)
            assertNull(homes.current()!!.home.value().inventory[HomeItem.HARBOR_BADGE])
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            for(suffix in listOf("","-wal","-shm")) {
                val file=java.io.File(context.getDatabasePath(name).path+suffix)
                if(file.exists())assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("CHANGED-REWARD-HOME"))
            }
        }finally {db.close();context.deleteDatabase(name);key.fill(0)}
    }
}
