package com.ai.assistance.operit.data.model

/** One souvenir per saved adventure and reward kind; it is not currency or a real-world transfer. */
enum class HomeAdventureReward(val item:HomeItem,val adventureItem:AdventureItem,val flag:AdventureFlag) {
    LIGHTHOUSE(HomeItem.HARBOR_BADGE,AdventureItem.BADGE,AdventureFlag.LIT),
    SCARF(HomeItem.SEAGULL_PIN,AdventureItem.PIN,AdventureFlag.SCARF_RETURNED);
    fun earned(game:AdventureGame)=flag in game.flags&&(game.inventory[adventureItem]?:0)==1
}

/** A replayable local receipt, stripped of all dialogue and participant names. */
class HomeAdventureProof private constructor(val json:String,val game:AdventureGame) {
    fun earned(reward:HomeAdventureReward)=reward.earned(game)
    override fun equals(other:Any?)=other is HomeAdventureProof&&json==other.json
    override fun hashCode()=json.hashCode()
    companion object {
        private const val USER="冒险者"
        private const val ROLE="同行者"
        fun from(game:AdventureGame):HomeAdventureProof {
            var proof=AdventureGame.start(USER,ROLE)
            game.events.forEach {if(it is AdventureAction.Move||it is AdventureAction.Interact)proof=proof.apply(it)}
            return HomeAdventureProof(proof.encode(),proof)
        }
        fun decode(json:String):HomeAdventureProof {
            val game=AdventureGame.decode(json)
            require(game.userName==USER&&game.roleName==ROLE)
            require(game.events.all {it is AdventureAction.Move||it is AdventureAction.Interact})
            return HomeAdventureProof(game.encode(),game)
        }
    }
}
