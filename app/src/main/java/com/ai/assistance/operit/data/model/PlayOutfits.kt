package com.ai.assistance.operit.data.model

import org.json.JSONObject

/** Value-only activity snapshot: historical outfits do not depend on mutable home inventory. */
data class PlayOutfits(val user:HomeItem?,val role:HomeItem?) {
    init {listOfNotNull(user,role).forEach {require(it.group==HomeItemGroup.CLOTHING)}}
    fun encode()=JSONObject().put("user",user?.name?:JSONObject.NULL).put("role",role?.name?:JSONObject.NULL)
    fun observation()=JSONObject().put("user",user?.title?:JSONObject.NULL).put("role",role?.title?:JSONObject.NULL)
    fun description()="本次穿搭 · 我：${user?.title?:"未选择"} · 角色：${role?.title?:"未选择"}"
    companion object {
        fun capture(owner:RoleAccessScope,home:HomeState?):PlayOutfits {
            val resident=home?.residents?.get(owner.roleId)
            if(resident!=null&&resident.role!=owner)throw ChatFailure("ACCESS")
            return PlayOutfits(home?.userOutfit,resident?.outfit)
        }
        internal fun decode(value:JSONObject):PlayOutfits {
            PlayJson.keys(value,"user","role")
            fun item(key:String):HomeItem?=if(value.get(key)===JSONObject.NULL)null else HomeItem.valueOf(PlayJson.string(value,key))
            return PlayOutfits(item("user"),item("role"))
        }
    }
}
