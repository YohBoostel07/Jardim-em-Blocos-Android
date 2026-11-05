package com.yohboostel.jardim

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renderizador de cenário 3D real: terreno, ilhas, árvores, copas, flores,
 * pedras e lago são malhas tridimensionais iluminadas por OpenGL ES 2.0.
 */
class Garden3DView(context: Context) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        setRenderer(Garden3DRenderer())
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
    }
}

private class Garden3DRenderer : GLSurfaceView.Renderer {
    private data class Mesh(val vertices: FloatBuffer, val normals: FloatBuffer, val indices: ShortBuffer, val count: Int)
    private var program = 0
    private lateinit var sphere: Mesh
    private lateinit var cylinder: Mesh
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private var start = System.nanoTime()

    private val vertexShader = """
        uniform mat4 uMvp;
        uniform mat4 uModel;
        attribute vec3 aPosition;
        attribute vec3 aNormal;
        varying float vLight;
        void main() {
            vec3 n = normalize(mat3(uModel) * aNormal);
            vec3 lightDir = normalize(vec3(-0.45, 0.85, 0.55));
            vLight = 0.48 + 0.62 * max(dot(n, lightDir), 0.0);
            gl_Position = uMvp * vec4(aPosition, 1.0);
        }
    """.trimIndent()

    private val fragmentShader = """
        precision mediump float;
        uniform vec4 uColor;
        varying float vLight;
        void main() {
            gl_FragColor = vec4(uColor.rgb * vLight, uColor.a);
        }
    """.trimIndent()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.48f, 0.76f, 0.88f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        program = linkProgram(vertexShader, fragmentShader)
        sphere = makeSphere(22, 30)
        cylinder = makeCylinder(24)
        start = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.perspectiveM(projection, 0, 43f, width.toFloat() / height.coerceAtLeast(1), 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val t = (System.nanoTime() - start) / 1_000_000_000f
        val cameraX = sin(t * 0.12f) * 0.75f
        Matrix.setLookAtM(view, 0, cameraX, 6.1f, 14.5f, 0f, 0.0f, -1.5f, 0f, 1f, 0f)

        // Plataforma flutuante com laterais de terra e topo gramado.
        sphere(0f, -0.62f, -2.2f, 6.4f, 0.72f, 5.15f, 0.43f, 0.25f, 0.13f)
        sphere(0f, -0.27f, -2.2f, 6.16f, 0.48f, 4.95f, 0.22f, 0.55f, 0.25f)
        sphere(0f, -0.05f, -2.2f, 5.88f, 0.23f, 4.72f, 0.30f, 0.68f, 0.29f)

        // Colinas ao fundo e pequenas elevações no terreno.
        sphere(-4.0f, 0.0f, -4.0f, 2.25f, 1.2f, 1.7f, 0.25f, 0.58f, 0.27f)
        sphere(3.7f, 0.0f, -4.4f, 2.35f, 1.25f, 1.8f, 0.27f, 0.61f, 0.29f)
        sphere(-0.5f, 0.04f, -5.1f, 2.8f, 1.0f, 1.5f, 0.31f, 0.65f, 0.31f)

        // Lago com margem de pedra e superfície azul.
        sphere(2.15f, 0.055f, -2.45f, 1.75f, 0.16f, 1.28f, 0.63f, 0.72f, 0.55f)
        sphere(2.15f, 0.14f, -2.45f, 1.48f, 0.055f, 1.05f, 0.12f, 0.62f, 0.82f)
        sphere(1.65f, 0.205f, -2.6f, 0.48f, 0.018f, 0.055f, 0.65f, 0.91f, 0.97f)
        sphere(2.55f, 0.205f, -2.15f, 0.36f, 0.018f, 0.045f, 0.75f, 0.95f, 1.0f)

        // Árvores de copas orgânicas em vários planos de profundidade.
        tree(-4.2f, 0.10f, -2.7f, 1.15f, 0.16f, 0.47f, 0.20f)
        tree(-2.55f, 0.10f, -4.25f, 0.88f, 0.13f, 0.43f, 0.18f)
        tree(-0.55f, 0.10f, -4.15f, 1.02f, 0.12f, 0.48f, 0.20f)
        tree(4.15f, 0.10f, -3.15f, 1.12f, 0.15f, 0.43f, 0.18f)
        tree(-4.45f, 0.08f, 0.15f, 0.92f, 0.19f, 0.50f, 0.22f)
        tree(-2.25f, 0.08f, 1.45f, 0.76f, 0.15f, 0.46f, 0.20f)
        tree(3.9f, 0.08f, 0.55f, 0.88f, 0.15f, 0.49f, 0.21f)

        // Arbustos e pedras modelados como volumes independentes.
        for (i in 0..13) {
            val x = -4.9f + (i % 7) * 1.55f
            val z = -0.8f + (i / 7) * 2.0f
            sphere(x, 0.13f, z, 0.48f, 0.31f, 0.42f, 0.17f, 0.48f, 0.20f)
            sphere(x + 0.22f, 0.18f, z - 0.12f, 0.31f, 0.26f, 0.30f, 0.23f, 0.59f, 0.25f)
        }
        rock(-3.1f, 0.12f, -1.4f, 0.42f)
        rock(0.7f, 0.12f, 1.1f, 0.32f)
        rock(4.55f, 0.12f, -0.2f, 0.36f)
        rock(-1.3f, 0.12f, -3.2f, 0.29f)

        // Canteiros floridos em primeiro plano: caule, folhas e pétalas 3D.
        val flowerColors = arrayOf(
            floatArrayOf(1.0f, 0.28f, 0.48f), floatArrayOf(1.0f, 0.72f, 0.16f),
            floatArrayOf(0.73f, 0.48f, 0.96f), floatArrayOf(0.96f, 0.91f, 0.80f)
        )
        for (i in 0..27) {
            val x = -4.85f + (i % 14) * 0.72f
            val z = if (i < 14) 2.75f else 0.25f
            flower(x, z, flowerColors[i % flowerColors.size], 0.48f + (i % 3) * 0.07f)
        }

        // Pequenas flores e pontos de luz no gramado.
        for (i in 0..16) {
            val x = -4.8f + (i * 1.31f) % 9.6f
            val z = -3.0f + (i % 5) * 0.85f
            sphere(x, 0.18f, z, 0.10f, 0.09f, 0.10f, 1.0f, 0.83f, 0.35f)
        }

        // Nuvens volumétricas e sol no horizonte.
        for (cloud in 0..3) {
            val cx = -4.8f + cloud * 3.2f
            val cy = 4.8f + (cloud % 2) * 0.35f
            sphere(cx, cy, -10.5f, 1.05f, 0.30f, 0.48f, 0.94f, 0.98f, 0.98f)
            sphere(cx - 0.55f, cy + 0.12f, -10.5f, 0.58f, 0.43f, 0.45f, 1.0f, 1.0f, 1.0f)
            sphere(cx + 0.42f, cy + 0.10f, -10.5f, 0.67f, 0.40f, 0.48f, 0.98f, 1.0f, 1.0f)
        }
        sphere(-4.6f, 5.2f, -9.5f, 0.72f, 0.72f, 0.72f, 1.0f, 0.86f, 0.48f)
    }

    private fun tree(x: Float, ground: Float, z: Float, size: Float, r: Float, g: Float, b: Float) {
        cylinder(x, ground + size * 0.52f, z, size * 0.16f, size * 1.08f, size * 0.16f, 0.39f, 0.22f, 0.12f)
        // Quatro volumes sobrepostos criam uma copa arredondada, com luz e sombra reais.
        sphere(x, ground + size * 1.28f, z, size * 0.62f, size * 0.59f, size * 0.57f, r, g, b)
        sphere(x - size * 0.40f, ground + size * 1.12f, z + size * 0.08f, size * 0.43f, size * 0.43f, size * 0.43f, r * 0.88f, g * 0.91f, b * 0.88f)
        sphere(x + size * 0.40f, ground + size * 1.13f, z + size * 0.04f, size * 0.44f, size * 0.44f, size * 0.43f, r * 1.04f, g * 1.04f, b * 1.02f)
        sphere(x - size * 0.26f, ground + size * 1.55f, z + size * 0.28f, size * 0.34f, size * 0.36f, size * 0.34f, r * 0.94f, g * 0.98f, b * 0.94f)
        sphere(x + size * 0.29f, ground + size * 1.55f, z - size * 0.24f, size * 0.36f, size * 0.37f, size * 0.34f, r * 1.08f, g * 1.04f, b * 1.02f)
        sphere(x, ground + size * 1.65f, z - size * 0.02f, size * 0.40f, size * 0.42f, size * 0.40f, r * 1.08f, g * 1.07f, b * 1.03f)
    }

    private fun flower(x: Float, z: Float, color: FloatArray, height: Float) {
        cylinder(x, height * 0.48f, z, 0.035f, height, 0.035f, 0.16f, 0.48f, 0.18f)
        sphere(x - 0.12f, height * 0.42f, z, 0.16f, 0.055f, 0.075f, 0.20f, 0.62f, 0.22f)
        sphere(x + 0.11f, height * 0.60f, z, 0.15f, 0.05f, 0.07f, 0.25f, 0.70f, 0.25f)
        val cy = height + 0.03f
        for (p in 0..5) {
            val a = (Math.PI * 2.0 * p / 6.0).toFloat()
            sphere(x + cos(a) * 0.17f, cy + sin(a) * 0.12f, z, 0.13f, 0.12f, 0.12f, color[0], color[1], color[2])
        }
        sphere(x, cy + 0.015f, z - 0.015f, 0.095f, 0.09f, 0.09f, 1.0f, 0.78f, 0.20f)
    }

    private fun rock(x: Float, y: Float, z: Float, size: Float) {
        sphere(x, y + size * 0.30f, z, size, size * 0.62f, size * 0.78f, 0.46f, 0.52f, 0.49f)
        sphere(x - size * 0.16f, y + size * 0.58f, z - size * 0.08f, size * 0.52f, size * 0.34f, size * 0.52f, 0.62f, 0.66f, 0.60f)
    }

    private fun sphere(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, r: Float, g: Float, b: Float) {
        draw(sphere, x, y, z, sx, sy, sz, r, g, b)
    }

    private fun cylinder(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, r: Float, g: Float, b: Float) {
        draw(cylinder, x, y, z, sx, sy, sz, r, g, b)
    }

    private fun draw(mesh: Mesh, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, r: Float, g: Float, b: Float) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, y, z)
        Matrix.scaleM(model, 0, sx, sy, sz)
        Matrix.multiplyMM(mvp, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, mvp, 0)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMvp"), 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uModel"), 1, false, model, 0)
        GLES20.glUniform4f(GLES20.glGetUniformLocation(program, "uColor"), r, g, b, 1f)
        val p = GLES20.glGetAttribLocation(program, "aPosition")
        val n = GLES20.glGetAttribLocation(program, "aNormal")
        GLES20.glEnableVertexAttribArray(p);GLES20.glEnableVertexAttribArray(n)
        mesh.vertices.position(0);mesh.normals.position(0)
        GLES20.glVertexAttribPointer(p, 3, GLES20.GL_FLOAT, false, 0, mesh.vertices)
        GLES20.glVertexAttribPointer(n, 3, GLES20.GL_FLOAT, false, 0, mesh.normals)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.count, GLES20.GL_UNSIGNED_SHORT, mesh.indices)
        GLES20.glDisableVertexAttribArray(p);GLES20.glDisableVertexAttribArray(n)
    }

    private fun makeSphere(stacks: Int, slices: Int): Mesh {
        val v=ArrayList<Float>();val n=ArrayList<Float>();val idx=ArrayList<Short>()
        for(i in 0..stacks){
            val phi=Math.PI*i/stacks
            for(j in 0..slices){
                val theta=2.0*Math.PI*j/slices
                val x=(sin(phi)*cos(theta)).toFloat()
                val y=cos(phi).toFloat()
                val z=(sin(phi)*sin(theta)).toFloat()
                v.add(x);v.add(y);v.add(z);n.add(x);n.add(y);n.add(z)
            }
        }
        for(i in 0 until stacks)for(j in 0 until slices){
            val a=(i*(slices+1)+j).toShort();val b=(a.toInt()+slices+1).toShort()
            idx.add(a);idx.add(b);idx.add((a.toInt()+1).toShort())
            idx.add((a.toInt()+1).toShort());idx.add(b);idx.add((b.toInt()+1).toShort())
        }
        return mesh(v,n,idx)
    }

    private fun makeCylinder(slices: Int): Mesh {
        val v=ArrayList<Float>();val n=ArrayList<Float>();val idx=ArrayList<Short>()
        for(j in 0..slices){
            val a=2.0*Math.PI*j/slices;val x=cos(a).toFloat();val z=sin(a).toFloat()
            v.add(x);v.add(-0.5f);v.add(z);n.add(x);n.add(0f);n.add(z)
            v.add(x);v.add(0.5f);v.add(z);n.add(x);n.add(0f);n.add(z)
        }
        for(j in 0 until slices){
            val a=(j*2).toShort();val b=(a.toInt()+1).toShort()
            idx.add(a);idx.add(b);idx.add((a.toInt()+2).toShort())
            idx.add((a.toInt()+2).toShort());idx.add(b);idx.add((b.toInt()+2).toShort())
        }
        return mesh(v,n,idx)
    }

    private fun mesh(v: List<Float>, n: List<Float>, idx: List<Short>): Mesh {
        fun floats(data: List<Float>): FloatBuffer {
            val buffer=ByteBuffer.allocateDirect(data.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            data.forEach { buffer.put(it) };buffer.position(0);return buffer
        }
        val indices=ByteBuffer.allocateDirect(idx.size*2).order(ByteOrder.nativeOrder()).asShortBuffer()
        idx.forEach { indices.put(it) };indices.position(0)
        return Mesh(floats(v),floats(n),indices,idx.size)
    }

    private fun linkProgram(vs: String, fs: String): Int {
        fun compile(type: Int, source: String): Int {
            val shader=GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader)
            val status=IntArray(1);GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,status,0)
            if(status[0]==0)throw RuntimeException("OpenGL shader: "+GLES20.glGetShaderInfoLog(shader))
            return shader
        }
        val vertex=compile(GLES20.GL_VERTEX_SHADER,vs);val fragment=compile(GLES20.GL_FRAGMENT_SHADER,fs)
        val p=GLES20.glCreateProgram()
        GLES20.glAttachShader(p,vertex);GLES20.glAttachShader(p,fragment);GLES20.glLinkProgram(p)
        val status=IntArray(1);GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,status,0)
        if(status[0]==0)throw RuntimeException("OpenGL program: "+GLES20.glGetProgramInfoLog(p))
        GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment)
        return p
    }
}
