package com.ai.assistance.operit

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.api.chat.RoomRequestStore
import com.ai.assistance.operit.core.tools.*
import com.ai.assistance.operit.core.tools.packTool.*
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.security.Credentials
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.*

/** Real SQLCipher + Kotlin repositories + Android QuickJS JNI. Requires a connected ARM64 device. */
class ToolPkgAndroidTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val marker="synthetic-package-private-marker-中文🙂"
    private val credentials=object:Credentials {override fun read(ref:String,endpoint:String):String=error("Local tools cannot read a credential")}
    private fun bundle():ToolPkgBundle {
        val root="toolpkg/time/"
        val files=linkedMapOf("manifest.json" to context.assets.open(root+"manifest.json").use {it.readBytes()},
            "main.js" to "exports.registerToolPkg=()=>{};".toByteArray(),
            "packages/time.js" to ("/* METADATA {\"tools\":[{\"name\":\"echo\",\"description\":\"$marker\",\"parameters\":[{\"name\":\"marker\",\"type\":\"string\",\"description\":\"synthetic\",\"required\":true}]}]} */\nexports.echo=p=>({marker:p.marker,host:typeof fetch});").toByteArray())
        // STORED makes the marker visible in the package bytes: compression must not hide a plaintext DB regression.
        val out=ByteArrayOutputStream()
        ZipOutputStream(out).use {zip->files.forEach {(name,bytes)->
            zip.putNextEntry(ZipEntry(name).apply {method=ZipEntry.STORED;size=bytes.size.toLong();compressedSize=size;crc=CRC32().apply {update(bytes)}.value})
            zip.write(bytes);zip.closeEntry()
        }}
        assertTrue(out.toByteArray().toString(Charsets.UTF_8).contains(marker))
        return ToolPkgCodec.decode(out.toByteArray())
    }
    private fun fixture(block:suspend (AppDatabase,String)->Unit)=runBlocking {
        System.loadLibrary("sqlcipher")
        val name="toolpkg-${UUID.randomUUID()}.db"
        val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        val db=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key)).build()
        try {withTimeout(120_000){block(db,name)}}finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun duplicateInstallIsDisabledIsolatedEncryptedAndTransactional()=fixture {db,name->
        val repo=ToolPkgRepository(db);val preview=bundle()
        val first=repo.install(preview,ToolPkgRepository::validateRegistration)
        val second=repo.install(preview,ToolPkgRepository::validateRegistration)
        assertNotEquals(first.id,second.id);assertFalse(first.enabled);assertFalse(second.enabled)
        assertNull(first.credentialRef);assertEquals(2,db.toolpkgs().list().first().size)
        try {repo.install(preview){throw ToolFailure("JS_EXECUTION")};fail()}catch(e:ToolFailure){assertEquals("JS_EXECUTION",e.code)}
        assertEquals(2,db.toolpkgs().list().first().size)
        assertEquals(preview.sha256,db.toolpkgs().read(first.id)!!.sha256)
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        for(suffix in listOf("","-wal","-shm")) {
            val file=java.io.File(context.getDatabasePath(name).path+suffix)
            if(file.exists())assertFalse(file.readBytes().toString(Charsets.UTF_8).contains(marker))
        }
        ExtensionRepository(db,credentials).delete(first.id,first.version)
        assertNull(db.toolpkgs().read(first.id));assertNotNull(db.toolpkgs().read(second.id))
    }
    @Test fun actualLocalPluginKeepsFiftyAlternatingRoleRequestsIsolated()=fixture {db,_->
        val repo=ToolPkgRepository(db);val extensions=ExtensionRepository(db,credentials);val roles=RoleRepository(db)
        var extension=repo.install(bundle(),ToolPkgRepository::validateRegistration)
        extension=extensions.setEnabled(extension.id,extension.version,true)
        db.dao().saveConfig(ModelConfigData("config","test","OpenAI 兼容","https://example.invalid/chat","test",90,"model-ref"))
        val a=roles.saveRole(null,"A","persona-A","","config");val b=roles.saveRole(null,"B","persona-B","","config")
        val ca=roles.createChat(a.scope(),"config");val cb=roles.createChat(b.scope(),"config")
        for(role in listOf(a,b))extensions.grant(role.scope(),extension.id,extension.version,"time.echo",true)
        val requests=RoomRequestStore(db);val store=RoomToolInvocationStore(db)
        val dispatcher=ToolDispatcher(store,{p->LocalToolPkgSession({repo.load(p)})},{true})
        repeat(50) {n->
            val (role,chat)=if(n%2==0)a to ca else b to cb;val other=if(n%2==0)b else a
            val request=requests.prepare(role.scope(),chat.id,"${role.name}-marker-$n",UUID.randomUUID().toString())
            val call=ToolInvocation(ToolRequestOwner(request.scope,request.chatId,request.requestId,request.assistantId),request.tools.single(),"call","{\"marker\":\"${role.name}-$n 中文🙂\"}")
            try {dispatcher.execute(call.copy(owner=call.owner.copy(scope=other.scope())));fail()}catch(e:ToolFailure){assertEquals("ACCESS",e.code)}
            val result=dispatcher.execute(call)
            val text=JSONObject(result.json).getJSONArray("content").getJSONObject(0).getString("text")
            assertEquals("${role.name}-$n 中文🙂",JSONObject(text).getString("marker"))
            assertEquals("undefined",JSONObject(text).getString("host"))
            assertTrue(db.extensions().invocations(other.id,other.spaceId,chat.id).first().isEmpty())
            requests.update(request,"ok",MessageState.COMPLETE,"",1,1)
        }
        val stale=requests.prepare(a.scope(),ca.id,"revoke",UUID.randomUUID().toString())
        extensions.grant(a.scope(),extension.id,extension.version,"time.echo",false)
        try {dispatcher.execute(ToolInvocation(ToolRequestOwner(stale.scope,stale.chatId,stale.requestId,stale.assistantId),stale.tools.single(),"revoked","{}"));fail()}
        catch(e:ToolFailure){assertEquals("ACCESS",e.code)}
        assertNotNull(db.dao().config("config"))
    }
}
