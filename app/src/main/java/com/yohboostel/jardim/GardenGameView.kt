package com.yohboostel.jardim

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
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
        Color.rgb(83, 190, 139), Color.rgb(93, 164, 226),
        Color.rgb(246, 183, 91), Color.rgb(204, 137, 211),
        Color.rgb(239, 119, 126), Color.rgb(103, 199, 198)
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
    private data class Spark(val x: Float, val y: Float, val color: Int, val size: Float)

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

        paint.shader = LinearGradient(0f, 0f, w, h,
            intArrayOf(Color.rgb(17, 55, 48), Color.rgb(29, 91, 67), Color.rgb(38, 105, 73)),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        drawGardenBackdrop(canvas, w, h)
        if (mainMenuOpen) {
            when {
                showAchievements -> drawAchievementsScreen(canvas, w, h)
                showExpeditions -> drawExpeditionsScreen(canvas, w, h)
                else -> drawMainMenu(canvas, w, h)
            }
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
        if (showGardens) {
            if (gardenDetailOpen) drawGardenDetailScreen(canvas, w, h) else drawGardensScreen(canvas, w, h)
        }
        if (showShop) drawShopScreen(canvas, w, h)
        if (showExpeditionComplete) drawExpeditionCompleteScreen(canvas, w, h)
        if (showTutorial) drawTutorial(canvas, w, h)
        else if (confirmRestart) drawRestartConfirmation(canvas, w, h)
        else if (paused) drawPauseMenu(canvas, w, h)
        else if (gameOver) drawGameOver(canvas, w, h)
    }

    private fun drawMainMenu(c: Canvas, w: Float, h: Float) {
        paint.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(Color.rgb(17,55,48), Color.rgb(31,105,72), Color.rgb(112,157,91)), null, Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,w,h,paint); paint.shader = null
        drawGardenBackdrop(c,w,h)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-rounded",Typeface.BOLD)
        paint.color = Color.WHITE; paint.textSize = w*.085f
        c.drawText("Jardim em",w/2f,h*.105f,paint); c.drawText("Blocos",w/2f,h*.19f,paint)
        paint.textSize=w*.029f; paint.color=Color.rgb(231,245,216); paint.typeface=Typeface.create("sans-serif",Typeface.BOLD)
        c.drawText("UM MUNDO PARA CULTIVAR",w/2f,h*.225f,paint)
        val hero=RectF(w*.12f,h*.275f,w*.88f,h*.565f); drawGardenMiniature(c,0,gardenStages[0],hero)
        paint.color=Color.argb(45,13,46,31); c.drawRoundRect(hero,22f,22f,paint)
        paint.textSize=w*.028f; paint.color=Color.WHITE
        c.drawText("Recupere jardins e descubra novos lugares",w/2f,h*.60f,paint)
        val play=RectF(w*.15f,h*.65f,w*.85f,h*.735f); rounded(c,play,22f,Color.rgb(255,205,91))
        paint.color=Color.rgb(57,82,48); paint.textSize=w*.043f; paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD)
        c.drawText("JOGAR",play.centerX(),play.centerY()+w*.015f,paint)
        val gardens=RectF(w*.07f,h*.755f,w*.35f,h*.835f)
        val expedition=RectF(w*.36f,h*.755f,w*.64f,h*.835f)
        val achievements=RectF(w*.65f,h*.755f,w*.93f,h*.835f)
        rounded(c,gardens,16f,Color.rgb(239,231,197))
        rounded(c,expedition,16f,Color.rgb(255,218,133))
        rounded(c,achievements,16f,Color.rgb(239,231,197))
        paint.color=Color.rgb(47,91,64); paint.textSize=w*.023f
        c.drawText("MEUS JARDINS",gardens.centerX(),gardens.centerY()+w*.01f,paint)
        c.drawText("EXPEDIÇÃO",expedition.centerX(),expedition.centerY()+w*.01f,paint)
        c.drawText("CONQUISTAS",achievements.centerX(),achievements.centerY()+w*.01f,paint)
        paint.color=Color.rgb(255,232,166); paint.textSize=w*.031f
        c.drawText("✦  $coins moedas",w/2f,h*.895f,paint)
        paint.color=Color.argb(210,255,255,255); paint.textSize=w*.023f; paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL)
        c.drawText("RECORDE  $best     •     NÍVEL " + (1+best/500),w/2f,h*.935f,paint)
    }

    private fun drawAchievementsScreen(c: Canvas,w: Float,h: Float){
        drawDim(c,w,h);rounded(c,RectF(w*.045f,h*.045f,w*.955f,h*.955f),28f,Color.rgb(239,231,197));paint.textAlign=Paint.Align.CENTER
        paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD);paint.color=Color.rgb(47,91,64);paint.textSize=w*.055f;c.drawText("Galeria de conquistas",w/2f,h*.115f,paint)
        paint.textSize=w*.025f;paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.color=Color.rgb(103,111,87);c.drawText("Desafios permanentes que rendem moedas",w/2f,h*.155f,paint)
        val titles=listOf("Primeira partida","Jardineiro dedicado","Pontuação de ouro","Combo espetacular","Mestre das linhas","Mundo florescido")
        val details=listOf("Jogue sua primeira partida","Limpe 10 linhas no total","Alcance 500 pontos","Limpe 3 linhas de uma vez","Limpe 50 linhas no total","Restaure 3 etapas de jardins")
        val unlocked=listOf(gamesPlayed>=1,totalLines>=10,best>=500,bestCombo>=3,totalLines>=50,gardenStages.sum()>=3)
        val keys=listOf("ach_first","ach_lines","ach_score","ach_combo","ach_master_lines","ach_world");val rewards=intArrayOf(50,75,100,100,150,200)
        for(i in 0..5){val top=h*(.195f+i*.102f);rounded(c,RectF(w*.085f,top,w*.915f,top+h*.087f),14f,if(unlocked[i])Color.rgb(255,250,229)else Color.rgb(218,216,196))
            paint.color=if(unlocked[i])Color.rgb(225,170,67)else Color.rgb(153,157,139);c.drawCircle(w*.16f,top+h*.043f,w*.034f,paint);paint.color=Color.WHITE;paint.textSize=w*.035f;c.drawText(if(unlocked[i])"★"else"·",w*.16f,top+h*.055f,paint)
            paint.textAlign=Paint.Align.LEFT;paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD);paint.color=Color.rgb(47,91,64);paint.textSize=w*.027f;c.drawText(titles[i],w*.23f,top+h*.036f,paint)
            paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textSize=w*.020f;paint.color=Color.rgb(103,111,87);c.drawText(details[i],w*.23f,top+h*.065f,paint)
            val claimed=prefs.getBoolean(keys[i],false);val rr=RectF(w*.70f,top+h*.018f,w*.88f,top+h*.071f);rounded(c,rr,9f,if(unlocked[i]&&!claimed)Color.rgb(83,151,91)else Color.rgb(188,199,172))
            paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD);paint.color=if(unlocked[i]&&!claimed)Color.WHITE else Color.rgb(76,91,66);paint.textSize=w*.019f;c.drawText(if(claimed)"RESGATADA"else if(unlocked[i])"+"+rewards[i]else"BLOQUEADA",rr.centerX(),rr.centerY()+w*.006f,paint)
        }
        val back=RectF(w*.22f,h*.84f,w*.78f,h*.91f);rounded(c,back,16f,Color.rgb(71,145,91));paint.color=Color.WHITE;paint.textSize=w*.028f;c.drawText("VOLTAR AO MENU",back.centerX(),back.centerY()+w*.01f,paint)
    }
    private fun drawExpeditionsScreen(c: Canvas, w: Float, h: Float) {
        drawDim(c, w, h)
        rounded(c, RectF(w*.045f,h*.055f,w*.955f,h*.945f), 28f, Color.rgb(239,231,197))
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.color = Color.rgb(47,91,64)
        paint.textSize = w*.058f
        c.drawText("Expedição dos Jardins", w/2f, h*.125f, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = w*.025f
        paint.color = Color.rgb(103,111,87)
        c.drawText("Supere metas de pontuação para avançar", w/2f, h*.165f, paint)
        for (i in 0..4) {
            val top = h*(.205f+i*.118f)
            val unlocked = i <= expeditionUnlocked
            val completed = i < expeditionUnlocked
            rounded(c, RectF(w*.085f,top,w*.915f,top+h*.102f), 15f,
                if (completed) Color.rgb(220,239,207) else if (unlocked) Color.rgb(255,250,229) else Color.rgb(215,215,195))
            paint.textAlign = Paint.Align.LEFT
            paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
            paint.color = if (unlocked) Color.rgb(47,91,64) else Color.rgb(130,133,116)
            paint.textSize = w*.027f
            c.drawText("ETAPA "+(i+1)+"  •  "+expeditionNames[i], w*.13f, top+h*.039f, paint)
            paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            paint.textSize = w*.021f
            paint.color = Color.rgb(103,111,87)
            c.drawText("Alcance "+expeditionGoals[i]+" pontos em uma partida", w*.13f, top+h*.073f, paint)
            val reward = if (completed) "CONCLUÍDA" else if (unlocked) "+"+expeditionRewards[i]+" ✦" else "BLOQUEADA"
            paint.textAlign = Paint.Align.RIGHT
            paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
            paint.textSize = w*.021f
            paint.color = if (completed) Color.rgb(65,135,79) else if (unlocked) Color.rgb(174,116,43) else Color.rgb(130,133,116)
            c.drawText(reward, w*.88f, top+h*.055f, paint)
        }
        rounded(c, RectF(w*.22f,h*.825f,w*.78f,h*.90f), 16f, Color.rgb(71,145,91))
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE
        paint.textSize = w*.029f
        c.drawText("VOLTAR AO MENU", w*.5f, h*.87f, paint)
    }

    private fun drawExpeditionCompleteScreen(c: Canvas, w: Float, h: Float) {
        drawDim(c,w,h)
        drawPanel(c,w,h*.30f,h*.72f)
        paint.textAlign=Paint.Align.CENTER
        paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD)
        paint.color=Color.rgb(47,91,64)
        paint.textSize=w*.060f
        c.drawText("ETAPA CONCLUÍDA!",w/2f,h*.405f,paint)
        paint.textSize=w*.034f
        paint.color=Color.rgb(174,116,43)
        c.drawText(expeditionNames[expeditionStage],w/2f,h*.465f,paint)
        paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL)
        paint.textSize=w*.030f
        paint.color=Color.rgb(103,111,87)
        c.drawText("Você recebeu "+expeditionRewards[expeditionStage]+" moedas",w/2f,h*.515f,paint)
        c.drawText(if(expeditionUnlocked>=5) "Todas as etapas foram concluídas!" else "Próxima etapa: "+(expeditionUnlocked+1)+" de 5",w/2f,h*.56f,paint)
        drawButton(c,w,h*.645f,"CONTINUAR",true)
    }

    private fun drawGardenBackdrop(c: Canvas, w: Float, h: Float) {
        paint.color = Color.argb(24, 255, 255, 255)
        for (i in 0..6) c.drawCircle(w * (i / 6f), h * .19f, w * .11f, paint)
        paint.color = Color.rgb(249, 218, 139)
        c.drawCircle(w * .86f, h * .115f, w * .043f, paint)
        paint.color = Color.argb(45, 255, 255, 255)
        c.drawOval(w * .06f, h * .12f, w * .28f, h * .17f, paint)
        c.drawOval(w * .20f, h * .105f, w * .42f, h * .16f, paint)
    }

    private fun drawHeader(c: Canvas, w: Float, h: Float) {
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.color = Color.WHITE
        paint.textSize = w * .068f
        c.drawText("Jardim em Blocos", w / 2f, h * .071f, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.color = Color.rgb(203, 235, 211)
        paint.textSize = w * .025f
        c.drawText("CULTIVE  •  COMBINE  •  FLORESÇA", w / 2f, h * .105f, paint)

        drawGardenShortcut(c, w, h)
        drawStat(c, w * .25f, h * .165f, "PONTOS", score.toString())
        drawStat(c, w * .69f, h * .165f, "RECORDE", max(best, score).toString())
        paint.color=Color.rgb(255,226,160);paint.textSize=w*.025f;c.drawText("NÍVEL $level  •  ✦ $coins",w*.50f,h*.216f,paint)
        if (activeExpedition) {
            paint.color = Color.rgb(255,235,177)
            paint.textSize = w*.021f
            c.drawText("EXPEDIÇÃO "+(expeditionStage+1)+"  •  META "+expeditionGoals[expeditionStage],w*.5f,h*.242f,paint)
        }
        val lp=(score%500)/500f;val lb=RectF(w*.25f,h*.222f,w*.75f,h*.229f);rounded(c,lb,5f,Color.argb(85,235,247,232));if(lp>0f)rounded(c,RectF(lb.left,lb.top,lb.left+lb.width()*lp,lb.bottom),5f,Color.rgb(255,213,112))
        val shopButton = RectF(w * .715f, h * .125f, w * .835f, h * .205f)
        rounded(c, shopButton, 18f, Color.argb(175, 5, 35, 29))
        paint.color = Color.rgb(255, 226, 160)
        paint.textSize = w * .034f
        c.drawText("LOJA", shopButton.centerX(), shopButton.centerY() + w * .012f, paint)
        val button = RectF(w * .855f, h * .125f, w * .965f, h * .205f)
        rounded(c, button, 18f, Color.argb(150, 5, 35, 29))
        paint.color = Color.WHITE
        paint.textSize = w * .043f
        c.drawText("Ⅱ", button.centerX(), button.centerY() + w * .014f, paint)
    }

    private fun drawGardenShortcut(c: Canvas, w: Float, h: Float) {
        val button = RectF(w * .035f, h * .125f, w * .145f, h * .205f)
        rounded(c, button, 18f, Color.argb(165, 5, 35, 29))
        paint.color = Color.rgb(255, 232, 166)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.textSize = w * .047f
        c.drawText("✿", button.centerX(), button.centerY() + w * .016f, paint)
    }

    private fun drawGardenMiniature(c: Canvas,index:Int,stage:Int,rect:RectF){
        val sky=when(index){0->Color.rgb(174,218,239);1->Color.rgb(161,205,180);else->Color.rgb(168,218,222)};val grass=when(index){0->Color.rgb(123,166,91);1->Color.rgb(85,139,91);else->Color.rgb(115,167,145)}
        rounded(c,rect,16f,sky);paint.color=Color.argb(95,255,255,255);c.drawCircle(rect.left+rect.width()*.18f,rect.top+rect.height()*.20f,rect.width()*.09f,paint);c.drawCircle(rect.left+rect.width()*.25f,rect.top+rect.height()*.17f,rect.width()*.075f,paint);c.drawCircle(rect.left+rect.width()*.32f,rect.top+rect.height()*.21f,rect.width()*.08f,paint)
        paint.color=Color.rgb(103,150,113);c.drawOval(rect.left-rect.width()*.08f,rect.top+rect.height()*.47f,rect.left+rect.width()*.53f,rect.bottom+rect.height()*.10f,paint);paint.color=grass;c.drawOval(rect.left+rect.width()*.30f,rect.top+rect.height()*.53f,rect.right+rect.width()*.12f,rect.bottom+rect.height()*.16f,paint)
        if(index==2){paint.color=Color.rgb(68,151,184);c.drawOval(rect.left+rect.width()*.12f,rect.top+rect.height()*.57f,rect.right-rect.width()*.08f,rect.bottom+rect.height()*.08f,paint);paint.color=Color.argb(115,255,255,255);c.drawOval(rect.left+rect.width()*.30f,rect.top+rect.height()*.66f,rect.left+rect.width()*.62f,rect.top+rect.height()*.73f,paint)}
        if(index==1){paint.color=Color.rgb(91,129,73);c.drawCircle(rect.left+rect.width()*.22f,rect.top+rect.height()*.43f,rect.width()*.15f,paint);c.drawCircle(rect.left+rect.width()*.78f,rect.top+rect.height()*.40f,rect.width()*.18f,paint)}
        if(stage>=1){val x=rect.left+rect.width()*.50f;val y=rect.top+rect.height()*.79f;paint.color=Color.rgb(75,119,60);paint.strokeWidth=max(2f,rect.width()*.018f);c.drawLine(x,y,x,rect.top+rect.height()*.39f,paint);c.drawOval(x-rect.width()*.17f,rect.top+rect.height()*.48f,x,rect.top+rect.height()*.61f,paint);c.drawOval(x,rect.top+rect.height()*.43f,x+rect.width()*.17f,rect.top+rect.height()*.56f,paint)}
        if(stage>=2){val tx=if(index==0)rect.left+rect.width()*.77f else rect.left+rect.width()*.25f;val ty=rect.top+rect.height()*.53f;paint.color=Color.rgb(112,78,48);c.drawRoundRect(tx-rect.width()*.025f,ty,tx+rect.width()*.025f,rect.bottom-rect.height()*.10f,4f,4f,paint);paint.color=if(index==1)Color.rgb(43,111,67)else Color.rgb(62,139,77);c.drawCircle(tx,ty-rect.height()*.10f,rect.width()*.16f,paint);c.drawCircle(tx-rect.width()*.09f,ty,rect.width()*.12f,paint);c.drawCircle(tx+rect.width()*.09f,ty,rect.width()*.12f,paint)}
        if(stage>=3){val fc=intArrayOf(Color.rgb(245,118,142),Color.rgb(255,215,94),Color.WHITE,Color.rgb(185,145,220));for(i in 0..3){val fx=rect.left+rect.width()*(.16f+i*.20f);val fy=rect.top+rect.height()*(.78f-(i%2)*.08f);paint.color=fc[i];c.drawCircle(fx,fy,rect.width()*.045f,paint);paint.color=Color.rgb(255,224,123);c.drawCircle(fx,fy,rect.width()*.018f,paint)}}
        paint.strokeWidth=1f
    }
    private fun drawGardenDetailScreen(c: Canvas, w: Float, h: Float) {
        drawDim(c, w, h)
        rounded(c, RectF(w * .045f, h * .055f, w * .955f, h * .945f), 28f, Color.rgb(239, 231, 197))
        val index = selectedGarden.coerceIn(0, 2)
        val stage = gardenStages[index]
        val unlocked = index == 0 || gardenStages[index - 1] >= 3
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.color = Color.rgb(47, 91, 64)
        paint.textSize = w * .052f
        c.drawText(gardenNames[index], w / 2f, h * .125f, paint)
        paint.textSize = w * .027f
        paint.color = Color.rgb(174, 116, 43)
        c.drawText("MOEDAS " + coins, w / 2f, h * .17f, paint)
        val scene = RectF(w * .10f, h * .205f, w * .90f, h * .57f)
        drawGardenMiniature(c, index, stage, scene)
        paint.textSize = w * .034f
        paint.color = Color.rgb(47, 91, 64)
        c.drawText("ETAPA " + stage + " DE 3", w / 2f, h * .625f, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = w * .027f
        paint.color = Color.rgb(103, 111, 87)
        c.drawText(gardenDescriptions[index], w / 2f, h * .67f, paint)
        val progress = RectF(w * .18f, h * .705f, w * .82f, h * .725f)
        rounded(c, progress, 10f, Color.rgb(218, 220, 195))
        if (stage > 0) rounded(c, RectF(progress.left, progress.top, progress.left + progress.width() * stage / 3f, progress.bottom), 10f, Color.rgb(91, 161, 99))
        val action = RectF(w * .18f, h * .755f, w * .82f, h * .835f)
        val canRestore = unlocked && stage < 3 && coins >= gardenCosts[index][stage]
        val label = when { !unlocked -> "JARDIM BLOQUEADO"; stage >= 3 -> "JARDIM RESTAURADO!"; else -> "RESTAURAR  •  " + gardenCosts[index][stage] + " MOEDAS" }
        rounded(c, action, 18f, if (canRestore) Color.rgb(71, 145, 91) else Color.rgb(178, 190, 165))
        paint.color = if (canRestore) Color.WHITE else Color.rgb(76, 91, 66)
        paint.textSize = w * .028f
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        c.drawText(label, action.centerX(), action.centerY() + w * .01f, paint)
        val back = RectF(w * .25f, h * .865f, w * .75f, h * .92f)
        rounded(c, back, 15f, Color.rgb(71, 145, 91))
        paint.color = Color.WHITE
        paint.textSize = w * .029f
        c.drawText("VOLTAR AOS JARDINS", back.centerX(), back.centerY() + w * .01f, paint)
    }
    private fun drawGardensScreen(c: Canvas, w: Float, h: Float) {
        // Mapa-múndi ilustrado: terreno contínuo, rio, trilha e regiões exploráveis.
        paint.shader = LinearGradient(0f, 0f, w, h,
            intArrayOf(Color.rgb(157, 202, 157), Color.rgb(190, 211, 151), Color.rgb(225, 211, 153)),
            null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // Colinas e clareiras ao fundo.
        paint.color = Color.rgb(119, 174, 119)
        c.drawOval(-w*.28f, h*.12f, w*.68f, h*.53f, paint)
        c.drawOval(w*.42f, h*.30f, w*1.22f, h*.72f, paint)
        paint.color = Color.rgb(139, 190, 126)
        c.drawOval(-w*.20f, h*.48f, w*.72f, h*.88f, paint)
        c.drawOval(w*.38f, h*.62f, w*1.18f, h*.96f, paint)

        // Rio sinuoso atravessando o mundo.
        val river = android.graphics.Path().apply {
            moveTo(w*1.08f, h*.18f)
            cubicTo(w*.72f, h*.29f, w*.92f, h*.39f, w*.55f, h*.49f)
            cubicTo(w*.18f, h*.59f, w*.48f, h*.69f, w*.02f, h*.79f)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = w*.105f
        paint.color = Color.rgb(104, 177, 190)
        c.drawPath(river, paint)
        paint.strokeWidth = w*.012f
        paint.color = Color.argb(115, 224, 250, 235)
        c.drawPath(river, paint)

        // Trilha de exploração com borda escura e centro de areia.
        val trail = android.graphics.Path().apply {
            moveTo(w*.28f, h*.285f)
            cubicTo(w*.78f, h*.34f, w*.18f, h*.43f, w*.70f, h*.49f)
            cubicTo(w*.91f, h*.56f, w*.17f, h*.62f, w*.32f, h*.705f)
        }
        paint.strokeWidth = w*.052f
        paint.color = Color.rgb(119, 104, 68)
        c.drawPath(trail, paint)
        paint.strokeWidth = w*.034f
        paint.color = Color.rgb(239, 211, 145)
        c.drawPath(trail, paint)
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT

        // Pequenas árvores e pedras decorativas do mundo.
        for (i in 0..9) {
            val x = w * (0.08f + (i % 5) * .205f)
            val y = h * (0.235f + (i / 5) * .255f)
            paint.color = Color.rgb(104, 143, 78)
            c.drawCircle(x, y, w*.026f, paint)
            paint.color = Color.rgb(65, 119, 69)
            c.drawCircle(x-w*.008f, y-w*.009f, w*.020f, paint)
            paint.color = Color.rgb(116, 83, 52)
            c.drawRect(x-w*.004f, y+w*.012f, x+w*.004f, y+w*.035f, paint)
        }

        // Cabeçalho flutuante.
        rounded(c, RectF(w*.055f,h*.035f,w*.945f,h*.155f), 25f, Color.rgb(248,239,204))
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.color = Color.rgb(47,91,64)
        paint.textSize = w*.060f
        c.drawText("MUNDO JARDIM",w*.5f,h*.092f,paint)
        paint.textSize = w*.027f
        paint.color = Color.rgb(116,105,68)
        c.drawText("Explore, restaure e desbloqueie novas terras",w*.5f,h*.132f,paint)
        rounded(c,RectF(w*.66f,h*.165f,w*.94f,h*.211f),14f,Color.rgb(255,245,210))
        paint.color = Color.rgb(174,116,43)
        paint.textSize = w*.026f
        c.drawText("✦  "+coins+" moedas",w*.80f,h*.196f,paint)

        val centersX = floatArrayOf(.28f,.70f,.32f)
        val centersY = floatArrayOf(.295f,.49f,.695f)
        val regionColors = intArrayOf(Color.rgb(244,190,112),Color.rgb(131,177,119),Color.rgb(116,190,205))
        for (i in 0..2) {
            val cx = w*centersX[i]
            val cy = h*centersY[i]
            val unlocked = i == 0 || gardenStages[i-1] >= 3
            val stage = gardenStages[i]
            val island = RectF(cx-w*.205f,cy-h*.078f,cx+w*.205f,cy+h*.078f)

            // Ilha com sombra, borda de terra e interior verde.
            paint.color = Color.argb(65,35,65,38)
            c.drawOval(island.left+w*.012f,island.top+h*.014f,island.right+w*.012f,island.bottom+h*.014f,paint)
            paint.color = Color.rgb(118,151,83)
            c.drawOval(island.left,island.top,island.right,island.bottom,paint)
            paint.color = Color.rgb(205,190,126)
            c.drawOval(island.left+w*.012f,island.top+h*.008f,island.right-w*.012f,island.bottom-h*.008f,paint)
            paint.color = regionColors[i]
            c.drawOval(island.left+w*.021f,island.top+h*.014f,island.right-w*.021f,island.bottom-h*.014f,paint)

            val scene = RectF(cx-w*.155f,cy-h*.061f,cx+w*.155f,cy+h*.040f)
            drawGardenMiniature(c,i,stage,scene)

            // Faixa com nome e estado da região.
            val namePlate = RectF(cx-w*.17f,cy+h*.040f,cx+w*.17f,cy+h*.073f)
            rounded(c,namePlate,10f,Color.rgb(250,244,218))
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create("sans-serif-rounded",Typeface.BOLD)
            paint.textSize = w*.025f
            paint.color = Color.rgb(47,91,64)
            c.drawText(gardenNames[i],cx,cy+h*.062f,paint)

            // Medalhão de progresso.
            paint.color = if (!unlocked) Color.rgb(104,108,93) else if (stage>=3) Color.rgb(230,174,59) else Color.rgb(68,132,78)
            c.drawCircle(cx+w*.157f,cy-h*.050f,w*.036f,paint)
            paint.color = Color.WHITE
            paint.textSize = w*.034f
            c.drawText(if(!unlocked)"×" else if(stage>=3)"★" else (stage+1).toString(),cx+w*.157f,cy-h*.038f,paint)

            if (!unlocked) {
                paint.color = Color.argb(135,35,47,38)
                c.drawOval(island.left,island.top,island.right,island.bottom,paint)
                paint.color = Color.WHITE
                paint.textSize = w*.025f
                c.drawText("CONCLUA A REGIÃO ANTERIOR",cx,cy+h*.012f,paint)
            }
        }

        // Painel compacto de missões e recompensas.
        rounded(c,RectF(w*.055f,h*.785f,w*.945f,h*.885f),20f,Color.rgb(248,239,204))
        paint.textAlign=Paint.Align.LEFT
        paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD)
        paint.color=Color.rgb(47,91,64)
        paint.textSize=w*.025f
        c.drawText("MISSÃO DIÁRIA  •  "+dailyLines+"/5 linhas",w*.09f,h*.818f,paint)
        c.drawText("MISSÃO SEMANAL  •  "+weeklyLines+"/20 linhas",w*.09f,h*.858f,paint)
        val dr=dailyLines>=5&&!dailyRewardClaimed
        val wr=weeklyLines>=20&&!weeklyRewardClaimed
        rounded(c,RectF(w*.70f,h*.795f,w*.90f,h*.835f),10f,if(dr)Color.rgb(83,151,91)else Color.rgb(188,199,172))
        rounded(c,RectF(w*.70f,h*.838f,w*.90f,h*.878f),10f,if(wr)Color.rgb(83,151,91)else Color.rgb(188,199,172))
        paint.textAlign=Paint.Align.CENTER
        paint.textSize=w*.019f
        paint.typeface=Typeface.create("sans-serif",Typeface.BOLD)
        paint.color=if(dr)Color.WHITE else Color.rgb(76,91,66)
        c.drawText(if(dailyRewardClaimed)"RESGATADA"else"+100 MOEDAS",w*.80f,h*.821f,paint)
        paint.color=if(wr)Color.WHITE else Color.rgb(76,91,66)
        c.drawText(if(weeklyRewardClaimed)"RESGATADA"else"+250 MOEDAS",w*.80f,h*.864f,paint)
        rounded(c,RectF(w*.27f,h*.90f,w*.73f,h*.955f),16f,Color.rgb(57,119,70))
        paint.color=Color.WHITE
        paint.textSize=w*.028f
        c.drawText("VOLTAR AO JOGO",w*.5f,h*.936f,paint)
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
        val box = RectF(x - width * .17f, y - height * .026f, x + width * .17f, y + height * .047f)
        rounded(c, box, height * .018f, Color.argb(105, 5, 35, 29))
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.color = Color.rgb(193, 224, 200)
        paint.textSize = width * .023f
        c.drawText(label, x, y, paint)
        paint.color = Color.WHITE
        paint.textSize = width * .040f
        c.drawText(value, x, y + height * .039f, paint)
    }

    private fun drawBoard(c: Canvas) {
        val outer = RectF(boardLeft - 8f, boardTop - 8f, boardLeft + boardSize + 8f, boardTop + boardSize + 8f)
        rounded(c, outer, 24f, Color.argb(225, 8, 39, 33))
        for (r in 0..8) for (col in 0..8) {
            val left = boardLeft + col * cell
            val top = boardTop + r * cell
            val rect = RectF(left + 2f, top + 2f, left + cell - 2f, top + cell - 2f)
            rounded(c, rect, cell * .17f, if (board[r][col]) colors[r][col] else Color.argb(76, 207, 239, 215))
            if (board[r][col]) {
                paint.color = Color.argb(65, 255, 255, 255)
                c.drawCircle(left + cell * .28f, top + cell * .24f, cell * .085f, paint)
                paint.color = Color.argb(35, 0, 0, 0)
                c.drawRoundRect(rect.left + cell * .08f, rect.bottom - cell * .13f,
                    rect.right - cell * .08f, rect.bottom - cell * .04f, cell * .05f, cell * .05f, paint)
            }
        }
        if (dragging && previewRow >= 0 && previewCol >= 0) {
            val p = pieces.getOrNull(selected)
            if (p != null) p.cells.forEach { (dx, dy) ->
                val row = previewRow + dy
                val col = previewCol + dx
                if (row in 0..8 && col in 0..8) {
                    val rect = RectF(boardLeft + col * cell + 2f, boardTop + row * cell + 2f,
                        boardLeft + (col + 1) * cell - 2f, boardTop + (row + 1) * cell - 2f)
                    rounded(c, rect, cell * .17f,
                        if (previewValid) withAlpha(p.color, 155) else Color.argb(145, 235, 75, 75))
                }
            }
        }
    }

    private fun drawPieceTray(c: Canvas, w: Float, h: Float) {
        paint.color = Color.rgb(231, 246, 230)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textSize = w * .030f
        c.drawText("ESCOLHA UMA PEÇA", w / 2f, slotsTop - h * .020f, paint)
        val slotWidth = w * .275f
        for (i in 0..2) {
            val centerX = w * (.18f + i * .32f)
            val active = i == selected && pieces[i] != null
            val rect = RectF(centerX - slotWidth / 2f, trayCenterY - h * .047f,
                centerX + slotWidth / 2f, trayCenterY + h * .047f)
            rounded(c, rect, 19f, when {
                pieces[i] == null -> Color.argb(45, 7, 38, 32)
                active -> Color.rgb(247, 231, 184)
                else -> Color.argb(105, 7, 38, 32)
            })
            if (active) {
                paint.color = Color.rgb(128, 94, 49)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f
                c.drawRoundRect(rect, 19f, 19f, paint)
                paint.style = Paint.Style.FILL
            }
            val piece = pieces[i]
            if (piece != null && !(dragging && i == selected)) {
                drawPiece(c, piece, centerX, trayCenterY - h * .004f, min(cell * .37f, h * .025f))
            } else if (piece == null) {
                paint.color = Color.argb(115, 235, 247, 232)
                paint.textSize = w * .045f
                c.drawText("✓", centerX, trayCenterY + h * .012f, paint)
            }
        }
        if (comboAlpha > 0f) {
            paint.color = withAlpha(Color.rgb(255, 226, 139), (comboAlpha * 255).toInt())
            paint.textSize = w * .043f
            paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
            c.drawText(comboLabel, w / 2f, trayCenterY + h * .105f, paint)
        }
    }

    private fun drawFooter(c: Canvas, w: Float, h: Float) {
        paint.color = if (activePower.isNotEmpty()) Color.rgb(255, 226, 160) else Color.rgb(218, 241, 220)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        paint.textSize = w * .025f
        val hint = when (activePower) {
            "hammer" -> "MARTELO ATIVO • TOQUE EM UM BLOCO"
            "row" -> "BOMBA DE LINHA ATIVA • TOQUE NA LINHA"
            else -> "Arraste uma peça para o tabuleiro"
        }
        c.drawText(hint, w / 2f, h * .955f, paint)
    }

    private fun drawShopScreen(c: Canvas, w: Float, h: Float) {
        drawDim(c,w,h)
        rounded(c,RectF(w*.045f,h*.055f,w*.955f,h*.945f),28f,Color.rgb(239,231,197))
        paint.textAlign=Paint.Align.CENTER
        paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD)
        paint.color=Color.rgb(47,91,64)
        paint.textSize=w*.060f
        c.drawText("LOJA DE PODERES",w*.5f,h*.125f,paint)
        paint.textSize=w*.029f
        paint.color=Color.rgb(174,116,43)
        c.drawText("✦  "+coins+" moedas disponíveis",w*.5f,h*.17f,paint)
        val names=listOf("Martelo","Bomba de linha","Nova mão")
        val descriptions=listOf("Remove um bloco do tabuleiro","Limpa todos os blocos de uma linha","Troca as três peças por novas")
        val counts=intArrayOf(hammerCount,rowBombCount,rerollCount)
        val prices=intArrayOf(80,120,60)
        val icons=listOf("◆","✹","↻")
        for(i in 0..2){
            val top=h*(.225f+i*.185f)
            rounded(c,RectF(w*.09f,top,w*.91f,top+h*.155f),18f,Color.rgb(255,250,229))
            paint.color=intArrayOf(Color.rgb(220,143,79),Color.rgb(210,104,82),Color.rgb(99,155,190))[i]
            c.drawCircle(w*.19f,top+h*.075f,w*.052f,paint)
            paint.color=Color.WHITE
            paint.textSize=w*.052f
            c.drawText(icons[i],w*.19f,top+h*.093f,paint)
            paint.textAlign=Paint.Align.LEFT
            paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD)
            paint.color=Color.rgb(47,91,64)
            paint.textSize=w*.032f
            c.drawText(names[i],w*.29f,top+h*.055f,paint)
            paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL)
            paint.color=Color.rgb(103,111,87)
            paint.textSize=w*.022f
            c.drawText(descriptions[i],w*.29f,top+h*.090f,paint)
            paint.typeface=Typeface.create("sans-serif-rounded",Typeface.BOLD)
            paint.color=Color.rgb(174,116,43)
            paint.textSize=w*.024f
            c.drawText("Possui: "+counts[i],w*.29f,top+h*.128f,paint)
            val btn=RectF(w*.68f,top+h*.045f,w*.87f,top+h*.115f)
            rounded(c,btn,12f,if(counts[i]>0)Color.rgb(71,145,91)else Color.rgb(226,181,84))
            paint.textAlign=Paint.Align.CENTER
            paint.color=Color.WHITE
            paint.textSize=w*.022f
            c.drawText(if(counts[i]>0)"USAR" else prices[i].toString()+" ✦",btn.centerX(),btn.centerY()+w*.008f,paint)
        }
        rounded(c,RectF(w*.23f,h*.825f,w*.77f,h*.90f),16f,Color.rgb(71,145,91))
        paint.textAlign=Paint.Align.CENTER
        paint.color=Color.WHITE
        paint.textSize=w*.029f
        c.drawText("VOLTAR AO JOGO",w*.5f,h*.87f,paint)
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
                2->{rerollCount--;showShop=false;pieces=MutableList(3){newPiece()};selected=0;gameOver=false}
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
        if(activePower.isEmpty() || x<boardLeft || x>=boardLeft+boardSize || y<boardTop || y>=boardTop+boardSize)return
        val row=((y-boardTop)/cell).toInt().coerceIn(0,8)
        val col=((x-boardLeft)/cell).toInt().coerceIn(0,8)
        var removed=0
        if(activePower=="hammer" && board[row][col]){
            board[row][col]=false
            colors[row][col]=Color.TRANSPARENT
            removed=1
        } else if(activePower=="row"){
            for(c in 0..8) if(board[row][c]){
                board[row][c]=false
                colors[row][c]=Color.TRANSPARENT
                removed++
            }
        }
        if(removed>0){
            score+=removed*10
            if(score>best){best=score;prefs.edit().putInt("best_score",best).apply()}
            level=1+score/500
            if(activePower=="hammer")hammerCount-- else rowBombCount--
            activePower=""
            savePowerInventory()
            gameOver=!availablePiecesHaveMove()
            playTone(ToneGenerator.TONE_PROP_ACK,160)
        } else {
            playTone(ToneGenerator.TONE_PROP_BEEP,70)
        }
        invalidate()
    }

    private fun drawPiece(c: Canvas, piece: Piece, cx: Float, cy: Float, size: Float) {
        val maxX = piece.cells.maxOf { it.first } + 1
        val maxY = piece.cells.maxOf { it.second } + 1
        val unit = min(size * 2.35f / maxX, size * 2.35f / maxY)
        piece.cells.forEach { (x, y) ->
            val left = cx - maxX * unit / 2f + x * unit
            val top = cy - maxY * unit / 2f + y * unit
            val rect = RectF(left, top, left + unit - 3f, top + unit - 3f)
            rounded(c, rect, 7f, piece.color)
            paint.color = Color.argb(65, 255, 255, 255)
            c.drawCircle(rect.left + rect.width() * .28f, rect.top + rect.height() * .24f, unit * .09f, paint)
        }
    }

    private fun drawSparks(c: Canvas) {
        sparks.forEach {
            paint.color = withAlpha(it.color, (255 * (1f - effectProgress)).toInt().coerceIn(0, 255))
            c.drawCircle(it.x, it.y - effectProgress * height * .07f,
                it.size * (1f - effectProgress * .35f), paint)
        }
    }

    private fun drawDim(c: Canvas, w: Float, h: Float) {
        paint.color = Color.argb(205, 4, 23, 19)
        c.drawRect(0f, 0f, w, h, paint)
    }

    private fun drawPanel(c: Canvas, w: Float, top: Float, bottom: Float) {
        rounded(c, RectF(w * .075f, top, w * .925f, bottom), 28f, Color.rgb(239, 231, 197))
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
        val rect = RectF(w * .20f, centerY - height * .027f, w * .80f, centerY + height * .027f)
        rounded(c, rect, 18f, if (primary) Color.rgb(71, 145, 91) else Color.rgb(194, 211, 177))
        paint.color = if (primary) Color.WHITE else Color.rgb(47, 91, 64)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textSize = w * .031f
        c.drawText(label, rect.centerX(), rect.centerY() + w * .011f, paint)
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
                sparks = sparks + Spark(boardLeft + (c + .5f) * cell,
                    boardTop + (r + .5f) * cell, colors[r][c], cell * .17f)
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
            playTone(if (lines >= 3) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP, 180)
            performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
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
            duration = 480
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                effectProgress = it.animatedValue as Float
                comboAlpha = 1f - effectProgress
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
            if (mainMenuOpen || showTutorial || paused || gameOver || confirmRestart || showGardens || showShop) return true
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
                if (activePower.isNotEmpty()) {
                    dragging=false
                    previewRow=-1
                    previewCol=-1
                    usePowerAt(event.x,event.y)
                    return true
                }
                if (dragging) {
                    updatePreview(event.x, event.y)
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
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
