package com.ai.assistance.operit.data.model

import kotlin.math.abs
import kotlin.math.sign

enum class XqColor { RED, BLACK; fun other()=if(this==RED)BLACK else RED }
enum class XqKind(val fen:Char) { KING('k'),ADVISOR('a'),ELEPHANT('e'),HORSE('h'),CHARIOT('r'),CANNON('c'),PAWN('p') }
data class XqPiece(val id:Int,val color:XqColor,val kind:XqKind)
data class XqMove(val from:Int,val to:Int) {
    init {require(from in 0..89&&to in 0..89&&from!=to)}
    fun coordinate()="${('a'.code+from%9).toChar()}${9-from/9}${('a'.code+to%9).toChar()}${9-to/9}"
    companion object {
        fun parse(text:String):XqMove {
            require(text.length==4&&text[0] in 'a'..'i'&&text[2] in 'a'..'i'&&text[1] in '0'..'9'&&text[3] in '0'..'9')
            return XqMove((9-text[1].digitToInt())*9+(text[0]-'a'),(9-text[3].digitToInt())*9+(text[2]-'a'))
        }
    }
}

/** Rows run from Black's home rank (0) to Red's (9); physical IDs survive every move. */
class XiangqiBoard private constructor(private val cells:Array<XqPiece?>,val turn:XqColor) {
    operator fun get(square:Int):XqPiece? {require(square in 0..89);return cells[square]}
    fun pieces():List<Pair<Int,XqPiece>> = cells.mapIndexedNotNull {square,piece->piece?.let {square to it}}
    fun squareOf(id:Int)=cells.indexOfFirst {it?.id==id}
    fun crossed(square:Int,color:XqColor)=if(color==XqColor.RED)square/9<=4 else square/9>=5
    fun positionKey()=buildString(91) {
        cells.forEach {p->append(p?.let {if(it.color==XqColor.RED)it.kind.fen.uppercaseChar()else it.kind.fen}?:'.')}
        append(if(turn==XqColor.RED)'r'else'b')
    }
    fun fen()=buildString {
        repeat(10){r->if(r>0)append('/');var empty=0
            repeat(9){c->val p=cells[r*9+c]
                if(p==null)empty++ else {if(empty>0){append(empty);empty=0};append(if(p.color==XqColor.RED)p.kind.fen.uppercaseChar()else p.kind.fen)}}
            if(empty>0)append(empty)
        };append(if(turn==XqColor.RED)" w"else" b")
    }
    fun inCheck(color:XqColor):Boolean {
        val king=cells.indexOfFirst {it?.color==color&&it.kind==XqKind.KING}
        require(king>=0)
        return cells.indices.any {from->val p=cells[from];p!=null&&p.color!=color&&pseudo(XqMove(from,king),p.color)}
    }
    fun legal(move:XqMove,color:XqColor=turn):Boolean =
        cells[move.to]?.kind!=XqKind.KING&&pseudo(move,color)&&!relocate(move,color.other()).inCheck(color)

    fun legalMoves(color:XqColor=turn):List<XqMove> = buildList {
        cells.forEachIndexed {from,p->if(p?.color==color)targets(from,p).forEach {to->
            val move=XqMove(from,to);if(legal(move,color))add(move)
        }}
    }
    fun move(move:XqMove):XiangqiBoard {
        require(legal(move));return relocate(move,turn.other())
    }
    /** Rule analysis only: caller has verified the hypothetical capture/recapture. */
    internal fun relocate(move:XqMove,next:XqColor):XiangqiBoard {
        require(cells[move.from]!=null)
        val copy=cells.copyOf();copy[move.to]=copy[move.from];copy[move.from]=null
        return XiangqiBoard(copy,next)
    }
    internal fun pseudo(move:XqMove,color:XqColor,occupancy:XiangqiBoard=this):Boolean {
        val p=cells[move.from]?:return false
        if(p.color!=color||cells[move.to]?.color==color)return false
        return reaches(p,move.from,move.to,cells[move.to]!=null,occupancy)
    }
    /** Separate occupancy is needed by WXF rooted-piece analysis before a hypothetical capture. */
    internal fun reaches(p:XqPiece,from:Int,to:Int,capture:Boolean,occupancy:XiangqiBoard=this):Boolean {
        val r=from/9;val c=from%9;val tr=to/9;val tc=to%9;val dr=tr-r;val dc=tc-c
        return when(p.kind) {
            XqKind.KING->if(cells[to]?.kind==XqKind.KING&&c==tc)between(from,to,occupancy)==0
                else abs(dr)+abs(dc)==1&&palace(tr,tc,p.color)
            XqKind.ADVISOR->abs(dr)==1&&abs(dc)==1&&palace(tr,tc,p.color)
            XqKind.ELEPHANT->abs(dr)==2&&abs(dc)==2&&(if(p.color==XqColor.RED)tr>=5 else tr<=4)&&occupancy.cells[(r+dr/2)*9+c+dc/2]==null
            XqKind.HORSE->when {
                abs(dr)==2&&abs(dc)==1->occupancy.cells[(r+dr.sign)*9+c]==null
                abs(dr)==1&&abs(dc)==2->occupancy.cells[r*9+c+dc.sign]==null
                else->false
            }
            XqKind.CHARIOT->(dr==0||dc==0)&&between(from,to,occupancy)==0
            XqKind.CANNON->(dr==0||dc==0)&&between(from,to,occupancy)==if(capture)1 else 0
            XqKind.PAWN->(dc==0&&dr==(if(p.color==XqColor.RED)-1 else 1))||(dr==0&&abs(dc)==1&&crossed(from,p.color))
        }
    }
    private fun targets(from:Int,p:XqPiece):List<Int> {
        val r=from/9;val c=from%9
        val offsets=when(p.kind) {
            XqKind.KING,XqKind.PAWN->listOf(-1 to 0,1 to 0,0 to -1,0 to 1)
            XqKind.ADVISOR->listOf(-1 to -1,-1 to 1,1 to -1,1 to 1)
            XqKind.ELEPHANT->listOf(-2 to -2,-2 to 2,2 to -2,2 to 2)
            XqKind.HORSE->listOf(-2 to -1,-2 to 1,2 to -1,2 to 1,-1 to -2,-1 to 2,1 to -2,1 to 2)
            XqKind.CHARIOT,XqKind.CANNON->return (0..9).filter {it!=r}.map {it*9+c}+(0..8).filter {it!=c}.map {r*9+it}
        }
        return offsets.mapNotNull {(dr,dc)->if(r+dr in 0..9&&c+dc in 0..8)(r+dr)*9+c+dc else null}
    }
    companion object {
        const val INITIAL="rheakaehr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RHEAKAEHR w"
        fun start()=parse(INITIAL)
        /** Internal fixture/storage notation, not a public arbitrary-position import. */
        internal fun parse(fen:String):XiangqiBoard {
            require(fen.length<=180)
            val parts=fen.split(' ');require(parts.size==2&&parts[1] in setOf("w","b"))
            val ranks=parts[0].split('/');require(ranks.size==10)
            val cells=arrayOfNulls<XqPiece>(90);var id=0
            ranks.forEachIndexed {r,rank->var c=0;rank.forEach {ch->
                if(ch in '1'..'9')c+=ch.digitToInt() else {
                    require(c<9);val symbol=when(ch.lowercaseChar()){'n'->'h';'b'->'e';else->ch.lowercaseChar()}
                    val kind=XqKind.entries.single {it.fen==symbol}
                    cells[r*9+c]=XqPiece(id++,if(ch.isUpperCase())XqColor.RED else XqColor.BLACK,kind);c++
                }
            };require(c==9)}
            require(id<=32)
            XqColor.entries.forEach {color->
                require(cells.count {it?.color==color&&it.kind==XqKind.KING}==1)
                cells.forEachIndexed {square,p->if(p?.color==color)when(p.kind) {
                    XqKind.KING,XqKind.ADVISOR->require(palace(square/9,square%9,color))
                    XqKind.ELEPHANT->require(if(color==XqColor.RED)square/9>=5 else square/9<=4)
                    else->Unit
                }}
                XqKind.entries.forEach {kind->require(cells.count {it?.color==color&&it.kind==kind}<=when(kind){XqKind.KING->1;XqKind.PAWN->5;else->2})}
            }
            return XiangqiBoard(cells,if(parts[1]=="w")XqColor.RED else XqColor.BLACK)
        }
        private fun palace(row:Int,column:Int,color:XqColor)=column in 3..5&&(if(color==XqColor.RED)row in 7..9 else row in 0..2)
        private fun between(from:Int,to:Int,board:XiangqiBoard):Int {
            if(from==to||from/9!=to/9&&from%9!=to%9)return -1
            val step=if(from/9==to/9)(to-from).sign else (to-from).sign*9
            var count=0;var s=from+step
            while(s!=to){if(board.cells[s]!=null)count++;s+=step};return count
        }
    }
}
