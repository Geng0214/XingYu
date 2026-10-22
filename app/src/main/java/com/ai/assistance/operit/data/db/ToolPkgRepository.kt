package com.ai.assistance.operit.data.db

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ai.assistance.operit.core.tools.*
import com.ai.assistance.operit.core.tools.javascript.QuickJsRunner
import com.ai.assistance.operit.core.tools.packTool.*
import com.ai.assistance.operit.data.model.*
import java.util.UUID
import kotlinx.coroutines.flow.Flow

object ToolPkgAddress {
    private const val PREFIX="toolpkg:sha256:"
    fun isLocal(address:String)=address.startsWith(PREFIX)
    fun fromHash(hash:String):String {require(Regex("[a-f0-9]{64}").matches(hash));return PREFIX+hash}
    fun hash(address:String):String {require(isLocal(address));val value=address.removePrefix(PREFIX);require(fromHash(value)==address);return value}
}

@Entity(tableName="toolpkg_packages",foreignKeys=[ForeignKey(entity=McpExtension::class,parentColumns=["id"],childColumns=["extensionId"],onDelete=ForeignKey.CASCADE)])
data class ToolPkgRecord(@PrimaryKey val extensionId:String,val packageId:String,val packageVersion:String,val apiVersion:String,
    val sha256:String,val archive:ByteArray,val installedAt:Long)
data class ToolPkgInfo(val extensionId:String,val packageId:String,val packageVersion:String,val apiVersion:String,val sha256:String,val installedAt:Long)

@Dao interface ToolPkgDao {
    @Insert suspend fun insert(record:ToolPkgRecord)
    @Query("SELECT * FROM toolpkg_packages WHERE extensionId=:id") suspend fun read(id:String):ToolPkgRecord?
    @Query("SELECT extensionId,packageId,packageVersion,apiVersion,sha256,installedAt FROM toolpkg_packages ORDER BY installedAt,extensionId")
    fun list():Flow<List<ToolPkgInfo>>
}

class ToolPkgRepository(private val db:AppDatabase) {
    // Registration validation happens only after the user confirms the frozen import preview.
    suspend fun install(preview:ToolPkgBundle,validate:suspend (ToolPkgBundle)->Unit):McpExtension {
        val bundle=ToolPkgCodec.decode(preview.archive())
        require(bundle.sha256==preview.sha256)
        validate(bundle)
        return db.withTransaction {
            val extension=McpExtension(UUID.randomUUID().toString(),bundle.name,ToolPkgAddress.fromHash(bundle.sha256),null,10_000,false,1)
            db.extensions().saveExtension(extension)
            db.toolpkgs().insert(ToolPkgRecord(extension.id,bundle.packageId,bundle.packageVersion,bundle.apiVersion,bundle.sha256,bundle.archive(),System.currentTimeMillis()))
            db.extensions().insertTools(bundle.tools.map {ExtensionTool(extension.id,it.name,it.definition.description,it.definition.inputSchema)})
            extension
        }
    }
    suspend fun load(permission:ToolPermission):ToolPkgBundle=db.withTransaction {
        require(permission.credentialRef==null)
        val extension=db.extensions().extension(permission.extensionId)?:throw ToolFailure("ACCESS")
        if(!extension.enabled||extension.version!=permission.configVersion||extension.endpoint!=permission.endpoint)throw ToolFailure("ACCESS")
        val record=db.toolpkgs().read(extension.id)?:throw ToolFailure("ACCESS")
        if(record.sha256!=ToolPkgAddress.hash(permission.endpoint)||ToolPkgCodec.hash(record.archive)!=record.sha256)throw ToolFailure("PKG_INTEGRITY")
        ToolPkgCodec.decode(record.archive)
    }
    companion object {
        suspend fun validateRegistration(bundle:ToolPkgBundle) {
            QuickJsRunner().execute(bundle.sources,bundle.main,"registerToolPkg","{}",3000,registrationOnly=true)
        }
    }
}

object ToolPkgMigration:Migration(5,6) {
    override fun migrate(db:SupportSQLiteDatabase) {
        db.query("PRAGMA foreign_key_check").use {check(!it.moveToFirst()){ "Invalid associations before ToolPkg upgrade" }}
        db.query("SELECT COUNT(*) FROM role_tool_grants g LEFT JOIN roles r ON r.id=g.roleId AND r.spaceId=g.spaceId WHERE r.id IS NULL").use {check(it.moveToFirst()&&it.getLong(0)==0L)}
        db.query("SELECT COUNT(*) FROM request_tool_permissions p LEFT JOIN roles r ON r.id=p.roleId AND r.spaceId=p.spaceId LEFT JOIN chats c ON c.id=p.chatId AND c.roleId=r.id LEFT JOIN messages m ON m.id=p.assistantId AND m.chatId=c.id AND m.requestId=p.requestId AND m.kind='assistant' WHERE r.id IS NULL OR c.id IS NULL OR m.id IS NULL").use {check(it.moveToFirst()&&it.getLong(0)==0L)}
        db.query("SELECT COUNT(*) FROM tool_invocations i LEFT JOIN roles r ON r.id=i.roleId AND r.spaceId=i.spaceId LEFT JOIN chats c ON c.id=i.chatId AND c.roleId=r.id LEFT JOIN messages m ON m.id=i.assistantId AND m.chatId=c.id AND m.requestId=i.requestId AND m.kind='assistant' WHERE r.id IS NULL OR c.id IS NULL OR m.id IS NULL").use {check(it.moveToFirst()&&it.getLong(0)==0L)}
        db.execSQL("CREATE TABLE IF NOT EXISTS toolpkg_packages (extensionId TEXT NOT NULL, packageId TEXT NOT NULL, packageVersion TEXT NOT NULL, apiVersion TEXT NOT NULL, sha256 TEXT NOT NULL, archive BLOB NOT NULL, installedAt INTEGER NOT NULL, PRIMARY KEY(extensionId), FOREIGN KEY(extensionId) REFERENCES mcp_extensions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
    }
}
