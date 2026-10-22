package com.ai.assistance.operit.data.model

/** User-authored identity belongs to exactly one role space, never to a shared model configuration. */
object UserPersona {
    const val NAME_LIMIT=40
    const val DESCRIPTION_LIMIT=4000
    fun validate(name:String,description:String,enabled:Boolean) {
        require(name.length<=NAME_LIMIT&&name.none(Char::isISOControl)&&description.length<=DESCRIPTION_LIMIT)
        require(!enabled||name.isNotBlank())
    }
    fun instruction(role:RoleEntity):String {
        validate(role.userName,role.userPersona,role.personaEnabled)
        if(!role.personaEnabled)return ""
        return "\n\n当前聊天用户的设定（只适用于此角色空间）：\n称呼：${role.userName}\n用户设定：${role.userPersona}"
    }
}
