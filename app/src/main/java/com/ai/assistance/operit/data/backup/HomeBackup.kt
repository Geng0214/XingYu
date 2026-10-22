package com.ai.assistance.operit.data.backup

import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*

internal object HomeBackup {
    fun home(r:BackupRow)=HomeRow(r.text("id"),r.text("initialJson"),r.text("stateJson"),r.number("version"),r.number("createdAt"),r.number("updatedAt"))
    fun command(r:BackupRow)=HomeCommandRow(r.text("commandId"),r.text("homeId"),r.number("beforeVersion"),r.text("actionJson"),r.text("description"),r.number("createdAt"))
    fun validate(s:BackupSnapshot)=try {
        val roles=s.rows("roles").associate {it.text("id") to RoleAccessScope(it.text("id"),it.text("spaceId"))}
        val homes=s.rows("home_world").map(::home);require(homes.size<=1)
        val commands=s.rows("home_events").map(::command)
        val scopes=homes.map {it.scope()}.toSet();require(commands.all {it.scope() in scopes})
        val activities=s.rows("play_sessions").associateBy {it.text("id")}
        homes.forEach {row->
            val saved=row.value()
            require(saved.residents.values.all {roles[it.role.roleId]==it.role})
            require(saved.residents.keys==roles.keys)
            HomeStorageValidation.validate(row,commands)
        }
        commands.forEach {command->(command.action() as? HomeAction.AdventureReward)?.let {claim->
            activities[claim.sourceId]?.let {source->
                require(source.text("roleId")==claim.sourceRole.roleId&&source.text("spaceId")==claim.sourceRole.spaceId)
                val game=(PlayStates.decode(source.text("stateJson")) as? AdventurePlayState)?.game?:error("Invalid reward source")
                require(claim.proof.game.isPrefixOf(HomeAdventureProof.from(game).game))
            }
        }}
    }catch(_:Exception){throw ChatFailure("BACKUP_FORMAT")}
}
