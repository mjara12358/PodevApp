package com.mjdevp.podevapp

import android.content.Context
import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import com.googlecode.tesseract.android.TessBaseAPI
import org.opencv.core.Core
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import java.io.File
import java.io.InputStream
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

class ExtractRuta(val context: Context, private val activity: MainActivity) {

    private val tess = TessBaseAPI()
    private val folderTessDataName : String = "tessdata"
    private val pathDir = context.getExternalFilesDir(null).toString()

    // Clase de datos para almacenar las coordenadas
    data class BoundingBox(val x: Int, val y: Int, val width: Int, val height: Int)

    // Lista de rutas
    val rutas = listOf(
        "c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9", "c10",
        "c11", "c12", "c13", "c14", "c15", "c16", "e1", "e2", "e3",
        "e4", "e5", "e6", "e7"
    )

    init {
        val folder = File(pathDir, folderTessDataName)

        if (!folder.exists()){
            folder.mkdir()
        }

        if (folder.exists()){
            addFile ("eng.traineddata", R.raw.eng)
            addFile ("spa.traineddata", R.raw.spa)
            addFile ("spa_old.traineddata", R.raw.spa_old)
        }

    }

    private fun addFile (name : String, source : Int){
        val file = File ("$pathDir/$folderTessDataName/$name")
        if (!file.exists()){
            val inputStream : InputStream = context.resources.openRawResource(source)
            file.appendBytes(inputStream.readBytes())
            file.createNewFile()
        }
    }

    fun recycle (){
        tess.recycle()
    }

    // Función principal
    fun setUtils(bitmap: Bitmap): Bitmap {
        val mat = Mat()

        // Convertir Bitmap a Mat
        Utils.bitmapToMat(bitmap, mat)

        // Obtener las dimensiones de la imagen
        val width = mat.width()
        val height = mat.height()

        // Recortar la tercera parte superior de la imagen
        val croppedMat = cropImage(mat, width, height)

        // Convertir la imagen recortada a escala de grises
        val grayMat = convertToGrayScale(croppedMat)

        // Invertir los píxeles de la imagen binaria
        val thresholdMat = imgThreshold(grayMat, 130.0)

        // Invertir los píxeles de la imagen binaria
        val invertMat = imgInvert(thresholdMat, 130.0)

        // Convertir la imagen recortada a laplacian
        //val laplacianMat = convertToLaplacian(invertMat)

        // Detectar contornos en la imagen
        //val contoursMat = detectContours(invertMat)
        val (contoursImage, boundingBox) = detectContours(invertMat)

        var croppedBitmap = Bitmap.createBitmap(grayMat.width(), grayMat.height(), bitmap.config)
        Utils.matToBitmap(grayMat, croppedBitmap)

        extracToTextMLKit(croppedBitmap)

        return croppedBitmap
    }

    // Función para recortar la tercera y media parte superior de la imagen
    private fun cropImage(mat: Mat, width: Int, height: Int): Mat {
        val thirdWidth = width / 3

        // Crear un rectángulo que representa la tercera parte superior de la imagen
        val rect = Rect(0, 0, thirdWidth + thirdWidth/2, height)

        // Recortar la imagen usando la submatriz
        return mat.submat(rect)
    }

    // Función para convertir la imagen a escala de grises
    private fun convertToGrayScale(mat: Mat): Mat {
        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
        return grayMat
    }

    // Función para convertir la imagen a laplacian
    private fun convertToLaplacian(mat: Mat): Mat {
        val laplacianMat = Mat()
        Imgproc.Laplacian(mat, laplacianMat, CvType.CV_8U)
        return laplacianMat
    }

    // Función para aplicar un umbral (threshold) a la imagen
    private fun imgThreshold(mat: Mat, thers: Double): Mat {
        //println("> Proceso Threshold con Thresh: $thers")

        // Aplicar un umbral a la imagen
        val thresholdMat = Mat()
        Imgproc.threshold(mat, thresholdMat, thers, 255.0, Imgproc.THRESH_BINARY)

        return thresholdMat
    }

    // Función para aplicar un filtro de nitidez a la imagen
    private fun sharpenImage(mat: Mat): Mat {
        // Crear un nuevo objeto Mat para la imagen de salida
        val sharpened = Mat()

        // Definir un kernel para el filtro de nitidez
        val kernel = Mat(3, 3, CvType.CV_32F)
        kernel.put(0, 0, -1.0)
        kernel.put(0, 1, -1.0)
        kernel.put(0, 2, -1.0)
        kernel.put(1, 0, -1.0)
        kernel.put(1, 1, 9.0) // Punto central, valor más alto
        kernel.put(1, 2, -1.0)
        kernel.put(2, 0, -1.0)
        kernel.put(2, 1, -1.0)
        kernel.put(2, 2, -1.0)

        // Aplicar el filtro de nitidez utilizando la convolución
        Imgproc.filter2D(mat, sharpened, mat.depth(), kernel)

        // Devolver la imagen con mayor nitidez
        return sharpened
    }

    // Función para invertir los píxeles de la imagen binaria
    fun imgInvert(newImage: Mat, thers: Double): Mat {
        //println("> Proceso Invert con Thresh: $thers")

        // Verificar que la imagen no esté vacía
        if (newImage.empty()) {
            throw IllegalArgumentException("La imagen de entrada está vacía.")
        }

        // Convertir la imagen a escala de grises si no está en escala de grises
        val gray = Mat()
        if (newImage.channels() == 3) {
            Imgproc.cvtColor(newImage, gray, Imgproc.COLOR_BGR2GRAY)
        } else {
            newImage.copyTo(gray)
        }

        // Aplicar un umbral a la imagen
        val threshold = Mat()
        Imgproc.threshold(gray, threshold, thers, 255.0, Imgproc.THRESH_BINARY)

        // Invertir los píxeles de la imagen binaria
        val inverted = Mat()
        Core.bitwise_not(threshold, inverted)

        // Retornar la imagen procesada
        return inverted
    }

    // Modificar la función para devolver las coordenadas del primer contorno o coordenadas en cero
    private fun detectContours(src: Mat): Pair<Mat, BoundingBox> {
        // Aplicar el filtro de desenfoque gaussiano
        val blurred = Mat()
        Imgproc.GaussianBlur(src, blurred, Size(5.0, 5.0), 0.0)

        // Detectar los bordes en la imagen
        val edges = Mat()
        Imgproc.Canny(blurred, edges, 75.0, 200.0)

        // Encontrar los contornos en los bordes detectados
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            edges,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        // Filtrar los contornos según los criterios establecidos
        val filteredContours = mutableListOf<MatOfPoint>()
        val minAspectRatio = 0.3
        val maxAspectRatio = 0.5
        val minWidth = 30
        val maxWidth = 80
        val minHeight = 50
        val maxHeight = 100

        for (contour in contours) {
            val boundingRect = Imgproc.boundingRect(contour)
            val aspectRatio = boundingRect.width.toDouble() / boundingRect.height.toDouble()
            //println("> boundingRect: $boundingRect")
            //println("> aspectRatio: $aspectRatio")

            if (aspectRatio in minAspectRatio..maxAspectRatio &&
                boundingRect.width in minWidth..maxWidth &&
                boundingRect.height in minHeight..maxHeight
            ) {
                filteredContours.add(contour)
            }
        }

        // Dibujar todos los contornos en una nueva matriz
        val result = Mat.zeros(src.size(), CvType.CV_8UC3)
        Imgproc.drawContours(result, filteredContours, -1, Scalar(0.0, 255.0, 0.0), 2)

        // Inicializar las coordenadas en cero
        var boundingBox = BoundingBox(0, 0, 0, 0)

        // Imprimir las coordenadas del primer contorno filtrado, si existe
        if (filteredContours.isNotEmpty()) {
            val firstContour = filteredContours[0]
            val boundingRect = Imgproc.boundingRect(firstContour)
            boundingBox = BoundingBox(boundingRect.x, boundingRect.y, boundingRect.width, boundingRect.height)
            //println("> Primer contorno filtrado:")
            //println("> X: ${boundingBox.x}, Y: ${boundingBox.y}")
            //println("> Ancho: ${boundingBox.width}, Alto: ${boundingBox.height}")
        } else {
            println("No se encontraron contornos que cumplan con los criterios.")
        }

        // Devolver la matriz resultante y las coordenadas del primer contorno
        return Pair(result, boundingBox)
    }

    private fun cropImage(colorImage: Mat, x: Int, y: Int, width: Int, height: Int): Mat {
        // Asegurarse de que las coordenadas y dimensiones están dentro de los límites de la imagen
        val validX = max(0, x)
        val validY = max(0, y)
        val validWidth = min(colorImage.cols() - validX, width)
        val validHeight = min(colorImage.rows() - validY, height)

        // Crear un rectángulo que representa la región de la imagen a recortar
        val rect = Rect(validX, validY, validWidth, validHeight)

        // Recortar la imagen usando la submatriz
        return colorImage.submat(rect)
    }

    // Función para extraer texto de la imagen
    fun extracToTextMLKit(croppedBitmap : Bitmap) {
        // Configuración del cliente de reconocimiento de texto
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val image = InputImage.fromBitmap(croppedBitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                var resultText = visionText.text
                resultText = resultText.lowercase()

                // Normalizar el texto para eliminar acentos
                val normalizedText = Normalizer.normalize(resultText, Normalizer.Form.NFD)

                // Eliminar los caracteres de acento
                val textWithoutAccents = normalizedText.replace(Regex("\\p{M}"), "")

                Log.d("TextRecognition", "Texto reconocido: $textWithoutAccents")

                // Iterar sobre cada ruta en el array
                for (ruta in rutas) {
                    // Verificar si la ruta existe en el texto extraído (sin importar mayúsculas/minúsculas)
                    if (textWithoutAccents.contains(ruta, ignoreCase = true)) {
                        // Imprimir la ruta en mayúsculas y retornar la cadena
                        val rutaMayusculas = ruta.uppercase()
                        Log.d("OpenUtils", "Ruta encontrada: $rutaMayusculas")
                        if (activity.isTtsInitialized) {
                            activity.textToSpeech?.speak("Bus cerca $rutaMayusculas", TextToSpeech.QUEUE_FLUSH, null, null)
                        }
                    }else{

                    }
                }
            }
            .addOnFailureListener { e ->
                Log.d("TextRecognition", "Error: ${e.message}")
            }
    }
}