package com.ai.assistance.operit

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.db.RoleRepository
import com.ai.assistance.operit.data.model.*
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test

class MessageExportAndroidTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun completedReplyLookupAndVerifiedWriteStayRoleScoped()=runBlocking {
        System.loadLibrary("sqlcipher")
        val name="message-export-${UUID.randomUUID()}.db";val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        val db=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
        val target=context.filesDir.resolve("message-export-${UUID.randomUUID()}.md")
        try {
            db.dao().saveConfig(ModelConfigData("cfg","test","OpenAI 兼容","https://example.invalid/v1/chat/completions","model",60,"ref"))
            val roles=RoleRepository(db);val role=roles.saveRole(null,"A","persona","","cfg");val chat=roles.createChat(role.scope(),"cfg")
            val message=MessageEntity("assistant",chat.id,"request","assistant","# 私密回复\n中文🙂",MessageState.COMPLETE,100)
            db.dao().insertMessages(listOf(message))
            val scoped=requireNotNull(db.dao().message(role.id,role.spaceId,chat.id,message.id))
            assertNull(db.dao().message(role.id,"other-space",chat.id,message.id))
            val file=MessageExports.freeze(MessageExports.prepare(role.scope(),role,chat,scoped),MessageExportFormat.MARKDOWN,"回复")
            MessageExportFiles.writeVerified(file,{target.outputStream()},{target.inputStream()})
            assertArrayEquals(file.bytes(),target.readBytes())
        } finally {target.delete();db.close();context.deleteDatabase(name)}
    }
}
