package com.ai.assistance.operit.data.backup

import android.database.Cursor
import android.graphics.BitmapFactory
import androidx.room.withTransaction
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.ChatFailure
import com.ai.assistance.operit.data.security.CredentialVault
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.UUID

enum class HomeRestoreMode { IMPORT_WORLD, JOIN_CURRENT }

internal class BackupRepository(private val db:AppDatabase,private val vault:CredentialVault) {
    suspend fun snapshot(includeCredentials:Boolean,checkAccess:()->Unit):BackupSnapshot=db.withTransaction {
        checkAccess();var count=0;var size=0L
        val tables=BackupSchema.tables.map {table->
            val rows=mutableListOf<BackupRow>()
            val columns=table.columns.joinToString(","){"`${it.name}`"}
            db.openHelper.readableDatabase.query("SELECT $columns FROM `${table.name}`").use {cursor->
                while(cursor.moveToNext()) {
                    checkAccess();if(++count>BackupFormat.MAX_ROWS)throw ChatFailure("BACKUP_LIMIT")
                    val values=table.columns.mapIndexed {i,c->
                        if(cursor.isNull(i)){require(c.nullable);null}else when(c.type){
                            BackupType.TEXT->{require(cursor.getType(i)==Cursor.FIELD_TYPE_STRING);cursor.getString(i).also {size+=it.toByteArray().size+5}}
                            BackupType.INTEGER->{require(cursor.getType(i)==Cursor.FIELD_TYPE_INTEGER);size+=9;cursor.getLong(i)}
                            BackupType.REAL->{require(cursor.getType(i) in setOf(Cursor.FIELD_TYPE_FLOAT,Cursor.FIELD_TYPE_INTEGER));size+=9;cursor.getDouble(i)}
                            BackupType.BLOB->{require(cursor.getType(i)==Cursor.FIELD_TYPE_BLOB);cursor.getBlob(i).also {size+=it.size+5}}
                        }
                    }
                    if(size>BackupFormat.MAX_BYTES)throw ChatFailure("BACKUP_LIMIT")
                    rows+=BackupRow(table,values)
                }
            };rows
        }
        val snapshot=BackupSnapshot(System.currentTimeMillis(),tables,false,emptyList())
        BackupValidation.validate(snapshot)
        val credentials=if(includeCredentials)BackupValidation.bindings(snapshot).map {(ref,address)->
            checkAccess();BackupCredential(ref,address,vault.read(ref,address))
        }else emptyList()
        snapshot.copy(includesCredentials=includeCredentials,credentials=credentials).also {checkAccess()}
    }

    fun validateImages(snapshot:BackupSnapshot) {
        val images=snapshot.rows("role_portraits").map {it["image"] as ByteArray to 1600}+snapshot.rows("role_appearance").mapNotNull {row->(row["wallpaper"] as ByteArray?)?.let {it to 1600}}+
            snapshot.rows("message_attachments").map {it["data"] as ByteArray to com.ai.assistance.operit.data.model.MessageImages.MAX_EDGE}+
            snapshot.rows("sticker_assets").map {it["data"] as ByteArray to com.ai.assistance.operit.data.model.StickerAssets.MAX_EDGE}+
            snapshot.rows("message_stickers").map {it["data"] as ByteArray to com.ai.assistance.operit.data.model.StickerAssets.MAX_EDGE}+
            snapshot.rows("generated_images").mapNotNull {row->(row["data"] as ByteArray?)?.let {it to com.ai.assistance.operit.data.model.ImageGenerations.MAX_EDGE}}
        images.forEach {(bytes,maxEdge)->
            val options=BitmapFactory.Options().apply {inJustDecodeBounds=true}
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
            if(options.outWidth !in 1..maxEdge||options.outHeight !in 1..maxEdge||options.outMimeType!="image/jpeg")throw ChatFailure("BACKUP_FORMAT")
            val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size)?:throw ChatFailure("BACKUP_FORMAT")
            bitmap.recycle()
        }
    }

    suspend fun restore(snapshot:BackupSnapshot,checkAccess:()->Unit,homeMode:HomeRestoreMode=HomeRestoreMode.IMPORT_WORLD):BackupSummary {
        BackupValidation.validate(snapshot);validateImages(snapshot);checkAccess()
        val selected=when(homeMode) {
            HomeRestoreMode.IMPORT_WORLD->snapshot
            HomeRestoreMode.JOIN_CURRENT->snapshot.copy(tables=snapshot.tables.mapIndexed {i,rows->
                if(BackupSchema.tables[i].name in setOf("home_world","home_events"))emptyList()else rows})
        }
        // Complete this small commit section independently of coroutine delivery cancellation.
        // Otherwise cancellation after SQL commit could delete the newly referenced credential files.
        return withContext(NonCancellable) {
            val staged=mutableListOf<String>();var committed=false
            try {
                val available=snapshot.credentials.associateBy {it.ref}
                val refs=BackupValidation.bindings(snapshot).mapValues {(old,address)->
                    checkAccess()
                    val credential=available[old]
                    if(credential==null)UUID.randomUUID().toString()else vault.save(credential.key,address).also {staged+=it}
                }
                val imported=BackupRemap.create(selected,refs,System.currentTimeMillis())
                BackupValidation.validate(imported)
                db.withTransaction {
                    val sql=db.openHelper.writableDatabase
                    if(imported.rows("home_world").isNotEmpty()&&db.home().home()!=null)throw ChatFailure("HOME_BACKUP_EXISTS")
                    sql.query("SELECT COUNT(*) FROM group_rooms").use {cursor->
                        check(cursor.moveToFirst())
                        if(cursor.getLong(0)+imported.rows("group_rooms").size>128)throw ChatFailure("GROUP_LIMIT")
                    }
                    imported.tables.forEachIndexed {i,rows->
                        val table=BackupSchema.tables[i]
                        val columns=table.columns.joinToString(","){"`${it.name}`"}
                        val placeholders=table.columns.joinToString(","){"?"}
                        rows.forEach {row->checkAccess();sql.execSQL("INSERT INTO `${table.name}` ($columns) VALUES ($placeholders)",row.values.toTypedArray())}
                    }
                    db.dao().allRoles().forEach {role->com.ai.assistance.operit.data.db.HomeRepository(db).join(role.scope())}
                    sql.query("PRAGMA foreign_key_check").use {if(it.moveToFirst())throw ChatFailure("BACKUP_FORMAT")}
                    checkAccess()
                }
                committed=true
                db.invalidationTracker.refreshVersionsAsync()
                imported.summary()
            } finally {
                if(!committed)staged.forEach(vault::removeCredential)
            }
        }
    }
}
