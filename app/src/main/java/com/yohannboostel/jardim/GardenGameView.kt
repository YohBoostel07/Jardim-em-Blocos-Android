package com.yohboostel.jardim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.animation.ValueAnimator
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GardenGameView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val prefs = context.getSharedPreferences("garden_game", Context.MODE_PRIVATE)
    private val board = Array(9) { BooleanArray(9) }
    private val colors = Array(9) { IntArray(9) }
    private val palette = intArrayOf(
        Color.rgb(45, 205, 133), Color.rgb(54, 151, 245),
        Color.rgb(255, 177, 53), Color.rgb(178, 105, 232),
        Color.rgb(245, 91, 112), Color.rgb(35, 195, 190)
    )
    private val shapes = listOf(
        listOf(0 to 0),
        listOf(0 to 0, 1 to 0), listOf(0 to 0, 0 to 1),
        listOf(0 to 0, 1 to 0, 2 to 0), listOf(0 to 0, 0 to 1, 0 to 2),
        listOf(0 to 0, 1 to 0, 0 to 1), listOf(0 to 0, 1 to 0, 1 to 1),
        listOf(0 to 0, 0 to 1, 1 to 1), listOf(1 to 0, 0 to 1, 1 to 1),
        listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),
        listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0), listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3),
        listOf(0 to 0, 1 to 0, 2 to 0, 1 to 1),
        listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1),
        listOf(0 to 0, 0 to 1, 0 to 2, 1 to 2),
        listOf(1 to 0, 1 to 1, 0 to 2, 1 to 2),
        listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1, 2 to 2),
        listOf(0 to 0, 1 to 0, 2 to 0, 0 to 1, 0 to 2),
        listOf(0 to 0, 1 to 0, 2 to 0, 1 to 1, 1 to 2)
    )

    private data class Piece(val cells: List<Pair<Int, Int>>, val color: Int)
    private data class Spark(val x: Float, val y: Float, val color: Int, val size: Float, val vx: Float = 0f, val vy: Float = 0f, val rotation: Float = 0f)
    private data class Blast(val x: Float, val y: Float, val color: Int)
    private var blastCenters = emptyList<Blast>()

    private var pieces = MutableList<Piece?>(3) { newPiece() }
    private var selected = 0
    private var score = 0
    private var best = prefs.getInt("best_score", 0)
    private var level = 1
    private var gameOver = false
    private var paused = false
    private var showTutorial = !prefs.getBoolean("tutorial_seen", false)
    private var confirmRestart = false
    private var soundEnabled = prefs.getBoolean("sound_enabled", true)
    private var gamesPlayed = prefs.getInt("games_played", 0)
    private var totalLines = prefs.getInt("total_lines", 0)
    private var bestCombo = prefs.getInt("best_combo", 0)
    private var mainMenuOpen = true
    private var showAchievements = false
    private var showExpeditions = false
    private var expeditionUnlocked = prefs.getInt("expedition_unlocked", 0).coerceIn(0, 5)
    private var expeditionStage = 0
    private var activeExpedition = false
    private var showExpeditionComplete = false
    private val expeditionGoals = intArrayOf(300, 600, 900, 1300, 1800)
    private val expeditionRewards = intArrayOf(100, 150, 200, 300, 500)
    private val expeditionNames = listOf("Primeiros brotos", "Canteiro em ordem", "Jardim vibrante", "Bosque renovado", "Paraíso florescido")
    private var showGardens = false
    private var gardenDetailOpen = false
    private var selectedGarden = 0
    private var showShop = false
    private var activePower = ""
    private var hammerCount = prefs.getInt("power_hammer", 0)
    private var rowBombCount = prefs.getInt("power_row_bomb", 0)
    private var rerollCount = prefs.getInt("power_reroll", 0)
    private var coins = prefs.getInt("garden_coins", 100)
    private val gardenNames = listOf("Jardim das Flores", "Bosque Encantado", "Lago Sereno")
    private val gardenDescriptions = listOf("Recupere o canteiro e traga as cores de volta.", "Desperte a vida entre as árvores.", "Limpe as águas e floresça as margens.")
    private val gardenCosts = arrayOf(intArrayOf(50, 80, 120), intArrayOf(100, 150, 200), intArrayOf(150, 200, 250))
    private val gardenStages = IntArray(3) { prefs.getInt("garden_stage_$it", 0).coerceIn(0, 3) }
    private var dailyLines = prefs.getInt("daily_lines", 0)
    private var dailyRewardClaimed = prefs.getBoolean("daily_reward_claimed", false)
    private var weeklyLines = prefs.getInt("weekly_lines", 0)
    private var weeklyRewardClaimed = prefs.getBoolean("weekly_reward_claimed", false)
    private var boardLeft = 0f
    private var boardTop = 0f
    private var cell = 0f
    private var boardSize = 0f
    private var slotsTop = 0f
    private var trayCenterY = 0f
    private var dragging = false
    private var previewRow = -1
    private var previewCol = -1
    private var previewValid = false
    private var comboLabel = ""
    private var comboAlpha = 0f
    private var sparks = emptyList<Spark>()
    private var effectProgress = 1f
    private var lastComboLines = 0
    private var lastMoveTime = 0L
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 65)

    init {
        isFocusable = true
        isClickable = true
        refreshDailyMission()
        refreshWeeklyMission()
    }

    private fun newPiece(): Piece {
        // Mistura peças pequenas, médias e grandes sem tornar o sorteio injusto.
        val roll = Random.nextInt(100)
        val index = when {
            roll < 18 -> Random.nextInt(0, 6)
            roll < 72 -> Random.nextInt(1, 14)
            else -> Random.nextInt(9, shapes.size)
        }
        return Piece(shapes[index], palette[Random.nextInt(palette.size)])
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (mainMenuOpen) {
            when {
                showAchievements -> drawAchievementsScreen(canvas, w, h)
                showExpeditions -> drawExpeditionsScreen(canvas, w, h)
                else -> drawMainMenu(canvas, w, h)
            }
            return
        }
        // A área dos jardins substitui a partida; o tabuleiro não deve ser renderizado atrás dela.
        if (showGardens) {
            drawGardenWorldBackground(canvas, w, h)
            if (gardenDetailOpen) drawGardenDetailScreen(canvas, w, h) else drawGardensScreen(canvas, w, h)
            return
        }

        boardSize = min(w * .90f, h * .455f)
        cell = boardSize / 9f
        boardLeft = (w - boardSize) / 2f
        boardTop = h * .235f
        slotsTop = boardTop + boardSize + h * .062f
        trayCenterY = slotsTop + h * .054f

        drawHeader(canvas, w, h)
        drawBoard(canvas)
        drawPieceTray(canvas, w, h)
        drawFooter(canvas, w, h)
        if (sparks.isNotEmpty()) drawSparks(canvas)
        if (showShop) drawShopScreen(canvas, w, h)
        if (showExpeditionComplete) drawExpeditionCompleteScreen(canvas, w, h)
        if (showTutorial) drawTutorial(canvas, w, h)
        else if (confirmRestart) drawRestartConfirmation(canvas, w, h)
        else if (paused) drawPauseMenu(canvas, w, h)
        else if (gameOver) drawGameOver(canvas, w, h)
    }

    private fun drawMainMenu(c: Canvas, w: Float, h: Float) {
    drawGardenWorldBackground(c,w,h)
    paint.shader=LinearGradient(0f,0f,0f,h,intArrayOf(Color.argb(20,20,54,70),Color.argb(18,30,70,60),Color.argb(170,16,48,42)),null,Shader.TileMode.CLAMP)
    c.drawRect(0f,0f,w,h,paint);paint.shader=null
    // Sol, nuvens e pequenas partículas dão profundidade ao cenário.
    drawCloud3D(c,w*.19f,h*.105f,w*.16f,h*.035f);drawCloud3D(c,w*.82f,h*.16f,w*.13f,h*.028f)
    paint.color=Color.argb(235,255,225,129);c.drawCircle(w*.80f,h*.105f,w*.055f,paint)
    val title=RectF(w*.09f,h*.035f,w*.91f,h*.235f)
    drawPremiumCard(c,title,34f,Color.argb(225,31,91,71),Color.argb(220,13,55,53))
    paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BLACK)
    paint.color=Color.rgb(255,244,194);paint.textSize=w*.105f;paint.setShadowLayer(5f,0f,4f,Color.argb(150,0,0,0))
    c.drawText("JARDIM",w*.5f,h*.125f,paint);paint.clearShadowLayer()
    paint.color=Color.WHITE;paint.textSize=w*.064f;paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD)
    c.drawText("EM BLOCOS",w*.5f,h*.190f,paint)
    paint.color=Color.rgb(230,255,208);paint.textSize=w*.023f;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD)
    c.drawText("CULTIVE • COMBINE • FLORESÇA",w*.5f,h*.218f,paint)
    val play=RectF(w*.14f,h*.585f,w*.86f,h*.685f)
    drawPremiumCard(c,play,30f,Color.rgb(255,229,135),Color.rgb(236,153,58))
    paint.color=Color.rgb(73,73,42);paint.textSize=w*.052f;paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD)
    c.drawText("▶  JOGAR",play.centerX(),play.centerY()+w*.017f,paint)
    val rs=listOf(RectF(w*.055f,h*.715f,w*.345f,h*.815f),RectF(w*.355f,h*.715f,w*.645f,h*.815f),RectF(w*.655f,h*.715f,w*.945f,h*.815f))
    val labels=listOf("JARDINS","EXPEDIÇÃO","CONQUISTAS");val icons=listOf("✿","✧","★")
    val tops=listOf(Color.rgb(91,190,117),Color.rgb(105,166,220),Color.rgb(213,157,83))
    val bots=listOf(Color.rgb(35,126,78),Color.rgb(47,101,161),Color.rgb(151,91,48))
    for(i in rs.indices){drawPremiumCard(c,rs[i],22f,tops[i],bots[i]);paint.color=Color.WHITE;paint.textSize=w*.044f;paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD);c.drawText(icons[i],rs[i].centerX(),rs[i].top+h*.043f,paint);paint.textSize=w*.021f;c.drawText(labels[i],rs[i].centerX(),rs[i].bottom-h*.018f,paint)}
    val info=RectF(w*.17f,h*.85f,w*.83f,h*.925f);drawPremiumCard(c,info,24f,Color.argb(225,30,86,65),Color.argb(225,12,48,48))
    paint.color=Color.rgb(255,232,158);paint.textSize=w*.026f;paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD)
    c.drawText("✦  $coins MOEDAS     •     RECORDE $best",w*.5f,h*.895f,paint)
    paint.color=Color.WHITE;paint.textSize=w*.020f;paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL)
    c.drawText("UM MUNDO INTEIRO PARA FLORESCER",w*.5f,h*.955f,paint)
}

    private fun drawAchievementsScreen(c: Canvas,w: Float,h: Float){
        drawDim(c,w,h)
        drawPremiumCard(c,RectF(w*.045f,h*.045f,w*.955f,h*.955f),30f,Color.rgb(247,239,207),Color.rgb(207,220,181))
        paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("serif",Typeface.BOLD)
        paint.color=Color.rgb(48,91,65);paint.textSize=w*.056f
        c.drawText("SALÃO DAS CONQUISTAS",w*.5f,h*.115f,paint)
        paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textSize=w*.023f
        paint.color=Color.rgb(110,119,87);c.drawText("Desafios especiais • recompensas em moedas",w*.5f,h*.155f,paint)
        val titles=listOf("Primeira partida","Jardineiro dedicado","Pontuação de ouro","Combo espetacular","Mestre das linhas","Mundo florescido")
        val details=listOf("Jogue sua primeira partida","Limpe 10 linhas no total","Alcance 500 pontos","Limpe 3 linhas de uma vez","Limpe 50 linhas no total","Restaure 3 etapas de jardins")
        val unlocked=listOf(gamesPlayed>=1,totalLines>=10,best>=500,bestCombo>=3,totalLines>=50,gardenStages.sum()>=3)
        val keys=listOf("ach_first","ach_lines","ach_score","ach_combo","ach_master_lines","ach_world")
        val rewards=intArrayOf(50,75,100,100,150,200)
        for(i in 0..5){
            val top=h*(.195f+i*.102f);val rect=RectF(w*.085f,top,w*.915f,top+h*.087f)
            drawPremiumCard(c,rect,15f,if(unlocked[i])Color.rgb(255,252,232)else Color.rgb(220,219,193),if(unlocked[i])Color.rgb(231,239,207)else Color.rgb(198,204,176))
            paint.color=if(unlocked[i])Color.rgb(220,166,63)else Color.rgb(148,157,132)
            c.drawCircle(w*.16f,top+h*.043f,w*.034f,paint)
            paint.color=Color.WHITE;paint.textSize=w*.035f
            c.drawText(if(unlocked[i])"★"else"·",w*.16f,top+h*.055f,paint)
            paint.textAlign=Paint.Align.LEFT;paint.typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)
            paint.color=Color.rgb(47,91,64);paint.textSize=w*.027f;c.drawText(titles[i],w*.23f,top+h*.036f,paint)
            paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textSize=w*.019f
            paint.color=Color.rgb(103,111,87);c.drawText(details[i],w*.23f,top+h*.065f,paint)
            val claimed=prefs.getBoolean(keys[i],false)
            val rr=RectF(w*.70f,top+h*.018f,w*.88f,top+h*.071f)
            drawPremiumCard(c,rr,10f,if(unlocked[i]&&!claimed)Color.rgb(105,174,105)else Color.rgb(181,193,164),if(unlocked[i]&&!claimed)Color.rgb(49,124,72)else Color.rgb(145,160,132))
            paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD)
            paint.color=Color.WHITE;paint.textSize=w*.018f
            c.drawText(if(claimed)"RESGATADA"else if(unlocked[i])"+"+rewards[i]+" ✦"else"BLOQUEADA",rr.centerX(),rr.centerY()+w*.006f,paint)
        }
        val back=RectF(w*.22f,h*.84f,w*.78f,h*.91f)
        drawPremiumCard(c,back,17f,Color.rgb(72,151,94),Color.rgb(34,103,67))
        paint.color=Color.WHITE;paint.textSize=w*.028f;c.drawText("VOLTAR AO MENU",back.centerX(),back.centerY()+w*.01f,paint)
    }

    private fun drawExpeditionsScreen(c: Canvas, w: Float, h: Float) {
        drawDim(c,w,h)
        drawPremiumCard(c,RectF(w*.045f,h*.055f,w*.955f,h*.945f),30f,Color.rgb(247,239,207),Color.rgb(205,220,180))
        paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("serif",Typeface.BOLD)
        paint.color=Color.rgb(47,91,64);paint.textSize=w*.056f
        c.drawText("A GRANDE EXPEDIÇÃO",w*.5f,h*.125f,paint)
        paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textSize=w*.023f
        paint.color=Color.rgb(103,111,87);c.drawText("Cinco jornadas para restaurar o reino",w*.5f,h*.165f,paint)
        for(i in 0..4){
            val top=h*(.205f+i*.118f);val unlocked=i<=expeditionUnlocked;val completed=i<expeditionUnlocked
            val rect=RectF(w*.085f,top,w*.915f,top+h*.102f)
            drawPremiumCard(c,rect,16f,if(completed)Color.rgb(226,241,207)else if(unlocked)Color.rgb(255,251,228)else Color.rgb(218,218,192),if(completed)Color.rgb(190,220,165)else if(unlocked)Color.rgb(233,232,199)else Color.rgb(193,201,176))
            paint.color=if(completed)Color.rgb(80,151,88)else if(unlocked)Color.rgb(217,165,66)else Color.rgb(144,151,128)
            c.drawCircle(w*.145f,top+h*.051f,w*.031f,paint)
            paint.color=Color.WHITE;paint.textSize=w*.026f
            c.drawText(if(completed)"✓"else(i+1).toString(),w*.145f,top+h*.060f,paint)
            paint.textAlign=Paint.Align.LEFT;paint.typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)
            paint.color=if(unlocked)Color.rgb(47,91,64)else Color.rgb(130,133,116);paint.textSize=w*.027f
            c.drawText(expeditionNames[i],w*.21f,top+h*.040f,paint)
            paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textSize=w*.020f;paint.color=Color.rgb(103,111,87)
            c.drawText("Meta: "+expeditionGoals[i]+" pontos",w*.21f,top+h*.073f,paint)
            paint.textAlign=Paint.Align.RIGHT;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD);paint.textSize=w*.020f
            paint.color=if(completed)Color.rgb(65,135,79)else if(unlocked)Color.rgb(174,116,43)else Color.rgb(130,133,116)
            c.drawText(if(completed)"CONCLUÍDA"else if(unlocked)"+"+expeditionRewards[i]+" ✦"else"🔒",w*.88f,top+h*.056f,paint)
        }
        val back=RectF(w*.22f,h*.825f,w*.78f,h*.90f)
        drawPremiumCard(c,back,17f,Color.rgb(72,151,94),Color.rgb(34,103,67))
        paint.textAlign=Paint.Align.CENTER;paint.color=Color.WHITE;paint.textSize=w*.029f
        c.drawText("VOLTAR AO MENU",w*.5f,h*.87f,paint)
    }

    private fun drawExpeditionCompleteScreen(c: Canvas, w: Float, h: Float) {
        drawDim(c,w,h)
        drawPremiumCard(c,RectF(w*.075f,h*.30f,w*.925f,h*.72f),30f,Color.rgb(255,239,174),Color.rgb(201,220,167))
        paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("serif",Typeface.BOLD)
        paint.color=Color.rgb(57,105,66);paint.textSize=w*.060f
        c.drawText("✦  ETAPA CONCLUÍDA!  ✦",w*.5f,h*.405f,paint)
        paint.textSize=w*.034f;paint.color=Color.rgb(165,111,43)
        c.drawText(expeditionNames[expeditionStage],w*.5f,h*.465f,paint)
        paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textSize=w*.030f
        paint.color=Color.rgb(89,111,76)
        c.drawText("Você recebeu "+expeditionRewards[expeditionStage]+" moedas",w*.5f,h*.515f,paint)
        c.drawText(if(expeditionUnlocked>=5)"O reino está totalmente restaurado!"else"Próxima jornada: "+(expeditionUnlocked+1)+" de 5",w*.5f,h*.56f,paint)
        drawButton(c,w,h*.645f,"CONTINUAR",true)
    }

    private fun drawGardenWorldBackground(c: Canvas, w: Float, h: Float) {
    paint.shader=LinearGradient(0f,0f,0f,h,intArrayOf(Color.rgb(105,190,224),Color.rgb(194,226,190),Color.rgb(103,173,105),Color.rgb(42,119,72)),floatArrayOf(0f,.42f,.70f,1f),Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,paint);paint.shader=null
    drawCloud3D(c,w*.18f,h*.12f,w*.17f,h*.035f);drawCloud3D(c,w*.79f,h*.20f,w*.14f,h*.03f)
    drawSphere3D(c,w*.82f,h*.105f,w*.052f,h*.055f,Color.rgb(255,218,111))
    val hills=android.graphics.Path().apply{moveTo(0f,h*.57f);cubicTo(w*.18f,h*.37f,w*.36f,h*.64f,w*.55f,h*.48f);cubicTo(w*.75f,h*.32f,w*.88f,h*.57f,w,h*.43f);lineTo(w,h);lineTo(0f,h);close()}
    paint.shader=LinearGradient(0f,h*.42f,0f,h*.88f,Color.rgb(119,190,104),Color.rgb(53,139,75),Shader.TileMode.CLAMP);c.drawPath(hills,paint);paint.shader=null
    paint.color=Color.argb(55,255,255,255);for(i in 0..12){val x=w*(i/12f);c.drawCircle(x,h*(.72f+(i%3)*.055f),w*.012f,paint)}
    for(i in 0..5){val x=w*(.04f+i*.19f);drawTree3D(c,x,h*.79f,h*.22f,if(i%2==0)Color.rgb(49,157,76)else Color.rgb(67,174,83),.78f+(i%3)*.12f)}
    for(i in 0..17){val x=w*((i*.173f)%1f);val y=h*(.92f+(i%3)*.018f);drawFlower3D(c,x,y,h*.024f,when(i%4){0->Color.rgb(255,105,151);1->Color.rgb(255,211,81);2->Color.rgb(179,137,246);else->Color.WHITE})}
}

    private fun drawGardenBackdrop(c: Canvas, w: Float, h: Float) { /* O cenário é renderizado pela cena OpenGL 3D. */ }

    private fun drawCloud3D(c:Canvas,x:Float,y:Float,rx:Float,ry:Float){
        paint.color=Color.argb(45,43,91,105);c.drawOval(x-rx,y-ry*.05f,x+rx,y+ry*1.35f,paint)
        paint.shader=LinearGradient(x,y-ry,x,y+ry,Color.WHITE,Color.rgb(207,235,231),Shader.TileMode.CLAMP)
        c.drawOval(x-rx,y-ry,x+rx,y+ry,paint);paint.shader=null
        paint.color=Color.argb(125,255,255,255);c.drawCircle(x-rx*.35f,y-ry*.25f,ry*.65f,paint);c.drawCircle(x,y-ry*.40f,ry*.78f,paint)
    }

    private fun drawSphere3D(c:Canvas,x:Float,y:Float,rx:Float,ry:Float,base:Int){
        val r=Color.red(base);val g=Color.green(base);val b=Color.blue(base)
        paint.shader=RadialGradient(x-rx*.32f,y-ry*.42f,max(rx,ry)*1.45f,intArrayOf(Color.rgb(min(255,r+65),min(255,g+65),min(255,b+65)),base,Color.rgb(max(0,r-55),max(0,g-55),max(0,b-45))),floatArrayOf(0f,.58f,1f),Shader.TileMode.CLAMP)
        c.drawOval(x-rx,y-ry,x+rx,y+ry,paint);paint.shader=null
        paint.color=Color.argb(75,255,255,255);c.drawOval(x-rx*.58f,y-ry*.60f,x+rx*.08f,y-ry*.18f,paint)
    }

    private fun drawTree3D(c:Canvas,x:Float,baseY:Float,size:Float,leafColor:Int,scale:Float=1f){
    val hh=size*1.62f*scale;val trunk=size*.24f*scale
    paint.color=Color.argb(65,24,65,35);c.drawOval(x-size*.72f,baseY-size*.08f,x+size*.72f,baseY+size*.17f,paint)
    val trunkPath=android.graphics.Path().apply{moveTo(x-trunk*.42f,baseY);lineTo(x-trunk*.22f,baseY-hh*.78f);lineTo(x+trunk*.18f,baseY-hh*.78f);lineTo(x+trunk*.50f,baseY);close()}
    paint.shader=LinearGradient(x-trunk,baseY-hh*.5f,x+trunk,baseY,Color.rgb(190,128,75),Color.rgb(91,54,34),Shader.TileMode.CLAMP);c.drawPath(trunkPath,paint);paint.shader=null
    paint.color=Color.rgb(112,69,42);paint.strokeWidth=max(2f,trunk*.22f);paint.strokeCap=Paint.Cap.ROUND
    c.drawLine(x,baseY-hh*.48f,x-size*.53f,baseY-hh*.88f,paint);c.drawLine(x+trunk*.08f,baseY-hh*.59f,x+size*.55f,baseY-hh*.91f,paint)
    paint.strokeWidth=max(1f,size*.018f);paint.color=Color.argb(150,238,184,119);c.drawLine(x-trunk*.08f,baseY-hh*.18f,x-trunk*.15f,baseY-hh*.65f,paint);paint.strokeWidth=1f;paint.strokeCap=Paint.Cap.BUTT
    val r=Color.red(leafColor);val g=Color.green(leafColor);val b=Color.blue(leafColor)
    fun shade(dr:Int,dg:Int,db:Int)=Color.rgb((r+dr).coerceIn(0,255),(g+dg).coerceIn(0,255),(b+db).coerceIn(0,255))
    // Camadas de copa com contorno e luz, sem aparência de círculos chapados.
    drawSphere3D(c,x,baseY-hh*.92f,size*.66f,size*.61f,shade(8,16,4));drawSphere3D(c,x-size*.48f,baseY-hh*.76f,size*.53f,size*.47f,shade(-12,-2,-8));drawSphere3D(c,x+size*.48f,baseY-hh*.76f,size*.53f,size*.47f,shade(-16,-4,-9));drawSphere3D(c,x-size*.15f,baseY-hh*1.15f,size*.45f,size*.42f,shade(24,25,12));drawSphere3D(c,x+size*.42f,baseY-hh*1.04f,size*.38f,size*.34f,shade(9,15,5))
    for(i in 0..23){val a=i*2.399f;val lx=x+kotlin.math.cos(a)*size*.59f;val ly=baseY-hh*.91f+kotlin.math.sin(a)*size*.46f;val leaf=RectF(lx-size*.105f,ly-size*.052f,lx+size*.105f,ly+size*.052f);c.save();c.rotate((i*29f)%90f-45f,lx,ly);paint.color=if(i%3==0)shade(34,39,17)else shade(17,24,7);c.drawOval(leaf,paint);paint.color=Color.argb(120,225,255,183);paint.strokeWidth=max(1f,size*.012f);c.drawLine(lx-size*.07f,ly,lx+size*.07f,ly,paint);c.restore()}
    for(i in 0..4){val fx=x+((i-2)*size*.22f);val fy=baseY-hh*(.88f+(i%2)*.13f);paint.color=if(i%2==0)Color.rgb(255,190,74)else Color.rgb(226,91,91);c.drawCircle(fx,fy,size*.045f,paint);paint.color=Color.argb(170,255,255,220);c.drawCircle(fx-size*.012f,fy-size*.014f,size*.014f,paint)}
}

    private fun drawBush3D(c:Canvas,x:Float,y:Float,size:Float){
        paint.color=Color.argb(55,20,53,29);c.drawOval(x-size,y-size*.12f,x+size,y+size*.25f,paint)
        drawSphere3D(c,x-size*.38f,y-size*.22f,size*.55f,size*.47f,Color.rgb(47,142,71))
        drawSphere3D(c,x+size*.28f,y-size*.30f,size*.62f,size*.52f,Color.rgb(67,166,77))
        drawSphere3D(c,x,y-size*.52f,size*.56f,size*.50f,Color.rgb(79,180,82))
    }

    private fun drawRock3D(c:Canvas,x:Float,y:Float,rx:Float,ry:Float){
        val p=android.graphics.Path().apply{moveTo(x-rx,y);lineTo(x-rx*.65f,y-ry*.72f);lineTo(x+rx*.15f,y-ry);lineTo(x+rx*.82f,y-ry*.55f);lineTo(x+rx,y);lineTo(x+rx*.5f,y+ry*.24f);lineTo(x-rx*.65f,y+ry*.18f);close()}
        paint.color=Color.argb(65,25,50,40);c.drawOval(x-rx,y-ry*.12f,x+rx,y+ry*.42f,paint)
        paint.shader=LinearGradient(x-rx,y-ry,x+rx,y+ry,Color.rgb(205,211,187),Color.rgb(91,111,100),Shader.TileMode.CLAMP);c.drawPath(p,paint);paint.shader=null
        paint.color=Color.argb(100,255,255,240);c.drawLine(x-rx*.45f,y-ry*.48f,x+rx*.10f,y-ry*.78f,paint)
    }

    private fun drawFlower3D(c:Canvas,x:Float,y:Float,size:Float,petalColor:Int){
    paint.color=Color.argb(60,20,53,25);c.drawOval(x-size*.85f,y-size*.08f,x+size*.85f,y+size*.30f,paint)
    paint.color=Color.rgb(48,133,59);paint.strokeWidth=max(1.5f,size*.12f);paint.strokeCap=Paint.Cap.ROUND;c.drawLine(x,y,x-size*.06f,y-size*1.48f,paint);paint.strokeCap=Paint.Cap.BUTT
    val leaf=android.graphics.Path().apply{moveTo(x-size*.04f,y-size*.55f);quadTo(x-size*1.05f,y-size*1.20f,x-size*.95f,y-size*.48f);quadTo(x-size*.48f,y-size*.38f,x-size*.04f,y-size*.55f);close()}
    paint.shader=LinearGradient(x-size,y-size,x+size,y,Color.rgb(153,220,104),Color.rgb(40,123,55),Shader.TileMode.CLAMP);c.drawPath(leaf,paint);c.save();c.scale(-1f,1f,x,y-size*.8f);c.drawPath(leaf,paint);c.restore();paint.shader=null
    paint.color=Color.argb(170,220,255,168);paint.strokeWidth=max(1f,size*.025f);c.drawLine(x-size*.06f,y-size*.55f,x-size*.77f,y-size*.55f,paint)
    val cy=y-size*1.53f
    for(layer in 0..1){val count=if(layer==0)9 else 7;for(i in 0 until count){val a=(Math.PI*2*i/count+(if(layer==0)0.0 else Math.PI/count)).toFloat();val px=x+kotlin.math.cos(a)*size*.47f;val py=cy+kotlin.math.sin(a)*size*.42f;val pr=if(layer==0)size*.34f else size*.25f;paint.color=Color.argb(75,70,48,42);c.drawOval(px-pr,py-pr*.82f+size*.035f,px+pr,py+pr*.82f+size*.035f,paint);paint.shader=RadialGradient(px-pr*.25f,py-pr*.3f,pr*1.5f,intArrayOf(Color.WHITE,petalColor,Color.rgb((Color.red(petalColor)*.72f).toInt(),(Color.green(petalColor)*.72f).toInt(),(Color.blue(petalColor)*.78f).toInt())),null,Shader.TileMode.CLAMP);c.drawOval(px-pr,py-pr*.82f,px+pr,py+pr*.82f,paint);paint.shader=null;paint.color=Color.argb(105,255,255,255);c.drawOval(px-pr*.48f,py-pr*.52f,px-pr*.12f,py-pr*.12f,paint)}}
    drawSphere3D(c,x,cy,size*.24f,size*.22f,Color.rgb(255,194,53));paint.color=Color.rgb(143,91,31);for(i in 0..7){val a=(Math.PI*2*i/8).toFloat();c.drawCircle(x+kotlin.math.cos(a)*size*.13f,cy+kotlin.math.sin(a)*size*.12f,size*.023f,paint)}
}

    private fun drawHeader(c: Canvas, w: Float, h: Float) {
    val top=RectF(w*.025f,h*.018f,w*.975f,h*.245f);drawPremiumCard(c,top,27f,Color.rgb(38,111,88),Color.rgb(17,65,63))
    paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD);paint.color=Color.rgb(255,239,184);paint.textSize=w*.048f;c.drawText("JARDIM EM BLOCOS",w*.5f,h*.070f,paint)
    paint.color=Color.rgb(222,250,218);paint.textSize=w*.019f;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD);c.drawText(if(activeExpedition)"EXPEDIÇÃO • ETAPA "+(expeditionStage+1) else "CULTIVE • COMBINE • FLORESÇA",w*.5f,h*.102f,paint)
    drawGardenShortcut(c,w,h);drawStat(c,w*.25f,h*.165f,"PONTOS",score.toString());drawStat(c,w*.69f,h*.165f,"RECORDE",max(best,score).toString())
    paint.color=Color.rgb(255,229,157);paint.textSize=w*.022f;c.drawText("NÍVEL $level   •   ✦ $coins",w*.5f,h*.216f,paint)
    val lp=(score%500)/500f;val lb=RectF(w*.25f,h*.226f,w*.75f,h*.234f);rounded(c,lb,6f,Color.argb(100,220,245,220));if(lp>0f)drawPremiumCard(c,RectF(lb.left,lb.top,lb.left+lb.width()*lp,lb.bottom),5f,Color.rgb(255,230,143),Color.rgb(232,161,63))
    val shopButton=RectF(w*.715f,h*.125f,w*.835f,h*.205f);drawPremiumCard(c,shopButton,17f,Color.rgb(255,224,139),Color.rgb(203,133,48));paint.color=Color.rgb(62,67,39);paint.textSize=w*.026f;c.drawText("LOJA",shopButton.centerX(),shopButton.centerY()+w*.01f,paint)
    val button=RectF(w*.855f,h*.125f,w*.965f,h*.205f);drawPremiumCard(c,button,17f,Color.rgb(92,177,137),Color.rgb(30,104,83));paint.color=Color.WHITE;paint.textSize=w*.041f;c.drawText("Ⅱ",button.centerX(),button.centerY()+w*.014f,paint)
}

    private fun drawGardenShortcut(c: Canvas, w: Float, h: Float) {
        val button=RectF(w*.035f,h*.125f,w*.145f,h*.205f)
        drawPremiumCard(c,button,17f,Color.rgb(64,158,110),Color.rgb(24,91,75))
        paint.color=Color.WHITE;paint.textAlign=Paint.Align.CENTER
        paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD);paint.textSize=w*.045f
        c.drawText("✿",button.centerX(),button.centerY()+w*.015f,paint)
    }

    private fun drawGardenMiniature(c: Canvas, index: Int, stage: Int, rect: RectF) {
        val save=c.save()
        val clipPath=android.graphics.Path().apply{addRoundRect(rect,22f,22f,android.graphics.Path.Direction.CW)}
        c.clipPath(clipPath)
        val sky=when(index){0->intArrayOf(Color.rgb(116,196,235),Color.rgb(224,241,208));1->intArrayOf(Color.rgb(82,150,188),Color.rgb(199,224,177));else->intArrayOf(Color.rgb(99,185,224),Color.rgb(218,239,222))}
        paint.shader=LinearGradient(rect.left,rect.top,rect.left,rect.bottom,sky[0],sky[1],Shader.TileMode.CLAMP)
        c.drawRect(rect,paint);paint.shader=null
        drawSphere3D(c,rect.right-rect.width()*.16f,rect.top+rect.height()*.20f,rect.width()*.075f,rect.height()*.13f,Color.rgb(255,221,116))
        val hill=android.graphics.Path().apply{moveTo(rect.left,rect.top+rect.height()*.62f);cubicTo(rect.left+rect.width()*.24f,rect.top+rect.height()*.30f,rect.left+rect.width()*.48f,rect.top+rect.height()*.72f,rect.right,rect.top+rect.height()*.40f);lineTo(rect.right,rect.bottom);lineTo(rect.left,rect.bottom);close()}
        paint.shader=LinearGradient(rect.top,rect.top+rect.height()*.45f,rect.top,rect.bottom,Color.rgb(119,183,104),Color.rgb(43,119,67),Shader.TileMode.CLAMP)
        c.drawPath(hill,paint);paint.shader=null
        val ground=rect.bottom-rect.height()*.13f
        if(index==2){
            paint.color=Color.rgb(76,154,183);c.drawOval(rect.left+rect.width()*.38f,rect.top+rect.height()*.53f,rect.right+rect.width()*.08f,rect.bottom+rect.height()*.10f,paint)
            paint.color=Color.argb(150,214,247,244);c.drawLine(rect.left+rect.width()*.48f,rect.top+rect.height()*.70f,rect.right-rect.width()*.10f,rect.top+rect.height()*.68f,paint)
            drawRock3D(c,rect.left+rect.width()*.42f,ground,rect.width()*.07f,rect.height()*.09f)
            drawRock3D(c,rect.right-rect.width()*.12f,ground-rect.height()*.02f,rect.width()*.06f,rect.height()*.08f)
        }else{
            paint.color=Color.rgb(190,142,83)
            val path=android.graphics.Path().apply{moveTo(rect.left+rect.width()*.38f,rect.bottom);cubicTo(rect.left+rect.width()*.55f,rect.top+rect.height()*.77f,rect.left+rect.width()*.28f,rect.top+rect.height()*.63f,rect.left+rect.width()*.53f,rect.top+rect.height()*.53f);cubicTo(rect.left+rect.width()*.70f,rect.top+rect.height()*.46f,rect.left+rect.width()*.78f,rect.top+rect.height()*.55f,rect.right,rect.top+rect.height()*.48f);lineTo(rect.right,rect.bottom);close()}
            c.drawPath(path,paint)
        }
        val leaf=when(index){0->Color.rgb(52,159,76);1->Color.rgb(30,119,68);else->Color.rgb(42,142,78)}
        drawTree3D(c,rect.left+rect.width()*.18f,ground,rect.height()*.24f,leaf,1.05f)
        drawTree3D(c,rect.left+rect.width()*.78f,ground,rect.height()*.21f,if(index==1)Color.rgb(21,102,61)else leaf,.95f)
        if(index==1){
            drawTree3D(c,rect.left+rect.width()*.48f,ground-rect.height()*.08f,rect.height()*.28f,Color.rgb(34,125,65),1.12f)
            drawBush3D(c,rect.left+rect.width()*.32f,ground,rect.height()*.12f)
            drawBush3D(c,rect.left+rect.width()*.65f,ground,rect.height()*.10f)
        }else{
            for(i in 0..6){
                val fx=rect.left+rect.width()*(.30f+i*.085f)
                drawFlower3D(c,fx,ground-rect.height()*(if(i%2==0).03f else .01f),rect.height()*.075f,
                    when(i%4){0->Color.rgb(245,87,135);1->Color.rgb(255,205,65);2->Color.rgb(153,112,222);else->Color.WHITE})
            }
            if(index==2){drawBush3D(c,rect.left+rect.width()*.30f,ground,rect.height()*.12f);drawBush3D(c,rect.right-rect.width()*.22f,ground,rect.height()*.12f)}
        }
        for(i in 0 until stage.coerceIn(0,3)){
            drawSphere3D(c,rect.left+rect.width()*(.12f+i*.10f),rect.top+rect.height()*.86f,rect.width()*.025f,rect.height()*.045f,Color.rgb(255,221,126))
        }
        paint.color=Color.argb(90,255,255,255);c.drawLine(rect.left,rect.top+2f,rect.right,rect.top+2f,paint)
        c.restoreToCount(save)
        paint.shader=null;paint.style=Paint.Style.FILL
    }

    private fun drawGardenDetailScreen(c: Canvas, w: Float, h: Float) {
    val index=selectedGarden.coerceIn(0,2);val stage=gardenStages[index];val unlocked=index==0||gardenStages[index-1]>=3
    drawPremiumCard(c,RectF(w*.055f,h*.025f,w*.945f,h*.17f),27f,Color.rgb(255,249,221),Color.rgb(221,239,195))
    paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD);paint.color=Color.rgb(48,105,62);paint.textSize=w*.052f;c.drawText(gardenNames[index],w*.5f,h*.095f,paint)
    paint.color=Color.rgb(172,119,39);paint.textSize=w*.025f;c.drawText("✦  $coins MOEDAS",w*.5f,h*.145f,paint)
    val scene=RectF(w*.075f,h*.195f,w*.925f,h*.625f);drawPremiumCard(c,scene,26f,Color.rgb(201,231,177),Color.rgb(105,171,100));drawGardenMiniature(c,index,stage,RectF(scene.left+5f,scene.top+5f,scene.right-5f,scene.bottom-5f))
    drawPremiumCard(c,RectF(w*.12f,h*.64f,w*.88f,h*.715f),17f,Color.rgb(255,248,220),Color.rgb(224,239,199))
    paint.color=Color.rgb(57,111,63);paint.textSize=w*.032f;c.drawText("ETAPA $stage DE 3",w*.5f,h*.686f,paint)
    paint.textSize=w*.023f;paint.color=Color.rgb(83,125,72);c.drawText(gardenDescriptions[index],w*.5f,h*.745f,paint)
    val progress=RectF(w*.18f,h*.765f,w*.82f,h*.785f);rounded(c,progress,10f,Color.argb(160,125,158,104));if(stage>0)drawPremiumCard(c,RectF(progress.left,progress.top,progress.left+progress.width()*stage/3f,progress.bottom),9f,Color.rgb(145,211,111),Color.rgb(59,145,77))
    val action=RectF(w*.12f,h*.805f,w*.88f,h*.875f);val canRestore=unlocked&&stage<3&&coins>=gardenCosts[index][stage];val label=when{!unlocked->"JARDIM BLOQUEADO";stage>=3->"JARDIM RESTAURADO!";else->"RESTAURAR  •  "+gardenCosts[index][stage]+" MOEDAS"}
    drawPremiumCard(c,action,20f,if(canRestore)Color.rgb(133,202,111)else Color.rgb(182,195,163),if(canRestore)Color.rgb(54,143,75)else Color.rgb(124,150,112));paint.color=Color.WHITE;paint.textSize=w*.028f;paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD);c.drawText(label,action.centerX(),action.centerY()+w*.01f,paint)
    val back=RectF(w*.25f,h*.895f,w*.75f,h*.955f);drawPremiumCard(c,back,16f,Color.rgb(112,185,111),Color.rgb(46,129,76));paint.color=Color.WHITE;paint.textSize=w*.029f;c.drawText("VOLTAR AOS JARDINS",back.centerX(),back.centerY()+w*.01f,paint)
}

    private fun drawGardensScreen(c: Canvas, w: Float, h: Float) {
    val head=RectF(w*.055f,h*.025f,w*.945f,h*.15f);drawPremiumCard(c,head,28f,Color.rgb(255,248,218),Color.rgb(224,242,194))
    paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD);paint.color=Color.rgb(47,111,67);paint.textSize=w*.058f;c.drawText("MUNDO JARDIM",w*.5f,h*.083f,paint)
    paint.color=Color.rgb(89,133,75);paint.textSize=w*.023f;paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);c.drawText("Explore, restaure e desbloqueie novas terras",w*.5f,h*.124f,paint)
    drawPremiumCard(c,RectF(w*.66f,h*.16f,w*.94f,h*.211f),15f,Color.rgb(255,230,145),Color.rgb(228,164,69));paint.color=Color.rgb(91,76,38);paint.textSize=w*.025f;c.drawText("✦  $coins moedas",w*.80f,h*.194f,paint)
    val centersY=floatArrayOf(.295f,.49f,.685f)
    for(i in 0..2){val cy=h*centersY[i];val unlocked=i==0||gardenStages[i-1]>=3;val stage=gardenStages[i];val panel=RectF(w*.055f,cy-h*.075f,w*.945f,cy+h*.075f)
        drawPremiumCard(c,panel,23f,Color.rgb(255,250,226),Color.rgb(216,237,194))
        val thumb=RectF(panel.left+6f,panel.top+6f,panel.left+w*.31f,panel.bottom-6f);drawGardenMiniature(c,i,stage,thumb)
        paint.textAlign=Paint.Align.LEFT;paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD);paint.color=Color.rgb(45,100,59);paint.textSize=w*.035f;c.drawText(gardenNames[i],w*.40f,cy-h*.012f,paint)
        paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.color=Color.rgb(91,125,74);paint.textSize=w*.022f;c.drawText(if(!unlocked)"Conclua a região anterior" else "Restauração: $stage de 3",w*.40f,cy+h*.028f,paint)
        paint.textAlign=Paint.Align.RIGHT;paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD);paint.color=if(!unlocked)Color.rgb(130,130,115)else if(stage>=3)Color.rgb(184,125,35)else Color.rgb(48,135,73);paint.textSize=w*.022f;c.drawText(if(!unlocked)"BLOQUEADO"else if(stage>=3)"CONCLUÍDO ★"else "ABRIR  ›",w*.90f,cy+h*.058f,paint)
    }
    drawPremiumCard(c,RectF(w*.055f,h*.785f,w*.945f,h*.885f),22f,Color.rgb(255,248,220),Color.rgb(218,237,192))
    paint.textAlign=Paint.Align.LEFT;paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD);paint.color=Color.rgb(54,105,59);paint.textSize=w*.024f
    c.drawText("MISSÃO DIÁRIA  •  $dailyLines/5 linhas",w*.09f,h*.818f,paint);c.drawText("MISSÃO SEMANAL  •  $weeklyLines/20 linhas",w*.09f,h*.858f,paint)
    val dr=dailyLines>=5&&!dailyRewardClaimed;val wr=weeklyLines>=20&&!weeklyRewardClaimed
    drawPremiumCard(c,RectF(w*.70f,h*.795f,w*.90f,h*.835f),10f,if(dr)Color.rgb(127,199,105)else Color.rgb(176,194,153),if(dr)Color.rgb(54,145,73)else Color.rgb(113,145,102))
    drawPremiumCard(c,RectF(w*.70f,h*.838f,w*.90f,h*.878f),10f,if(wr)Color.rgb(127,199,105)else Color.rgb(176,194,153),if(wr)Color.rgb(54,145,73)else Color.rgb(113,145,102))
    paint.textAlign=Paint.Align.CENTER;paint.textSize=w*.019f;paint.color=Color.WHITE;c.drawText(if(dailyRewardClaimed)"RESGATADA"else"+100 MOEDAS",w*.80f,h*.821f,paint);c.drawText(if(weeklyRewardClaimed)"RESGATADA"else"+250 MOEDAS",w*.80f,h*.864f,paint)
    drawPremiumCard(c,RectF(w*.27f,h*.90f,w*.73f,h*.955f),17f,Color.rgb(111,190,105),Color.rgb(48,132,75));paint.textSize=w*.028f;paint.color=Color.WHITE;c.drawText("VOLTAR AO JOGO",w*.5f,h*.936f,paint)
}
    private fun refreshDailyMission() {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val savedDay = prefs.getString("daily_mission_day", "")
        if (savedDay != today) {
            dailyLines = 0
            dailyRewardClaimed = false
            prefs.edit().putString("daily_mission_day", today)
                .putInt("daily_lines", 0).putBoolean("daily_reward_claimed", false).apply()
        }
    }

    private fun refreshWeeklyMission() {
        val week = SimpleDateFormat("YYYYww", Locale.US).format(Date())
        val savedWeek = prefs.getString("weekly_mission_week", "")
        if (savedWeek != week) {
            weeklyLines = 0
            weeklyRewardClaimed = false
            prefs.edit().putString("weekly_mission_week", week)
                .putInt("weekly_lines", 0).putBoolean("weekly_reward_claimed", false).apply()
        }
    }

    private fun saveGardenProgress() {
        val editor = prefs.edit().putInt("garden_coins", coins)
            .putInt("daily_lines", dailyLines)
            .putBoolean("daily_reward_claimed", dailyRewardClaimed)
            .putInt("weekly_lines", weeklyLines)
            .putBoolean("weekly_reward_claimed", weeklyRewardClaimed)
        for (i in 0..2) editor.putInt("garden_stage_$i", gardenStages[i])
        editor.apply()
    }

    private fun restoreGarden(index: Int) {
        if (index !in 0..2) return
        val unlocked = index == 0 || gardenStages[index - 1] >= 3
        val stage = gardenStages[index]
        if (!unlocked || stage >= 3) return
        val cost = gardenCosts[index][stage]
        if (coins >= cost) {
            coins -= cost
            gardenStages[index]++
            saveGardenProgress()
            playTone(ToneGenerator.TONE_PROP_ACK, 120)
            performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
        } else {
            playTone(ToneGenerator.TONE_PROP_BEEP, 80)
        }
    }

    private fun drawStat(c: Canvas, x: Float, y: Float, label: String, value: String) {
        val box=RectF(x-width*.17f,y-height*.026f,x+width*.17f,y+height*.047f)
        drawPremiumCard(c,box,height*.018f,Color.argb(225,16,68,57),Color.argb(220,6,38,37))
        paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD)
        paint.color=Color.rgb(177,226,190);paint.textSize=width*.021f
        c.drawText(label,x,y,paint)
        paint.color=Color.WHITE;paint.textSize=width*.041f
        c.drawText(value,x,y+height*.039f,paint)
    }

    private fun drawBoard(c: Canvas) {
    val outer=RectF(boardLeft-10f,boardTop-10f,boardLeft+boardSize+10f,boardTop+boardSize+10f)
    paint.color=Color.argb(95,14,47,35);c.drawRoundRect(RectF(outer.left,outer.top+8f,outer.right,outer.bottom+10f),30f,30f,paint)
    drawPremiumCard(c,outer,28f,Color.rgb(184,220,157),Color.rgb(89,151,103))
    val inner=RectF(outer.left+6f,outer.top+6f,outer.right-6f,outer.bottom-6f);paint.shader=LinearGradient(inner.left,inner.top,inner.right,inner.bottom,Color.rgb(225,242,197),Color.rgb(179,216,164),Shader.TileMode.CLAMP);c.drawRoundRect(inner,23f,23f,paint);paint.shader=null
    for(r in 0..8)for(col in 0..8){val left=boardLeft+col*cell;val top=boardTop+r*cell;val rect=RectF(left+2.2f,top+2.2f,left+cell-2.2f,top+cell-2.2f)
        if(board[r][col])drawBlock(c,rect,colors[r][col],cell*.17f)else{paint.color=Color.argb(75,93,143,94);c.drawRoundRect(rect,cell*.16f,cell*.16f,paint);paint.color=Color.argb(35,255,255,255);c.drawRoundRect(RectF(rect.left+cell*.12f,rect.top+cell*.10f,rect.right-cell*.12f,rect.top+cell*.17f),cell*.05f,cell*.05f,paint)}}
    if(dragging&&previewRow>=0&&previewCol>=0){val p=pieces.getOrNull(selected);if(p!=null)p.cells.forEach{(dx,dy)->val row=previewRow+dy;val col=previewCol+dx;if(row in 0..8&&col in 0..8){val rect=RectF(boardLeft+col*cell+2f,boardTop+row*cell+2f,boardLeft+(col+1)*cell-2f,boardTop+(row+1)*cell-2f);drawBlock(c,rect,if(previewValid)p.color else Color.rgb(235,75,75),cell*.15f,if(previewValid)165 else 180)}}}
}

    private fun drawBlock(c: Canvas, rect: RectF, base: Int, radius: Float, alpha: Int = 255) {
    val inset=rect.width()*.075f
    val face=RectF(rect.left+inset,rect.top+inset,rect.right-inset,rect.bottom-inset)
    val dark=Color.rgb((Color.red(base)*.58f).toInt(),(Color.green(base)*.58f).toInt(),(Color.blue(base)*.58f).toInt())
    val light=Color.rgb((Color.red(base)+((255-Color.red(base))*.35f)).toInt(),(Color.green(base)+((255-Color.green(base))*.35f)).toInt(),(Color.blue(base)+((255-Color.blue(base))*.35f)).toInt())
    paint.color=Color.argb(alpha/3,0,20,20);c.drawRoundRect(RectF(face.left+inset*.3f,face.top+inset*1.8f,face.right+inset*.3f,face.bottom+inset*1.8f),radius,radius,paint)
    paint.shader=LinearGradient(face.left,face.top,face.right,face.bottom,light,dark,Shader.TileMode.CLAMP);paint.alpha=alpha;c.drawRoundRect(face,radius,radius,paint);paint.shader=null;paint.alpha=255
    paint.style=Paint.Style.STROKE;paint.strokeWidth=max(1.2f,rect.width()*.025f);paint.color=withAlpha(Color.WHITE,alpha/3);c.drawRoundRect(face,radius,radius,paint);paint.style=Paint.Style.FILL
    paint.shader=LinearGradient(face.left,face.top,face.left,face.top+face.height()*.45f,withAlpha(Color.WHITE,alpha/3),withAlpha(Color.TRANSPARENT,alpha),Shader.TileMode.CLAMP);c.drawRoundRect(RectF(face.left+2f,face.top+2f,face.right-2f,face.top+face.height()*.48f),radius*.8f,radius*.8f,paint);paint.shader=null
    paint.color=withAlpha(Color.WHITE,alpha/5);c.drawCircle(face.left+face.width()*.22f,face.top+face.height()*.22f,face.width()*.055f,paint)
}

    private fun drawPiece(c: Canvas, piece: Piece, cx: Float, cy: Float, size: Float) {
        val maxX=piece.cells.maxOf{it.first}+1;val maxY=piece.cells.maxOf{it.second}+1
        val unit=min(size*2.35f/maxX,size*2.35f/maxY)
        piece.cells.forEach{(x,y)->
            val left=cx-maxX*unit/2f+x*unit;val top=cy-maxY*unit/2f+y*unit
            drawBlock(c,RectF(left,top,left+unit-3f,top+unit-3f),piece.color,7f)
        }
    }

    private fun drawPieceTray(c: Canvas, w: Float, h: Float) {
    paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD);paint.color=Color.rgb(48,101,65);paint.textSize=w*.026f;c.drawText("ESCOLHA UMA PEÇA",w*.5f,slotsTop-h*.020f,paint)
    val slotWidth=w*.275f
    for(i in 0..2){val centerX=w*(.18f+i*.32f);val active=i==selected&&pieces[i]!=null;val rect=RectF(centerX-slotWidth/2f,trayCenterY-h*.047f,centerX+slotWidth/2f,trayCenterY+h*.047f)
        if(pieces[i]==null)drawPremiumCard(c,rect,20f,Color.rgb(202,221,185),Color.rgb(157,191,147))else if(active)drawPremiumCard(c,rect,20f,Color.rgb(255,232,157),Color.rgb(228,166,75))else drawPremiumCard(c,rect,20f,Color.rgb(207,235,191),Color.rgb(123,183,132))
        val piece=pieces[i];if(piece!=null&&!(dragging&&i==selected))drawPiece(c,piece,centerX,trayCenterY-h*.004f,min(cell*.37f,h*.025f))else if(piece==null){paint.color=Color.rgb(62,120,70);paint.textSize=w*.045f;c.drawText("✓",centerX,trayCenterY+h*.012f,paint)}
    }
    if(comboAlpha>0f){paint.color=withAlpha(Color.rgb(224,125,35),(comboAlpha*255).toInt());paint.textSize=w*.040f;paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD);c.drawText(comboLabel,w*.5f,trayCenterY+h*.105f,paint)}
}

    private fun drawFooter(c: Canvas, w: Float, h: Float) {
    val hint=when(activePower){"hammer"->"MARTELO ATIVO  •  TOQUE EM UM BLOCO";"row"->"BOMBA DE LINHA ATIVA  •  TOQUE NA LINHA";else->"ARRASTE UMA PEÇA PARA O TABULEIRO"}
    val panel=RectF(w*.10f,h*.925f,w*.90f,h*.975f);drawPremiumCard(c,panel,18f,Color.rgb(229,242,204),Color.rgb(173,211,161))
    paint.color=if(activePower.isNotEmpty())Color.rgb(143,83,25)else Color.rgb(47,105,62);paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD);paint.textSize=w*.022f;c.drawText(hint,w*.5f,h*.956f,paint)
}

    private fun drawShopScreen(c: Canvas, w: Float, h: Float) {
        drawDim(c,w,h)
        drawPremiumCard(c,RectF(w*.045f,h*.055f,w*.955f,h*.945f),30f,Color.rgb(247,239,207),Color.rgb(213,218,179))
        paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("serif",Typeface.BOLD);paint.color=Color.rgb(46,91,66);paint.textSize=w*.060f
        c.drawText("ARSENAL DO JARDINEIRO",w*.5f,h*.125f,paint)
        paint.textSize=w*.026f;paint.color=Color.rgb(156,105,45);c.drawText("✦  $coins MOEDAS DISPONÍVEIS",w*.5f,h*.17f,paint)
        val names=listOf("Martelo","Bomba de linha","Nova mão")
        val descriptions=listOf("Destrói um bloco escolhido","Libera uma linha inteira","Renova suas três peças")
        val counts=intArrayOf(hammerCount,rowBombCount,rerollCount);val prices=intArrayOf(80,120,60)
        val icons=listOf("⚒","✹","↻");val hues=intArrayOf(Color.rgb(213,142,74),Color.rgb(202,94,73),Color.rgb(76,151,166))
        for(i in 0..2){
            val top=h*(.225f+i*.185f);drawPremiumCard(c,RectF(w*.085f,top,w*.915f,top+h*.155f),20f,Color.rgb(255,252,231),Color.rgb(230,229,194))
            paint.color=hues[i];c.drawCircle(w*.19f,top+h*.075f,w*.052f,paint);paint.color=Color.WHITE;paint.textSize=w*.052f;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD);c.drawText(icons[i],w*.19f,top+h*.093f,paint)
            paint.textAlign=Paint.Align.LEFT;paint.typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD);paint.color=Color.rgb(47,91,64);paint.textSize=w*.035f;c.drawText(names[i],w*.29f,top+h*.055f,paint)
            paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.color=Color.rgb(103,111,87);paint.textSize=w*.021f;c.drawText(descriptions[i],w*.29f,top+h*.090f,paint)
            paint.typeface=Typeface.create("sans-serif",Typeface.BOLD);paint.color=Color.rgb(164,111,43);paint.textSize=w*.024f;c.drawText("NO INVENTÁRIO: "+counts[i],w*.29f,top+h*.128f,paint)
            val btn=RectF(w*.68f,top+h*.045f,w*.87f,top+h*.115f);drawPremiumCard(c,btn,12f,if(counts[i]>0)Color.rgb(93,170,105)else Color.rgb(242,196,91),if(counts[i]>0)Color.rgb(48,119,73)else Color.rgb(198,135,48))
            paint.textAlign=Paint.Align.CENTER;paint.color=Color.WHITE;paint.textSize=w*.023f;c.drawText(if(counts[i]>0)"USAR"else prices[i].toString()+" ✦",btn.centerX(),btn.centerY()+w*.008f,paint)
        }
        val back=RectF(w*.23f,h*.825f,w*.77f,h*.90f);drawPremiumCard(c,back,18f,Color.rgb(67,151,91),Color.rgb(33,103,67))
        paint.textAlign=Paint.Align.CENTER;paint.color=Color.WHITE;paint.textSize=w*.029f;c.drawText("VOLTAR AO JOGO",w*.5f,h*.87f,paint)
    }

    private fun savePowerInventory() {
        prefs.edit().putInt("power_hammer",hammerCount)
            .putInt("power_row_bomb",rowBombCount)
            .putInt("power_reroll",rerollCount).apply()
        saveGardenProgress()
    }

    private fun activateShopItem(index:Int) {
        val counts=intArrayOf(hammerCount,rowBombCount,rerollCount)
        val prices=intArrayOf(80,120,60)
        if(counts[index]>0){
            when(index){
                0->{activePower="hammer";showShop=false}
                1->{activePower="row";showShop=false}
                2->{rerollCount--;showShop=false;pieces=MutableList(3){newPiece()};selected=0;gameOver=false;emitBurst(width*.5f,height*.52f,Color.rgb(255,220,125),28,1.7f);animateEffects();playPowerSound("reroll")}
            }
            savePowerInventory()
            playTone(ToneGenerator.TONE_PROP_ACK,130)
            comboLabel=when(index){0->"MARTELO ATIVO";1->"BOMBA DE LINHA ATIVA";else->"NOVAS PEÇAS!"}
        } else if(coins>=prices[index]){
            coins-=prices[index]
            when(index){0->hammerCount++;1->rowBombCount++;2->rerollCount++}
            savePowerInventory()
            playTone(ToneGenerator.TONE_PROP_ACK,130)
        } else {
            playTone(ToneGenerator.TONE_PROP_BEEP,90)
        }
    }

    private fun usePowerAt(x:Float,y:Float) {
        if (activePower.isEmpty()) return
        if (x < boardLeft || x >= boardLeft + boardSize || y < boardTop || y >= boardTop + boardSize) {
            playTone(ToneGenerator.TONE_PROP_BEEP, 55)
            return
        }
        val row = ((y - boardTop) / cell).toInt().coerceIn(0, 8)
        val col = ((x - boardLeft) / cell).toInt().coerceIn(0, 8)
        val powerUsed = activePower
        var removed = 0
        if (powerUsed == "hammer") {
            if (board[row][col]) {
                emitBurst(boardLeft + (col + .5f) * cell, boardTop + (row + .5f) * cell, colors[row][col], 18, 1.25f)
                board[row][col] = false
                colors[row][col] = Color.TRANSPARENT
                removed = 1
            }
        } else if (powerUsed == "row") {
            for (c in 0..8) if (board[row][c]) {
                emitBurst(boardLeft + (c + .5f) * cell, boardTop + (row + .5f) * cell, colors[row][c], 9, 1.1f)
                board[row][c] = false
                colors[row][c] = Color.TRANSPARENT
                removed++
            }
        }
        if (removed > 0) {
            score += removed * 10
            if (score > best) { best = score; prefs.edit().putInt("best_score", best).apply() }
            level = 1 + score / 500
            if (powerUsed == "hammer") hammerCount-- else rowBombCount--
            activePower = ""
            savePowerInventory()
            animateEffects()
            playPowerSound(powerUsed)
            gameOver = !availablePiecesHaveMove()
        } else {
            // O item continua disponível se a célula ou linha estiver vazia.
            playTone(ToneGenerator.TONE_PROP_BEEP, 65)
        }
        invalidate()
    }

    private fun drawSparks(c: Canvas) {
        val t = effectProgress
        blastCenters.forEach { blast ->
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(2f, cell * .055f * (1f - t * .45f))
            paint.color = withAlpha(blast.color, (190f * (1f - t)).toInt().coerceIn(0, 190))
            c.drawCircle(blast.x, blast.y, cell * (.25f + t * 2.1f), paint)
            paint.style = Paint.Style.FILL
        }
        sparks.forEach { particle ->
            val px = particle.x + particle.vx * t
            val py = particle.y + particle.vy * t + height * .12f * t * t
            paint.color = withAlpha(particle.color, (255f * (1f - t)).toInt().coerceIn(0, 255))
            c.save()
            c.rotate(particle.rotation + t * 250f, px, py)
            val half = particle.size * (1f - t * .45f)
            c.drawRoundRect(RectF(px-half, py-half*.65f, px+half, py+half*.65f), half*.18f, half*.18f, paint)
            c.restore()
        }
        paint.style = Paint.Style.FILL
    }

    private fun emitBurst(x: Float, y: Float, color: Int, amount: Int = 12, spread: Float = 1f) {
        blastCenters = (blastCenters + Blast(x, y, color)).takeLast(24)
        val fragments = (0 until amount).map {
            val angle = Random.nextFloat() * (Math.PI.toFloat() * 2f)
            val speed = cell * (.35f + Random.nextFloat() * 1.15f) * spread
            Spark(x, y, color, cell * (.055f + Random.nextFloat() * .07f),
                kotlin.math.cos(angle) * speed, kotlin.math.sin(angle) * speed - cell * .22f,
                Random.nextFloat() * 180f)
        }
        sparks = (sparks + fragments).takeLast(220)
    }

    private fun drawDim(c: Canvas, w: Float, h: Float) {
        paint.shader=LinearGradient(0f,0f,w,h,intArrayOf(Color.argb(205,3,17,29),Color.argb(190,5,35,38)),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,w,h,paint);paint.shader=null
    }

    private fun drawPanel(c: Canvas, w: Float, top: Float, bottom: Float) {
        val rect=RectF(w*.055f,top,w*.945f,bottom)
        drawPremiumCard(c,rect,30f,Color.rgb(33,91,88),Color.rgb(10,43,56))
    }

    private fun drawTutorial(c: Canvas, w: Float, h: Float) {
        drawDim(c, w, h)
        drawPanel(c, w, h * .30f, h * .68f)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.color = Color.rgb(47, 91, 64)
        paint.textSize = w * .060f
        c.drawText("Como jogar", w / 2f, h * .385f, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = w * .035f
        c.drawText("1. Escolha uma das três peças.", w / 2f, h * .455f, paint)
        c.drawText("2. Arraste para um espaço livre.", w / 2f, h * .505f, paint)
        c.drawText("3. Complete linhas ou colunas.", w / 2f, h * .555f, paint)
        c.drawText("Use todas as peças para receber outras.", w / 2f, h * .605f, paint)
        drawButton(c, w, h * .625f, "COMEÇAR", true)
    }

    private fun drawPauseMenu(c: Canvas, w: Float, h: Float) {
        drawDim(c, w, h)
        drawPanel(c, w, h * .25f, h * .77f)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.color = Color.rgb(47, 91, 64)
        paint.textSize = w * .061f
        c.drawText("Jogo pausado", w / 2f, h * .355f, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = w * .032f
        c.drawText("Partidas: $gamesPlayed   •   Linhas: $totalLines", w / 2f, h * .405f, paint)
        c.drawText("Melhor combo: $bestCombo linhas", w / 2f, h * .445f, paint)
        drawButton(c, w, h * .505f, "CONTINUAR", true)
        drawButton(c, w, h * .585f, if (soundEnabled) "SOM: LIGADO" else "SOM: DESLIGADO", false)
        drawButton(c, w, h * .635f, "REINICIAR PARTIDA", false)
        drawButton(c, w, h * .715f, "MENU PRINCIPAL", false)
    }

    private fun drawRestartConfirmation(c: Canvas, w: Float, h: Float) {
        drawDim(c, w, h)
        drawPanel(c, w, h * .37f, h * .62f)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.color = Color.rgb(47, 91, 64)
        paint.textSize = w * .050f
        c.drawText("Recomeçar partida?", w / 2f, h * .445f, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = w * .031f
        c.drawText("Seu progresso nesta partida será perdido.", w / 2f, h * .49f, paint)
        drawButton(c, w, h * .535f, "SIM, RECOMEÇAR", true)
        drawButton(c, w, h * .595f, "CANCELAR", false)
    }

    private fun drawGameOver(c: Canvas, w: Float, h: Float) {
        drawDim(c, w, h)
        drawPanel(c, w, h * .34f, h * .66f)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.color = Color.rgb(47, 91, 64)
        paint.textSize = w * .066f
        c.drawText("Fim de jogo", w / 2f, h * .415f, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = w * .035f
        c.drawText("Pontuação: $score", w / 2f, h * .465f, paint)
        c.drawText("Recorde: $best", w / 2f, h * .505f, paint)
        if (activeExpedition) {
            paint.color = Color.rgb(174,116,43)
            paint.textSize = w*.027f
            c.drawText("Meta da etapa: "+expeditionGoals[expeditionStage]+" pontos",w/2f,h*.535f,paint)
        }
        drawButton(c, w, h * .565f, if(activeExpedition) "TENTAR NOVAMENTE" else "JOGAR NOVAMENTE", true)
        drawButton(c, w, h * .655f, "MENU PRINCIPAL", false)
    }

    private fun drawButton(c: Canvas, w: Float, centerY: Float, label: String, primary: Boolean) {
        val rect=RectF(w*.17f,centerY-height*.030f,w*.83f,centerY+height*.030f)
        if(primary)drawPremiumCard(c,rect,20f,Color.rgb(255,229,151),Color.rgb(205,143,52))
        else drawPremiumCard(c,rect,20f,Color.rgb(83,151,103),Color.rgb(34,101,68))
        paint.color=if(primary)Color.rgb(61,66,39)else Color.WHITE
        paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("sans-serif-condensed",Typeface.BOLD)
        paint.textSize=w*.033f
        c.drawText(label,rect.centerX(),rect.centerY()+w*.011f,paint)
    }

    private fun drawPremiumCard(c: Canvas, rect: RectF, radius: Float, topColor: Int, bottomColor: Int) {
        paint.shader=null;paint.style=Paint.Style.FILL
        paint.color=Color.argb(105,0,9,18)
        c.drawRoundRect(RectF(rect.left+1f,rect.top+6f,rect.right+2f,rect.bottom+8f),radius,radius,paint)
        paint.shader=LinearGradient(rect.left,rect.top,rect.right,rect.bottom,topColor,bottomColor,Shader.TileMode.CLAMP)
        c.drawRoundRect(rect,radius,radius,paint);paint.shader=null
        paint.style=Paint.Style.STROKE;paint.strokeWidth=max(1.4f,width*.0022f)
        paint.color=Color.argb(155,232,250,229)
        c.drawRoundRect(RectF(rect.left+1f,rect.top+1f,rect.right-1f,rect.bottom-1f),radius,radius,paint)
        paint.style=Paint.Style.FILL
        paint.shader=LinearGradient(rect.left,rect.top,rect.left,rect.top+rect.height()*.48f,
            Color.argb(65,255,255,255),Color.TRANSPARENT,Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(rect.left+3f,rect.top+2f,rect.right-3f,rect.top+rect.height()*.50f),radius,radius,paint)
        paint.shader=null
        paint.color=Color.argb(32,255,255,255)
        c.drawRoundRect(RectF(rect.left+radius*.55f,rect.top+radius*.18f,rect.right-radius*.55f,rect.top+radius*.18f+max(2f,rect.height()*.018f)),radius,radius,paint)
    }

    private fun rounded(c: Canvas, rect: RectF, radius: Float, color: Int) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawRoundRect(rect, radius, radius, paint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun fits(piece: Piece, row: Int, col: Int): Boolean = piece.cells.all { (dx, dy) ->
        row + dy in 0..8 && col + dx in 0..8 && !board[row + dy][col + dx]
    }

    private fun hasAnyMove(piece: Piece): Boolean =
        (0..8).any { row -> (0..8).any { col -> fits(piece, row, col) } }

    private fun availablePiecesHaveMove(): Boolean =
        pieces.filterNotNull().any { hasAnyMove(it) }

    private fun playTone(toneType: Int, duration: Int = 100) {
        if (soundEnabled) {
            try { tone.startTone(toneType, duration) } catch (_: Exception) { }
        }
    }

    private fun playPowerSound(power: String) {
        if (!soundEnabled) return
        try {
            when (power) {
                "hammer" -> { tone.startTone(ToneGenerator.TONE_PROP_NACK, 115); performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) }
                "row" -> { tone.startTone(ToneGenerator.TONE_PROP_ACK, 260); performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM) }
                "reroll" -> { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 90); performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP) }
                "line" -> { tone.startTone(ToneGenerator.TONE_PROP_ACK, 230); performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM) }
            }
        } catch (_: Exception) { }
    }

    private fun placePiece(piece: Piece, row: Int, col: Int) {
        piece.cells.forEach { (dx, dy) ->
            board[row + dy][col + dx] = true
            colors[row + dy][col + dx] = piece.color
        }
        score += piece.cells.size * 5
        val fullRows = (0..8).filter { r -> (0..8).all { board[r][it] } }
        val fullCols = (0..8).filter { c -> (0..8).all { r -> board[r][c] } }
        val clearedCells = linkedSetOf<Pair<Int, Int>>()
        fullRows.forEach { r -> (0..8).forEach { c -> clearedCells.add(r to c) } }
        fullCols.forEach { colIndex -> (0..8).forEach { r -> clearedCells.add(r to colIndex) } }

        val lines = fullRows.size + fullCols.size
        coins += piece.cells.size
        if (lines > 0) {
            coins += lines * 25
            refreshDailyMission()
            dailyLines += lines
            refreshWeeklyMission()
            weeklyLines += lines
            saveGardenProgress()
            clearedCells.forEach { (r, c) ->
                emitBurst(boardLeft + (c + .5f) * cell, boardTop + (r + .5f) * cell, colors[r][c], 5, .8f)
                board[r][c] = false
                colors[r][c] = Color.TRANSPARENT
            }
            val linePoints = lines * 100 + if (lines > 1) (lines - 1) * 75 else 0
            val comboBonus = if (lastMoveTime > 0L && System.currentTimeMillis() - lastMoveTime < 4500L) {
                min(lastComboLines, 4) * 25
            } else 0
            score += linePoints + comboBonus
            totalLines += lines
            bestCombo = max(bestCombo, lines)
            lastComboLines = lines
            lastMoveTime = System.currentTimeMillis()
            prefs.edit().putInt("total_lines", totalLines).putInt("best_combo", bestCombo).apply()
            comboLabel = when {
                lines >= 4 -> "INCRÍVEL! $lines LINHAS!"
                lines == 3 -> "MEGA COMBO! +$linePoints"
                lines == 2 -> "ÓTIMO COMBO! +$linePoints"
                else -> "+$linePoints"
            }
            comboAlpha = 1f
            animateEffects()
            playPowerSound("line")
        } else {
            playTone(ToneGenerator.TONE_PROP_BEEP, 55)
            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }
        saveGardenProgress()

        if (score > best) {
            best = score
            prefs.edit().putInt("best_score", best).apply()
        }
        level = 1 + score / 500
        pieces[selected] = null
        if (pieces.all { it == null }) {
            pieces = MutableList(3) { newPiece() }
            selected = 0
        } else if (pieces[selected] == null) {
            selected = pieces.indexOfFirst { it != null }.coerceAtLeast(0)
        }
        if (activeExpedition && score >= expeditionGoals[expeditionStage]) {
            activeExpedition = false
            showExpeditionComplete = true
            coins += expeditionRewards[expeditionStage]
            expeditionUnlocked = max(expeditionUnlocked, expeditionStage + 1).coerceAtMost(5)
            prefs.edit().putInt("expedition_unlocked", expeditionUnlocked).apply()
            saveGardenProgress()
            playTone(ToneGenerator.TONE_PROP_ACK, 220)
            performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
        }
        gameOver = !showExpeditionComplete && !availablePiecesHaveMove()
    }

    private fun animateEffects() {
        effectProgress = 0f
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 620
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                effectProgress = it.animatedValue as Float
                comboAlpha = 1f - effectProgress
                if (effectProgress >= 1f) { sparks = emptyList(); blastCenters = emptyList() }
                postInvalidateOnAnimation()
            }
            start()
        }
        sparks = sparks.takeLast(90)
    }

    private fun resetGame() {
        for (r in 0..8) for (c in 0..8) {
            board[r][c] = false
            colors[r][c] = Color.TRANSPARENT
        }
        score = 0
        level = 1
        gameOver = false
        paused = false
        confirmRestart = false
        pieces = MutableList(3) { newPiece() }
        selected = 0
        sparks = emptyList()
        blastCenters = emptyList()
        comboAlpha = 0f
        lastComboLines = 0
        lastMoveTime = 0L
        gamesPlayed++
        prefs.edit().putInt("games_played", gamesPlayed).apply()
    }

    private fun choosePieceAt(x: Float) {
        val index = ((x / width) * 3f).toInt().coerceIn(0, 2)
        if (pieces[index] != null) selected = index
    }

    private fun updatePreview(x: Float, y: Float) {
        val piece = pieces.getOrNull(selected) ?: run {
            previewRow = -1
            previewCol = -1
            previewValid = false
            return
        }
        if (x in boardLeft..(boardLeft + boardSize) && y in boardTop..(boardTop + boardSize)) {
            val pieceWidth = (piece.cells.maxOf { it.first } + 1)
            val pieceHeight = (piece.cells.maxOf { it.second } + 1)
            previewCol = ((x - boardLeft) / cell - pieceWidth / 2f).toInt()
            previewRow = ((y - boardTop) / cell - pieceHeight / 2f).toInt()
            previewValid = fits(piece, previewRow, previewCol)
        } else {
            previewRow = -1
            previewCol = -1
            previewValid = false
        }
    }

    private fun finishTutorial() {
        showTutorial = false
        prefs.edit().putBoolean("tutorial_seen", true).apply()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (mainMenuOpen || showTutorial || paused || gameOver || confirmRestart || showGardens || showShop || showExpeditionComplete) return true
            if (activePower.isNotEmpty()) {
                dragging = false
                previewRow = -1
                previewCol = -1
                invalidate()
                return true
            }
            if (event.y < height * .22f && event.x > width * .82f) return true
            if (event.y > trayCenterY - height * .06f && event.y < trayCenterY + height * .06f) {
                choosePieceAt(event.x)
                if (pieces[selected] != null) {
                    dragging = true
                    updatePreview(event.x, event.y)
                }
                invalidate()
                return true
            }
            if (event.x in boardLeft..(boardLeft + boardSize) && event.y in boardTop..(boardTop + boardSize)) {
                dragging = true
                updatePreview(event.x, event.y)
            }
            invalidate()
            return true
        }
        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                if (activePower.isNotEmpty()) return true
                if (dragging) {
                    updatePreview(event.x, event.y)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (activePower.isNotEmpty()) {
                    dragging = false
                    previewRow = -1
                    previewCol = -1
                    usePowerAt(event.x, event.y)
                    invalidate()
                    return true
                }
                if (mainMenuOpen) {
                    if (showExpeditions) {
                        if (event.y > height*.80f) {
                            showExpeditions = false
                        } else {
                            val idx=((event.y/height-.205f)/.118f).toInt()
                            if (idx in 0..4 && idx == expeditionUnlocked) {
                                expeditionStage = idx
                                activeExpedition = true
                                showExpeditions = false
                                showAchievements = false
                                mainMenuOpen = false
                                resetGame()
                                if (showTutorial) finishTutorial()
                            }
                        }
                    } else if (showAchievements) {
                        if (event.y > height*.80f) showAchievements=false
                        else {
                            val idx=((event.y/height-.195f)/.102f).toInt()
                            if(idx in 0..5) {
                                val unlocked=when(idx){0->gamesPlayed>=1;1->totalLines>=10;2->best>=500;3->bestCombo>=3;4->totalLines>=50;else->gardenStages.sum()>=3}
                                val key=listOf("ach_first","ach_lines","ach_score","ach_combo","ach_master_lines","ach_world")[idx]
                                val reward=intArrayOf(50,75,100,100,150,200)[idx]
                                if(unlocked&&!prefs.getBoolean(key,false)){coins+=reward;prefs.edit().putBoolean(key,true).apply();saveGardenProgress();playTone(ToneGenerator.TONE_PROP_ACK,160)}
                            }
                        }
                    } else {
                        when {
                            event.y in height*.64f..height*.745f -> {mainMenuOpen=false;showExpeditions=false;activeExpedition=false;if(showTutorial)finishTutorial()}
                            event.y in height*.75f..height*.85f && event.x<width*.36f -> {mainMenuOpen=false;showGardens=true}
                            event.y in height*.75f..height*.85f && event.x in width*.36f..width*.64f -> showExpeditions=true
                            event.y in height*.75f..height*.85f && event.x>width*.64f -> showAchievements=true
                        }
                    }
                    invalidate(); return true
                }
                if (showExpeditionComplete) {
                    if (event.y > height*.59f) {
                        showExpeditionComplete = false
                        mainMenuOpen = true
                        showExpeditions = true
                    }
                    invalidate()
                    return true
                }
                if (showTutorial) {
                    finishTutorial()
                    return true
                }
                if (showShop) {
                    if (event.y > height*.81f) {
                        showShop=false
                    } else {
                        val idx=((event.y/height-.225f)/.185f).toInt()
                        if(idx in 0..2) activateShopItem(idx)
                    }
                    invalidate()
                    return true
                }
                if (showGardens) {
                    if (gardenDetailOpen) {
                        when {
                            event.y in height * .85f..height * .94f -> gardenDetailOpen = false
                            event.y in height * .74f..height * .85f -> {
                                val index = selectedGarden
                                if ((index == 0 || gardenStages[index - 1] >= 3) && gardenStages[index] < 3) restoreGarden(index)
                            }
                        }
                    } else {
                        when {
                            event.y in height * .89f..height * .945f -> showGardens = false
                            event.y in height * .785f..height * .835f && event.x > width * .68f && dailyLines >= 5 && !dailyRewardClaimed -> {
                                dailyRewardClaimed = true
                                coins += 100
                                saveGardenProgress()
                                playTone(ToneGenerator.TONE_PROP_ACK, 150)
                            }
                            event.y in height * .838f..height * .88f && event.x > width * .68f && weeklyLines >= 20 && !weeklyRewardClaimed -> {
                                weeklyRewardClaimed = true
                                coins += 250
                                saveGardenProgress()
                                playTone(ToneGenerator.TONE_PROP_ACK, 180)
                            }
                            event.y in height * .21f..height * .37f -> { selectedGarden = 0; gardenDetailOpen = true }
                            event.y in height * .405f..height * .575f -> { selectedGarden = 1; gardenDetailOpen = true }
                            event.y in height * .61f..height * .77f -> { selectedGarden = 2; gardenDetailOpen = true }
                        }
                    }
                    invalidate()
                    return true
                }
                if (confirmRestart) {
                    if (event.y in height * .50f..height * .57f) resetGame()
                    else if (event.y in height * .57f..height * .64f) confirmRestart = false
                    invalidate()
                    return true
                }
                if (paused) {
                    when {
                        event.y in height * .47f..height * .54f -> {
                            paused = false
                            playTone(ToneGenerator.TONE_PROP_ACK, 70)
                        }
                        event.y in height * .55f..height * .62f -> {
                            soundEnabled = !soundEnabled
                            prefs.edit().putBoolean("sound_enabled", soundEnabled).apply()
                        }
                        event.y in height * .61f..height * .68f -> confirmRestart = true
                        event.y in height * .69f..height * .76f -> { paused = false; mainMenuOpen = true }
                    }
                    invalidate()
                    return true
                }
                if (gameOver) {
                    if (event.y in height * .55f..height * .63f) resetGame()
                    else if (event.y in height * .65f..height * .73f) { gameOver = false; mainMenuOpen = true }
                    invalidate()
                    return true
                }
                if (dragging) {
                    updatePreview(event.x, event.y)
                    val piece = pieces.getOrNull(selected)
                    if (piece != null && previewValid && previewRow >= 0 && previewCol >= 0) {
                        placePiece(piece, previewRow, previewCol)
                    }
                    dragging = false
                    previewRow = -1
                    previewCol = -1
                    invalidate()
                    return true
                }
                if (event.y < height * .22f && event.x < width * .16f) {
                    showGardens = true
                    invalidate()
                    return true
                }
                if (event.y in height*.12f..height*.21f && event.x in width*.70f..width*.845f) {
                    showShop=true
                    invalidate()
                    return true
                }
                if (event.y < height * .22f && event.x > width * .82f) {
                    paused = true
                    playTone(ToneGenerator.TONE_PROP_BEEP, 70)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                previewRow = -1
                previewCol = -1
                invalidate()
                return true
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try { tone.release() } catch (_: Exception) { }
    }
}
