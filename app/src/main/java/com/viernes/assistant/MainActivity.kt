package com.viernes.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import org.json.JSONObject

import java.net.HttpURLConnection
import java.net.URL


class MainActivity : ComponentActivity() {

    private var speechRecognizer: SpeechRecognizer? = null

    private val textoReconocido = mutableStateOf("")
    private val escuchando = mutableStateOf(false)

    /*
     * Controla si el usuario activó el modo
     * de escucha continua.
     */
    private var escuchaContinua = false


    private val mensajes = mutableStateListOf(
        Mensaje(
            "YOU",
            "Hola Viernes."
        ),
        Mensaje(
            "VIERNES",
            "Hola. Estoy aquí."
        ),
        Mensaje(
            "YOU",
            "¿Qué tenemos hoy?"
        ),
        Mensaje(
            "VIERNES",
            "Revisando tu agenda..."
        )
    )


    private val pedirPermisoAudio =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { permitido ->

            if (permitido) {
                escuchaContinua = true
                iniciarReconocimiento()
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prepararReconocimiento()

        enableEdgeToEdge()

        setContent {

            ViernesHUD(
                mensajes = mensajes,
                texto = textoReconocido.value,
                escuchando = escuchando.value,
                onMicrofonoClick = {
                    alternarMicrofono()
                }
            )
        }
    }


    /* =========================================================
       PREPARAR RECONOCIMIENTO
       ========================================================= */

    private fun prepararReconocimiento() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            return
        }


        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(this)


        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {

                    escuchando.value = true
                }


                override fun onBeginningOfSpeech() {

                    escuchando.value = true
                }


                override fun onRmsChanged(
                    rmsdB: Float
                ) {
                }


                override fun onBufferReceived(
                    buffer: ByteArray?
                ) {
                }


                override fun onEndOfSpeech() {

                    escuchando.value = false
                }


                override fun onError(
                    error: Int
                ) {

                    escuchando.value = false


                    /*
                     * Si la escucha continua está activa,
                     * volvemos a iniciar otra sesión.
                     *
                     * Pequeña pausa para evitar que Android
                     * rechace el siguiente startListening().
                     */

                    if (escuchaContinua) {

                        android.os.Handler(
                            android.os.Looper.getMainLooper()
                        ).postDelayed(
                            {
                                if (escuchaContinua) {
                                    iniciarReconocimiento()
                                }
                            },
                            500
                        )
                    }
                }


                override fun onResults(
                    results: Bundle?
                ) {

                    escuchando.value = false


                    val resultados =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )


                    val texto =
                        resultados
                            ?.firstOrNull()
                            ?.trim()
                            ?: ""


                    if (texto.isNotEmpty()) {

                        textoReconocido.value = texto


                        /*
                         * Solo activamos a Viernes cuando
                         * la frase empieza con "Viernes".
                         */

                        procesarPalabraActivacion(texto)
                    }


                    /*
                     * Volvemos a escuchar automáticamente.
                     */

                    if (escuchaContinua) {

                        android.os.Handler(
                            android.os.Looper.getMainLooper()
                        ).postDelayed(
                            {
                                if (escuchaContinua) {
                                    iniciarReconocimiento()
                                }
                            },
                            500
                        )
                    }
                }


                override fun onPartialResults(
                    partialResults: Bundle?
                ) {

                    val resultados =
                        partialResults?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )


                    val texto =
                        resultados?.firstOrNull()


                    if (!texto.isNullOrBlank()) {

                        textoReconocido.value = texto
                    }
                }


                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {
                }
            }
        )
    }


    /* =========================================================
       PALABRA DE ACTIVACIÓN
       ========================================================= */

    private fun procesarPalabraActivacion(
        textoOriginal: String
    ) {

        val texto =
            textoOriginal
                .trim()


        val textoMinusculas =
            texto.lowercase()


        val activado =
            textoMinusculas == "viernes" ||
                    textoMinusculas.startsWith("viernes ") ||
                    textoMinusculas.startsWith("viernes,") ||
                    textoMinusculas.startsWith("viernes.")


        if (!activado) {

            return
        }


        /*
         * Quitamos "Viernes" de la orden.
         *
         * Ejemplo:
         *
         * "Viernes qué tengo mañana"
         *
         * se convierte en:
         *
         * "qué tengo mañana"
         */

        var comando =
            texto
                .replaceFirst(
                    Regex(
                        "^viernes[,.]?\\s*",
                        RegexOption.IGNORE_CASE
                    ),
                    ""
                )
                .trim()


        /*
         * Si solamente dijeron "Viernes",
         * no mandamos una consulta vacía.
         */

        if (comando.isEmpty()) {

            mensajes.add(
                Mensaje(
                    "YOU",
                    texto
                )
            )


            mensajes.add(
                Mensaje(
                    "VIERNES",
                    "Te escucho."
                )
            )


            return
        }


        enviarMensajeAlServidor(
            comando,
            textoOriginal
        )
    }


    /* =========================================================
       BOTON DEL MICROFONO
       ========================================================= */

    private fun alternarMicrofono() {

        if (escuchaContinua) {

            escuchaContinua = false

            speechRecognizer?.stopListening()

            escuchando.value = false

            return
        }


        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            pedirPermisoAudio.launch(
                Manifest.permission.RECORD_AUDIO
            )

            return
        }


        escuchaContinua = true

        iniciarReconocimiento()
    }


    /* =========================================================
       INICIAR RECONOCIMIENTO
       ========================================================= */

    private fun iniciarReconocimiento() {

        if (!escuchaContinua) {
            return
        }


        val intent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )


                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "es-CO"
                )


                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "es-CO"
                )


                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
                )


                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    1
                )
            }


        textoReconocido.value = ""


        escuchando.value = true


        speechRecognizer?.startListening(
            intent
        )
    }


    /* =========================================================
       CONEXION CON RENDER
       ========================================================= */

    private fun enviarMensajeAlServidor(
        comando: String,
        textoOriginal: String
    ) {

        if (comando.isBlank()) {
            return
        }


        mensajes.add(
            Mensaje(
                "YOU",
                textoOriginal
            )
        )


        mensajes.add(
            Mensaje(
                "VIERNES",
                "Procesando..."
            )
        )


        Thread {

            var conexion: HttpURLConnection? = null


            try {

                val url =
                    URL(
                        "https://viernes-3wyd.onrender.com/chat"
                    )


                conexion =
                    url.openConnection()
                            as HttpURLConnection


                conexion.requestMethod =
                    "POST"


                conexion.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
                )


                conexion.setRequestProperty(
                    "Accept",
                    "application/json"
                )


                conexion.doOutput =
                    true


                conexion.connectTimeout =
                    15000


                conexion.readTimeout =
                    30000


                val json =
                    JSONObject().apply {

                        put(
                            "mensaje",
                            comando
                        )
                    }


                conexion.outputStream.use { output ->

                    output.write(
                        json.toString()
                            .toByteArray(
                                Charsets.UTF_8
                            )
                    )
                }


                val codigo =
                    conexion.responseCode


                if (codigo in 200..299) {

                    val respuesta =
                        conexion.inputStream
                            .bufferedReader()
                            .use {
                                it.readText()
                            }


                    val jsonRespuesta =
                        JSONObject(
                            respuesta
                        )


                    val mensajeViernes =
                        jsonRespuesta.optString(
                            "respuesta",
                            "No recibí una respuesta válida."
                        )


                    runOnUiThread {

                        reemplazarUltimoMensaje(
                            Mensaje(
                                "VIERNES",
                                mensajeViernes
                            )
                        )
                    }

                } else {

                    runOnUiThread {

                        reemplazarUltimoMensaje(
                            Mensaje(
                                "SYSTEM",
                                "Error del servidor: $codigo"
                            )
                        )
                    }
                }


            } catch (e: Exception) {

                runOnUiThread {

                    reemplazarUltimoMensaje(
                        Mensaje(
                            "SYSTEM",
                            "No pude conectar con Viernes."
                        )
                    )
                }

            } finally {

                conexion?.disconnect()
            }

        }.start()
    }


    /* =========================================================
       REEMPLAZAR ULTIMO MENSAJE
       ========================================================= */

    private fun reemplazarUltimoMensaje(
        mensaje: Mensaje
    ) {

        if (mensajes.isNotEmpty()) {

            mensajes[
                mensajes.lastIndex
            ] = mensaje

        } else {

            mensajes.add(
                mensaje
            )
        }
    }


    override fun onDestroy() {

        escuchaContinua = false

        speechRecognizer?.destroy()

        speechRecognizer = null

        super.onDestroy()
    }
}


/* =========================================================
   MENSAJE
   ========================================================= */

data class Mensaje(
    val usuario: String,
    val mensaje: String
)


/* =========================================================
   COLORES
   ========================================================= */

private val Fondo = Color(0xFF010308)
private val Cyan = Color(0xFF00EFFF)
private val Azul = Color(0xFF008CFF)
private val Blanco = Color(0xFFE8FCFF)
private val CyanOscuro = Color(0xFF4D8994)
private val Verde = Color(0xFF35FFAA)


/* =========================================================
   PANTALLA PRINCIPAL
   ========================================================= */

@Composable
fun ViernesHUD(
    mensajes: List<Mensaje>,
    texto: String,
    escuchando: Boolean,
    onMicrofonoClick: () -> Unit
) {

    val animation =
        rememberInfiniteTransition()


    val scan =
        animation.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = 4200,
                            easing = LinearEasing
                        ),
                    repeatMode =
                        RepeatMode.Restart
                )
        )


    val pulse =
        animation.animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = 1200
                        ),
                    repeatMode =
                        RepeatMode.Reverse
                )
        )


    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Fondo)
    ) {

        FondoHUD(
            scan.value
        )


        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 16.dp,
                        vertical = 48.dp
                    ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            /* =================================================
               TITULO
               ================================================= */

            Text(
                text = "VIERNES",
                color = Blanco,
                fontSize = 28.sp,
                fontWeight =
                    FontWeight.Light,
                letterSpacing = 7.sp
            )


            Text(
                text = "PERSONAL AI",
                color = CyanOscuro,
                fontSize = 9.sp,
                letterSpacing = 4.sp
            )


            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )


            /* =================================================
               ESTADO
               ================================================= */

            Estado()


            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )


            /* =================================================
               CHAT
               ================================================= */

            PanelHUD(
                titulo = "CONVERSATION",
                subtitulo = "LIVE",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(190.dp)
            ) {

                Chat(
                    mensajes = mensajes,
                    escuchando = escuchando
                )
            }


            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            /* =================================================
               AGENDA
               ================================================= */

            PanelHUD(
                titulo = "EVENTS",
                subtitulo = "AGENDA",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(155.dp)
            ) {

                Agenda()
            }


            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            /* =================================================
               SISTEMAS
               ================================================= */

            PanelHUD(
                titulo = "SYSTEMS",
                subtitulo = "STATUS",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(145.dp)
            ) {

                Sistemas()
            }


            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            /* =================================================
               MICROFONO
               ================================================= */

            Microfono(
                pulse = pulse.value,
                escuchando = escuchando,
                onClick = onMicrofonoClick
            )


            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )


            Text(
                text =
                    if (escuchando) {
                        "LISTENING..."
                    } else {
                        "TAP TO SPEAK"
                    },
                color = CyanOscuro,
                fontSize = 8.sp,
                letterSpacing = 3.sp
            )
        }
    }
}


/* =========================================================
   FONDO HUD
   ========================================================= */

@Composable
fun FondoHUD(
    scan: Float
) {

    Canvas(
        modifier =
            Modifier.fillMaxSize()
    ) {

        val separacion =
            45.dp.toPx()


        var x = 0f

        while (x < size.width) {

            drawLine(
                color =
                    Cyan.copy(
                        alpha = 0.035f
                    ),
                start =
                    Offset(
                        x,
                        0f
                    ),
                end =
                    Offset(
                        x,
                        size.height
                    ),
                strokeWidth = 1f
            )

            x += separacion
        }


        var y = 0f

        while (y < size.height) {

            drawLine(
                color =
                    Cyan.copy(
                        alpha = 0.035f
                    ),
                start =
                    Offset(
                        0f,
                        y
                    ),
                end =
                    Offset(
                        size.width,
                        y
                    ),
                strokeWidth = 1f
            )

            y += separacion
        }


        val linea =
            size.height * scan


        drawLine(
            color =
                Cyan.copy(
                    alpha = 0.20f
                ),
            start =
                Offset(
                    0f,
                    linea
                ),
            end =
                Offset(
                    size.width,
                    linea
                ),
            strokeWidth = 2f
        )
    }
}


/* =========================================================
   ESTADO
   ========================================================= */

@Composable
fun Estado() {

    Row(
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Box(
            modifier =
                Modifier
                    .size(7.dp)
                    .shadow(
                        elevation = 4.dp,
                        shape = CircleShape,
                        ambientColor =
                            Verde.copy(
                                alpha = 0.5f
                            ),
                        spotColor =
                            Verde.copy(
                                alpha = 0.5f
                            )
                    )
                    .background(
                        Verde,
                        CircleShape
                    )
        )


        Spacer(
            modifier =
                Modifier.width(8.dp)
        )


        Text(
            text = "SYSTEM ONLINE",
            color = Verde,
            fontSize = 10.sp,
            letterSpacing = 2.sp
        )
    }
}


/* =========================================================
   PANEL HUD
   ========================================================= */

@Composable
fun PanelHUD(
    titulo: String,
    subtitulo: String,
    modifier: Modifier,
    contenido: @Composable () -> Unit
) {

    Box(
        modifier =
            modifier
                .shadow(
                    elevation = 4.dp,
                    ambientColor =
                        Cyan.copy(
                            alpha = 0.25f
                        ),
                    spotColor =
                        Azul.copy(
                            alpha = 0.30f
                        )
                )
    ) {

        Canvas(
            modifier =
                Modifier.fillMaxSize()
        ) {

            val corte =
                14.dp.toPx()


            val marco =
                Path()


            marco.moveTo(
                corte,
                0f
            )


            marco.lineTo(
                size.width - corte,
                0f
            )


            marco.lineTo(
                size.width,
                corte
            )


            marco.lineTo(
                size.width,
                size.height - corte
            )


            marco.lineTo(
                size.width - corte,
                size.height
            )


            marco.lineTo(
                corte,
                size.height
            )


            marco.lineTo(
                0f,
                size.height - corte
            )


            marco.lineTo(
                0f,
                corte
            )


            marco.close()


            drawPath(
                path = marco,
                color =
                    Cyan.copy(
                        alpha = 0.025f
                    )
            )


            drawPath(
                path = marco,
                color =
                    Cyan.copy(
                        alpha = 0.75f
                    ),
                style =
                    Stroke(
                        width =
                            1.5.dp.toPx()
                    )
            )


            drawLine(
                color =
                    Blanco.copy(
                        alpha = 0.8f
                    ),
                start =
                    Offset(
                        corte,
                        0f
                    ),
                end =
                    Offset(
                        corte +
                                38.dp.toPx(),
                        0f
                    ),
                strokeWidth =
                    2.dp.toPx()
            )


            drawLine(
                color =
                    Blanco.copy(
                        alpha = 0.7f
                    ),
                start =
                    Offset(
                        0f,
                        corte
                    ),
                end =
                    Offset(
                        0f,
                        corte +
                                25.dp.toPx()
                    ),
                strokeWidth =
                    2.dp.toPx()
            )
        }


        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 18.dp,
                        vertical = 14.dp
                    )
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    text = titulo,
                    color = Blanco,
                    fontSize = 13.sp,
                    fontWeight =
                        FontWeight.Medium,
                    letterSpacing = 3.sp
                )


                Spacer(
                    modifier =
                        Modifier.width(9.dp)
                )


                Text(
                    text = subtitulo,
                    color = CyanOscuro,
                    fontSize = 8.sp,
                    letterSpacing = 2.sp
                )
            }


            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            contenido()
        }
    }
}


/* =========================================================
   CHAT
   ========================================================= */

@Composable
fun Chat(
    mensajes: List<Mensaje>,
    escuchando: Boolean
) {

    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(135.dp)
    ) {

        items(
            count = mensajes.size
        ) { indice ->

            val mensaje =
                mensajes[indice]


            LineaChat(
                usuario =
                    mensaje.usuario,
                mensaje =
                    mensaje.mensaje
            )


            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )
        }


        item {

            Text(
                text =
                    if (escuchando) {
                        "● LISTENING"
                    } else {
                        "● READY FOR INPUT"
                    },
                color = CyanOscuro,
                fontSize = 8.sp,
                letterSpacing = 2.sp
            )
        }
    }
}


@Composable
fun LineaChat(
    usuario: String,
    mensaje: String
) {

    Column {

        Text(
            text = usuario,
            color = Cyan,
            fontSize = 8.sp,
            letterSpacing = 2.sp
        )


        Spacer(
            modifier =
                Modifier.height(2.dp)
        )


        Text(
            text = mensaje,
            color = Blanco,
            fontSize = 13.sp
        )
    }
}


/* =========================================================
   AGENDA
   ========================================================= */

@Composable
fun Agenda() {

    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(105.dp)
    ) {

        item {

            Evento(
                hora = "15:00",
                nombre = "Ensayo de clarinete"
            )
        }


        item {

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )
        }


        item {

            Evento(
                hora = "16:00",
                nombre = "Ensayo de clarinete"
            )
        }


        item {

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )
        }


        item {

            Evento(
                hora = "18:00",
                nombre = "Actividad programada"
            )
        }


        item {

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )
        }


        item {

            Evento(
                hora = "20:00",
                nombre = "Tiempo libre"
            )
        }
    }
}


@Composable
fun Evento(
    hora: String,
    nombre: String
) {

    Row(
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text = hora,
            color = Cyan,
            fontSize = 11.sp
        )


        Spacer(
            modifier =
                Modifier.width(13.dp)
        )


        Box(
            modifier =
                Modifier
                    .size(5.dp)
                    .background(
                        Cyan,
                        CircleShape
                    )
        )


        Spacer(
            modifier =
                Modifier.width(10.dp)
        )


        Text(
            text = nombre,
            color = Blanco,
            fontSize = 12.sp
        )
    }
}


/* =========================================================
   SISTEMAS
   ========================================================= */

@Composable
fun Sistemas() {

    Sistema(
        nombre = "OLLAMA",
        estado = "ONLINE",
        activo = true
    )


    Spacer(
        modifier =
            Modifier.height(10.dp)
    )


    Sistema(
        nombre = "NVIDIA AI",
        estado = "STANDBY",
        activo = false
    )


    Spacer(
        modifier =
            Modifier.height(10.dp)
    )


    Sistema(
        nombre = "GEMINI",
        estado = "STANDBY",
        activo = false
    )
}


@Composable
fun Sistema(
    nombre: String,
    estado: String,
    activo: Boolean
) {

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Box(
                modifier =
                    Modifier
                        .size(5.dp)
                        .background(
                            if (activo) {
                                Verde
                            } else {
                                CyanOscuro
                            },
                            CircleShape
                        )
            )


            Spacer(
                modifier =
                    Modifier.width(9.dp)
            )


            Text(
                text = nombre,
                color = Blanco,
                fontSize = 11.sp,
                letterSpacing = 2.sp
            )
        }


        Text(
            text = estado,
            color =
                if (activo) {
                    Verde
                } else {
                    CyanOscuro
                },
            fontSize = 8.sp
        )
    }
}


/* =========================================================
   MICROFONO FUTURISTA
   ========================================================= */

@Composable
fun Microfono(
    pulse: Float,
    escuchando: Boolean,
    onClick: () -> Unit
) {

    Box(
        modifier =
            Modifier
                .size(82.dp)
                .clickable {
                    onClick()
                },
        contentAlignment =
            Alignment.Center
    ) {

        Canvas(
            modifier =
                Modifier.size(82.dp)
        ) {

            val cx =
                size.width / 2f


            val cy =
                size.height / 2f


            val radio =
                size.minDimension * 0.27f


            drawCircle(
                color =
                    Cyan.copy(
                        alpha =
                            if (escuchando) {
                                0.25f
                            } else {
                                0.12f
                            }
                    ),
                radius =
                    radio * 1.75f,
                style =
                    Stroke(
                        width = 1.2f
                    )
            )


            drawCircle(
                color =
                    Cyan.copy(
                        alpha =
                            if (escuchando) {
                                0.45f +
                                        pulse * 0.25f
                            } else {
                                0.25f +
                                        pulse * 0.18f
                            }
                    ),
                radius =
                    radio *
                            (
                                    1.38f +
                                            pulse * 0.08f
                                    ),
                style =
                    Stroke(
                        width = 2f
                    )
            )


            val micWidth =
                radio * 0.75f


            val micHeight =
                radio * 1.45f


            val micTop =
                cy -
                        micHeight / 2f


            drawRoundRect(
                color = Cyan,
                topLeft =
                    Offset(
                        cx -
                                micWidth / 2f,
                        micTop
                    ),
                size =
                    androidx.compose.ui.geometry.Size(
                        micWidth,
                        micHeight
                    ),
                cornerRadius =
                    androidx.compose.ui.geometry.CornerRadius(
                        micWidth / 2f,
                        micWidth / 2f
                    )
            )


            drawRoundRect(
                color = Fondo,
                topLeft =
                    Offset(
                        cx -
                                micWidth / 2f +
                                2.5f,
                        micTop +
                                2.5f
                    ),
                size =
                    androidx.compose.ui.geometry.Size(
                        micWidth - 5f,
                        micHeight - 5f
                    ),
                cornerRadius =
                    androidx.compose.ui.geometry.CornerRadius(
                        (micWidth - 5f) / 2f,
                        (micWidth - 5f) / 2f
                    )
            )


            val soporte =
                Path()


            soporte.moveTo(
                cx -
                        radio * 0.55f,
                cy +
                        radio * 0.05f
            )


            soporte.quadraticBezierTo(
                cx -
                        radio * 0.55f,
                cy +
                        radio * 0.75f,
                cx,
                cy +
                        radio * 0.75f
            )


            soporte.quadraticBezierTo(
                cx +
                        radio * 0.55f,
                cy +
                        radio * 0.75f,
                cx +
                        radio * 0.55f,
                cy +
                        radio * 0.05f
            )


            drawPath(
                path = soporte,
                color = Cyan,
                style =
                    Stroke(
                        width = 2.5f,
                        cap = StrokeCap.Round
                    )
            )


            drawLine(
                color = Cyan,
                start =
                    Offset(
                        cx,
                        cy +
                                radio * 0.75f
                    ),
                end =
                    Offset(
                        cx,
                        cy +
                                radio * 1.05f
                    ),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )


            drawLine(
                color = Cyan,
                start =
                    Offset(
                        cx -
                                radio * 0.45f,
                        cy +
                                radio * 1.05f
                    ),
                end =
                    Offset(
                        cx +
                                radio * 0.45f,
                        cy +
                                radio * 1.05f
                    ),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )


            drawLine(
                color =
                    Cyan.copy(
                        alpha = 0.7f
                    ),
                start =
                    Offset(
                        cx -
                                radio * 1.8f,
                        cy
                    ),
                end =
                    Offset(
                        cx -
                                radio * 1.35f,
                        cy
                    ),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )


            drawLine(
                color =
                    Cyan.copy(
                        alpha = 0.7f
                    ),
                start =
                    Offset(
                        cx +
                                radio * 1.35f,
                        cy
                    ),
                end =
                    Offset(
                        cx +
                                radio * 1.8f,
                        cy
                    ),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
        }
    }
}