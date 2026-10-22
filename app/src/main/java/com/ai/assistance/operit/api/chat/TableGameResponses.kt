package com.ai.assistance.operit.api.chat

import com.ai.assistance.operit.data.model.*
import org.json.JSONObject

data class WordResponse(val move:WordMove,val comment:String)
data class CardResponse(val move:CardMove,val comment:String)

/** Rules validate proposed model actions; no automatic move is substituted for an invalid answer. */
object TableGameResponses {
    fun wordInstruction(game:WordGuessGame):String {
        require(game.turn==PlaySide.ROLE)
        return if(game.setter==PlaySide.ROLE)
            "你出提示，用户猜词。answer/aliases 只用于构思；提示和comment都不能包含答案或别名原文，不替用户猜。返回 {\"schemaVersion\":1,\"action\":\"clue\",\"text\":\"不超过600字符的提示\",\"comment\":\"简短回应\"}。"
        else "你猜用户的词。只依据已公开分类和提示，答案由本地规则判断。返回 {\"schemaVersion\":1,\"action\":\"guess\",\"text\":\"一个不超过80字符的答案\",\"comment\":\"简短回应\"}；放弃可返回 {\"schemaVersion\":1,\"action\":\"give_up\",\"comment\":\"简短回应\"}。"
    }
    const val CARD_INSTRUCTION="双人四色卡牌：hand 是你的牌，不能假设对方手牌或后续牌序。face 0..9 数字、10跳过、11反转、12摸二、13万能、14万能摸四。只选 legalMoves 中一个动作；双人跳过/反转/罚摸后本方继续，不叠加。返回 {\"schemaVersion\":1,\"action\":\"play\",\"card\":牌ID,\"color\":null或万能牌选择的red/yellow/green/blue,\"comment\":\"简短回应\"}，或 {\"schemaVersion\":1,\"action\":\"draw\",\"comment\":\"简短回应\"}，摸后不出牌则 action 为 pass。"
    private fun value(text:String):JSONObject {
        require(text.length<=PlayGenerations.OUTPUT_LIMIT&&text.toByteArray(Charsets.UTF_8).size<=PlayGenerations.OUTPUT_LIMIT)
        return PlayJson.objectValue(text).also {PlayJson.integer(it,"schemaVersion",1..1)}
    }
    fun word(game:WordGuessGame,text:String):WordResponse=try {
        require(game.turn==PlaySide.ROLE)
        val v=value(text);val action=PlayJson.string(v,"action")
        val move=when(action) {
            "clue"->{PlayJson.keys(v,"schemaVersion","action","text","comment");WordMove.Clue(PlaySide.ROLE,PlayJson.string(v,"text"))}
            "guess"->{PlayJson.keys(v,"schemaVersion","action","text","comment");WordMove.Guess(PlaySide.ROLE,PlayJson.string(v,"text"))}
            "give_up"->{PlayJson.keys(v,"schemaVersion","action","comment");WordMove.GiveUp(PlaySide.ROLE)}
            else->throw IllegalArgumentException()
        }
        val comment=PlayJson.string(v,"comment");PlayJson.text(comment,1200,true)
        if(move is WordMove.Clue)game.validateClueText(comment,1200,true)
        game.apply(move)
        WordResponse(move,comment)
    }catch(_:Exception){throw ChatFailure("PLAY_RESPONSE")}
    fun cards(game:FourColorGame,text:String):CardResponse=try {
        require(game.turn==PlaySide.ROLE)
        val v=value(text);val move=when(PlayJson.string(v,"action")) {
            "play"->{
                PlayJson.keys(v,"schemaVersion","action","card","color","comment")
                val c=v.get("color");require(c==JSONObject.NULL||c is String)
                CardMove.Play(PlaySide.ROLE,PlayJson.integer(v,"card",0..107),if(c==JSONObject.NULL)null else CardColor.entries.single {it.wire==c})
            }
            "draw"->{PlayJson.keys(v,"schemaVersion","action","comment");CardMove.Draw(PlaySide.ROLE)}
            "pass"->{PlayJson.keys(v,"schemaVersion","action","comment");CardMove.Pass(PlaySide.ROLE)}
            else->throw IllegalArgumentException()
        }
        val comment=PlayJson.string(v,"comment");PlayJson.text(comment,1200,true)
        game.apply(move)
        CardResponse(move,comment)
    }catch(_:Exception){throw ChatFailure("PLAY_RESPONSE")}
}
