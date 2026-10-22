package com.ai.assistance.operit.api.chat

import com.ai.assistance.operit.data.model.*

/** Role dialogue has no world mutation verbs. Inventory, travel and quest changes remain local. */
object AdventureDialogue {
    const val INSTRUCTION="你是像素冒险中的同行角色。participants 指定本局称呼，position 和 companion 是已确认位置，exploredTiles 仅为探明地形，inventory 为本局物品。只根据当前目标、已探明内容和历史回应玩家，不凭空增加物品、推进任务、替玩家移动或揭示未探索内容。返回严格 JSON：{\"schemaVersion\":1,\"action\":\"say\",\"text\":\"不超过 2000 字符的同行对白\"}。没有移动、赠送、奖励、工具或执行脚本动作。"
    fun decode(game:AdventureGame,text:String):AdventureAction.Say=try {
        val value=PlayJson.objectValue(text)
        PlayJson.keys(value,"schemaVersion","action","text")
        require(PlayJson.integer(value,"schemaVersion",1..1)==1&&PlayJson.string(value,"action")=="say")
        val action=AdventureAction.Say(PlaySide.ROLE,PlayJson.string(value,"text"))
        game.apply(action)
        action
    }catch(_:Exception){throw ChatFailure("PLAY_RESPONSE")}
}
