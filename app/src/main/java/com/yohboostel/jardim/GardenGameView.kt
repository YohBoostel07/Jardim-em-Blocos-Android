package com.yohboostel.jardim

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.min
import kotlin.random.Random

class GardenGameView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val board = Array(9) { BooleanArray(9) }
    private val colors = Array(9) { IntArray(9) }
    private val palette = intArrayOf(Color.rgb(91,176,132), Color.rgb(105,164,210), Color.rgb(232,173,83), Color.rgb(185,130,193))
    private val shapes = listOf(
        listOf(0 to 0), listOf(0 to 0, 1 to 0), listOf(0 to 0, 0 to 1),
        listOf(0 to 0, 1 to 0, 2 to 0), listOf(0 to 0, 0 to 1, 1 to 1),
        listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1), listOf(0 to 0, 1 to 0, 2 to 0, 1 to 1),
        listOf(0 to 0, 0 to 1, 0 to 2), listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0)
    )
    private var pieces = List(3) { newPiece() }
    private var selected = 0
    private var score = 0
    private var best = 0
    private var level = 1
    private var gameOver = false
    private var boardLeft = 0f
    private var boardTop = 0f
    private var cell = 0f
    private var pieceTop = 0f
    private var gardenStage = 0

    private data class Piece(val cells: List<Pair<Int,Int>>, val color: Int)
    private fun newPiece() = Piece(shapes[Random.nextInt(shapes.size)], palette[Random.nextInt(palette.size)])

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        val bg = LinearGradient(0f, 0f, w, h, Color.rgb(20,61,51), Color.rgb(35,91,68), Shader.TileMode.CLAMP)
        paint.shader = bg; canvas.drawRect(0f,0f,w,h,paint); paint.shader = null
        drawGarden(canvas, w, h)
        paint.color = Color.WHITE; paint.textAlign = Paint.Align.CENTER; paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textSize = w * .075f; canvas.drawText("Jardim em Blocos", w/2, h*.075f, paint)
        paint.textSize = w*.032f; paint.color = Color.rgb(206,231,210); canvas.drawText("CULTIVE • COMBINE • FLORESÇA", w/2, h*.105f, paint)
        drawStat(canvas, w*.25f, h*.17f, "PONTOS", score.toString())
        drawStat(canvas, w*.75f, h*.17f, "RECORDE", maxOf(best,score).toString())
        val boardSize = min(w*.88f, h*.49f)
        cell = boardSize/9f; boardLeft = (w-boardSize)/2; boardTop = h*.235f
        rounded(canvas, boardLeft-8, boardTop-8, boardLeft+boardSize+8, boardTop+boardSize+8, 24f, Color.argb(210,12,43,37))
        for (r in 0..8) for (c in 0..8) {
            val l=boardLeft+c*cell; val t=boardTop+r*cell
            rounded(canvas,l+2,t+2,l+cell-2,t+cell-2,cell*.17f, if(board[r][c]) colors[r][c] else Color.argb(75,210,239,215))
            if(board[r][c]) {
                paint.color=Color.argb(55,255,255,255); canvas.drawCircle(l+cell*.28f,t+cell*.25f,cell*.09f,paint)
            }
        }
        pieceTop = boardTop+boardSize+h*.075f
        paint.color=Color.rgb(226,244,226); paint.textSize=w*.035f; paint.textAlign=Paint.Align.CENTER
        canvas.drawText("ESCOLHA UMA PEÇA",w/2,pieceTop-h*.025f,paint)
        val slotW=w*.27f
        for(i in 0..2) {
            val cx=w*(.19f+i*.31f); val cy=pieceTop+h*.055f
            val active=i==selected
            rounded(canvas,cx-slotW/2,cy-h*.015f,cx+slotW/2,cy+h*.095f,20f,if(active) Color.rgb(245,230,183) else Color.argb(105,8,39,33))
            drawPiece(canvas,pieces[i],cx,cy+h*.035f,cell*.42f)
        }
        paint.color=Color.rgb(226,244,226); paint.textSize=w*.032f
        canvas.drawText("Complete linhas e colunas para liberar espaço",w/2,h*.94f,paint)
        if(gameOver) {
            paint.color=Color.argb(190,5,24,20); canvas.drawRect(0f,0f,w,h,paint)
            paint.color=Color.WHITE; paint.textSize=w*.075f; canvas.drawText("Fim de jogo",w/2,h*.46f,paint)
            paint.textSize=w*.04f; canvas.drawText("Toque para recomeçar",w/2,h*.52f,paint)
        }
    }

    private fun drawGarden(c:Canvas,w:Float,h:Float) {
        paint.color=Color.argb(35,255,255,255)
        for(i in 0..5) c.drawCircle(w*(i/5f),h*.205f,18f,paint)
        paint.color=Color.rgb(248,221,151); c.drawCircle(w*.87f,h*.13f,w*.045f,paint)
        paint.color=Color.argb(55,255,255,255); c.drawOval(w*.05f,h*.13f,w*.3f,h*.19f,paint)
    }
    private fun drawStat(c:Canvas,x:Float,y:Float,label:String,value:String) {
        rounded(c,x-width*.17f,y-height*.027f,x+width*.17f,y+height*.045f,18f,Color.argb(95,5,35,29))
        paint.color=Color.rgb(194,222,197); paint.textAlign=Paint.Align.CENTER; paint.textSize=width*.027f
        c.drawText(label,x,y,paint); paint.color=Color.WHITE; paint.textSize=width*.045f; paint.typeface=Typeface.DEFAULT_BOLD
        c.drawText(value,x,y+height*.04f,paint)
    }
    private fun drawPiece(c:Canvas,p:Piece,cx:Float,cy:Float,size:Float) {
        val maxX=p.cells.maxOf{it.first}+1; val maxY=p.cells.maxOf{it.second}+1
        val unit=min(size*2.2f/maxX,size*2.2f/maxY)
        p.cells.forEach { (x,y) -> rounded(c,cx-maxX*unit/2+x*unit,cy-maxY*unit/2+y*unit,cx-maxX*unit/2+(x+1)*unit-3,cy-maxY*unit/2+(y+1)*unit-3,7f,p.color) }
    }
    private fun rounded(c:Canvas,l:Float,t:Float,r:Float,b:Float,rad:Float,color:Int) {
        paint.color=color; paint.shader=null; c.drawRoundRect(l,t,r,b,rad,rad,paint)
    }
    private fun fits(p:Piece,row:Int,col:Int):Boolean = p.cells.all { (x,y) ->
        row+y in 0..8 && col+x in 0..8 && !board[row+y][col+x]
    }
    private fun place(p:Piece,row:Int,col:Int) {
        p.cells.forEach { (x,y) -> board[row+y][col+x]=true; colors[row+y][col+x]=p.color }
        score += p.cells.size*5
        val clearRows=(0..8).filter { r -> (0..8).all { board[r][it] } }
        val clearCols=(0..8).filter { col -> (0..8).all { r -> board[r][col] } }
        clearRows.forEach { r -> for(col in 0..8) board[r][col]=false }
        clearCols.forEach { col -> for(r in 0..8) board[r][col]=false }
        val cleared=clearRows.size+clearCols.size
        if(cleared>0) { score+=cleared*100+if(cleared>1) (cleared-1)*75 else 0; gardenStage=(gardenStage+cleared).coerceAtMost(8) }
        best=maxOf(best,score)
        level=1+score/500
        pieces=pieces.toMutableList().also { it[selected]=newPiece() }
        gameOver=pieces.none { p -> (0..8).any { r -> (0..8).any { col -> fits(p,r,col) } } }
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        if(event.action!=MotionEvent.ACTION_UP) return true
        val x=event.x; val y=event.y
        if(gameOver) { for(r in 0..8) for(c in 0..8) board[r][c]=false; score=0; gameOver=false; pieces=List(3){newPiece()}; gardenStage=0; invalidate(); return true }
        val boardSize=cell*9
        if(x in boardLeft..(boardLeft+boardSize) && y in boardTop..(boardTop+boardSize)) {
            val col=((x-boardLeft)/cell).toInt(); val row=((y-boardTop)/cell).toInt()
            val p=pieces[selected]
            if(fits(p,row,col)) place(p,row,col)
        } else if(y>pieceTop-height*.03f && y<pieceTop+height*.13f) {
            val idx=((x/width)*3).toInt().coerceIn(0,2); selected=idx
        }
        invalidate(); return true
    }
}
