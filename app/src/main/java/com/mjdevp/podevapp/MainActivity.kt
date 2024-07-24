package com.mjdevp.podevapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mjdevp.podevapp.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import com.ingenieriiajhr.jhrCameraX.CameraJhr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    lateinit var binding : ActivityMainBinding
    lateinit var cameraJhr: CameraJhr
    lateinit var classifyTf: ClasiffyTf
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraExecutor: ExecutorService
    var ifStartCamera = false

    private var respuest: ArrayList<Respuestas>? = null
    private var escuchando: TextView? = null
    private var accionEscucha: Button? = null
    private var saludado = false
    private var respActual: String = ""
    private var respAnterior: String = ""
    lateinit var avatarImageView: ImageView

    private lateinit var auth: AuthManager
    private lateinit var analytics: AnalyticsManager
    var user: FirebaseUser? = null
    private lateinit var otpSmsReceiver: OtpSmsReceiver
    var inLogin: Boolean = false
    var inConfigProfile: Boolean = false
    var completedProfile: Boolean = true

    var phoneNumber: String? = null
    private lateinit var dataProfile: DataProfile

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var geocodingService: GeocodingService

    private var objeto: String = ""
    private var acumObject: String = ""
    private var contadorObjeto = 0
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var hablando = false
    private var enEscucha = false

    companion object {
        private const val TAG = "CameraXApp"
        private val RECONOCEDOR_VOZ = 7
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {

            }.toTypedArray()

        const val INPUT_SIZE = 224
        const val LOCATION_PERMISSION_REQUEST_CODE = 1
        const val NOTIFICATION_CHANNEL_ID = "noti_podev"
    }

    val classes = arrayOf("paradabus","bus","crucepeatonal","cebra","pare","peatonalrojo","peatonalverde","sendapodotactil")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        cameraJhr = CameraJhr(this)
        classifyTf = ClasiffyTf(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        requestAllPermissions()
        inicializar()
    }

    fun inicializar() {
        escuchando = binding.txtReconocido
        respuest = proveerDatos()
        accionEscucha = binding.btnControlVoz
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        dataProfile = DataProfile(this)
        geocodingService = GeocodingService(this, dataProfile, fusedLocationProviderClient, this)
        analytics = AnalyticsManager(this)
        auth = AuthManager(this, geocodingService, analytics, dataProfile)
        user = auth.getCurrentUser()
        otpSmsReceiver = OtpSmsReceiver(auth)
        setupAuthStateListener()
        avatarImageView = binding.avatar

        if(user == null){
            preparaRespuesta("inicio")
            changeAvatarImage(R.drawable.imglogogris)
        }else{
            preparaRespuesta("user")
            geocodingService.showCurrentLocation()
            for (profile in user!!.providerData){
                val providerId = profile.providerId
                if (providerId == "phone") {
                    phoneNumber = user!!.phoneNumber
                    dataProfile.phoneNumber = user!!.phoneNumber
                    dataProfile.checkProfileComplete()
                    geocodingService.startUpdatingLocation()
                    changeAvatarImage(R.drawable.imglogo)
                }
            }
            if(phoneNumber != null){
                upTokenNotification()
            }
        }

        lifecycleScope.launch {
            delay(10000)
            saludado = true
        }

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsInitialized = true
                textToSpeech?.setSpeechRate(1.0f)
            } else {
                Log.e("MainActivity", "Error al inicializar TextToSpeech")
            }
        }
    }

    // Permisos
    private fun requestAllPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECEIVE_SMS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Adding camera and audio permissions
        if (!allPermissionsGranted()) {
            permissionsToRequest.addAll(REQUIRED_PERMISSIONS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
                //Log.d("MainActivity", "Permisos concedidos")
            } else {
                //Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                Log.d("MainActivity", "Permisos no concedidos. La aplicación no podrá funcionar como se espera.")
                responder("Permisos no concedidos. La aplicación no podrá funcionar como se espera.")
            }
        }

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            for (i in permissions.indices) {
                when (permissions[i]) {
                    Manifest.permission.ACCESS_FINE_LOCATION -> {
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                            geocodingService.showCurrentLocation()
                        } else {
                            responder("Permiso de ubicación denegado. No se podrá mostrar información de la ubicación.")
                        }
                    }
                    Manifest.permission.RECEIVE_SMS -> {
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                            // Permiso de SMS concedido, puedes manejar aquí lo relacionado con SMS
                        } else {
                            responder("Permiso de SMS denegado. La aplicación no podrá recibir mensajes SMS para iniciar sesión con número de celular.")
                        }
                    }
                    Manifest.permission.POST_NOTIFICATIONS -> {
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                            // El SDK de FCM (y tu aplicación) pueden publicar notificaciones.
                        } else {
                            responder("Permiso de Notificaciones denegado. La aplicación no podrá recibir Notificaciones. " +
                                    "Si estás en desacuerdo con esto, autoriza las notificaciones desde configuraciones de apps.")
                        }
                    }
                }
            }
        }
    }

    // Process app
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == RECONOCEDOR_VOZ) {
            val reconocido = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val escuchado = reconocido?.get(0)

            // Verificar si escuchando y escuchado no son null
            if (escuchando != null && escuchado != null) {
                escuchando!!.text = escuchado
                preparaRespuesta(escuchado)
            } else {
                // Manejo de error o log adecuado
                Log.e("MainActivity", "Error: escuchando o escuchado es null")
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (cameraJhr.allpermissionsGranted() && !ifStartCamera){
            startCamera()
        }else{
            //Log.d("MainActivity", "La camara no pudo iniciar. Puede que falten permisos.")
        }
    }

    // Notification
    private fun upTokenNotification() {
        Firebase.messaging.token.addOnCompleteListener {
            if (!it.isSuccessful) {
                Log.w("MainActivity", "El token no pudo ser generado", it.exception)
                return@addOnCompleteListener
            }
            val token = it.result
            dataProfile.upTokenNotification(token)
        }
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID, "Notificaciones de podev", NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Estas notificaciones tienen un fin netamente informativo."
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Login
    suspend fun incognitoSingIn(){
        when(val result = auth.singInAnonymously()){
            is AuthRes.Success -> {
                geocodingService.showCurrentLocation()
                responder("Se inició sesión como invitado.")
                val params = Bundle().apply {
                    putString("Ingreso", "Invitado")
                }
                analytics.logEvent("LoginInv", params)
            }

            is AuthRes.Error -> {
                analytics.logError("Error Sing In Incognito: ${result.errorMessage}")
                responder("Hubo un error al iniciar sesión como invitado. Comprueba tu conexión a internet y luego intentalo de nuevo.")
            }
        }
    }

    fun newUserProfile() {
        inConfigProfile = true
        completedProfile = false
        dataProfile.upPodevWeb(true)
        responder("Completa tu perfil. Es necesario completar tu perfil para contactar contigo en caso de que se presenten errores con la " +
                "aplicación y para que puedas acceder a las funciones adicionales. Los datos que requerimos son: correo electrónico, " +
                "nombre, apellido y nombre y número de celular de un contacto de emergencia. " +
                "Para registrar estos datos sé muy específico con el dato al que te referirás y luego continúa con la información. " +
                "Por ejemplo, podrías indicar: mi correo es, y posteriormente especificas tu correo electrónico. No dejes espacios silenciosos, largos." +
                "Si quieres recordar los datos requeridos, solo pregunta: ¿Cuáles son los datos requeridos para mi perfil? " +
                "Solo al completar tu perfil saldrás del modo configuración y la aplicación seguirá con su funcionamiento normal")
    }

    // cerrar sesion
    fun logout() {
        auth.singOut()
        stopTextToSpeech()
        changeAvatarImage(R.drawable.imglogogris)

        val params = Bundle().apply {
            putString("Salida", "Sé cerro sesión")
        }
        analytics.logEvent("LoginOut", params)
    }

    private fun setupAuthStateListener() {
        auth.addAuthStateListener { firebaseAuth ->
            user = firebaseAuth.currentUser
        }
    }

    private fun stopAuthStateListener() {
        auth.removeAuthStateListener { firebaseAuth ->
            user = firebaseAuth.currentUser
        }
    }

    fun changeAvatarImage(resourceId: Int) {
        avatarImageView.setImageDrawable(ContextCompat.getDrawable(this, resourceId))
    }

    // Sms
    fun startListeningForSms() {
        val intentFilter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
        otpSmsReceiver.setAuthManager(auth)
        registerReceiver(otpSmsReceiver, intentFilter)
        //Log.d("MainActivity", "Se empezó a escuchar los SMS")

        // Stop listening after 5 minutes
        lifecycleScope.launch {
            delay(5 * 60 * 1000)
            stopListeningForSms()
        }
    }

    fun stopListeningForSms() {
        try {
            unregisterReceiver(otpSmsReceiver)
            //Log.d("MainActivity", "Dejar de escuchar SMS")
        } catch (e: IllegalArgumentException) {
            //Log.e("MainActivity", "Receptor no registrado")
        }
    }

    // Camera and classify
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        var lastClassifyTime = System.currentTimeMillis()

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Image Analysis
            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer { imageProxy ->
                        val bitmap = imageProxy.toBitmap() // Implement this function to convert imageProxy to Bitmap
                        val currentTime = System.currentTimeMillis()
                        if (user != null && inLogin == false && inConfigProfile == false && currentTime > lastClassifyTime + 500L) {
                            classifyImage(bitmap)
                            lastClassifyTime = currentTime
                        }
                        imageProxy.close()
                    })
                }

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer)

                ifStartCamera = true

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val buffer = this.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun classifyImage(img: Bitmap?) {
        val imgReScale = Bitmap.createScaledBitmap(img!!, INPUT_SIZE, INPUT_SIZE, false)
        val rotatedImg = imgReScale.rotate(90) // Rotar el Bitmap 90 grados
        classifyTf.listenerInterpreter(object : ReturnInterpreter {
            override fun classify(confidence: FloatArray, maxConfidence: Int) {
                val porcentaje = confidence[maxConfidence] * 100
                /*binding.txtResult.UiThread("${classes[0]} ${confidence[0].decimal()} \n${classes[1]} ${confidence[1].decimal()} \n${classes[2]} ${confidence[2].decimal()}" +
                        " \n${classes[3]} ${confidence[3].decimal()} \n${classes[4]} ${confidence[4].decimal()}" +
                        "\n${classes[5]} ${confidence[5].decimal()} \n${classes[6]} ${confidence[6].decimal()} \n${classes[7]} ${confidence[7].decimal()} \nMax: \n${classes[maxConfidence]} ${porcentaje.decimal()}")*/

                if (classes[maxConfidence].equals("peatonalrojo") && porcentaje >= 60 || classes[maxConfidence].equals("peatonalverde") && porcentaje >= 70 ||
                    classes[maxConfidence].equals("cebra") && porcentaje >= 99.96 || porcentaje >= 99.99 ){
                    //binding.txtResult.UiThread("${classes[maxConfidence]} ${porcentaje.decimal()}")

                    if (classes[maxConfidence].equals("pare")) {
                        objeto = "Señal de Pare"
                    } else if (classes[maxConfidence].equals("sendapodotactil")) {
                        objeto = "Senda podotactil cerca"
                    } else if (classes[maxConfidence].equals("cebra")) {
                        objeto = "Cebra peatonal alfrente"
                    } else if (classes[maxConfidence].equals("crucepeatonal")) {
                        objeto = "Señal de cruce peatonal"
                    } else if (classes[maxConfidence].equals("peatonalrojo")) {
                        objeto = "Semaforo peatonal en rojo"
                    } else if (classes[maxConfidence].equals("peatonalverde")) {
                        objeto = "Semaforo peatonal en verde"
                    } else if (classes[maxConfidence].equals("bus")) {
                        objeto = "Bus cerca"
                    } else if (classes[maxConfidence].equals("paradabus")) {
                        objeto = "Parada de transporte publico cerca"
                    }

                    if(acumObject == objeto && contadorObjeto >= 5 && !hablando && saludado && !enEscucha){
                        hablando = true
                        contadorObjeto = 0

                        if (isTtsInitialized) {
                            textToSpeech?.speak(objeto, TextToSpeech.QUEUE_FLUSH, null, null)
                        }

                        lifecycleScope.launch {
                            delay(4000)
                            hablando = false
                        }
                    }else if(acumObject == objeto && saludado){
                        contadorObjeto += 1
                    }else{
                        acumObject = objeto
                        contadorObjeto = 0
                    }
                }
            }
        })
        /**Preview Image Get */
        runOnUiThread {
            binding.imgBitMap.setImageBitmap(rotatedImg)
        }
        classifyTf.classify(rotatedImg)
    }

    private fun Bitmap.rotate(degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    // Format
    private fun TextView.UiThread(string: String){
        runOnUiThread {
            this.text = string
        }
    }

    private fun Float.decimal():String{return "%.6f".format(this) }

    fun extractPhoneNumberAndAddPrefix(input: String): String? {
        val digits = input.filter { it.isDigit() }
        return if (digits.length == 10) {
            "+57$digits"
        } else {
            null
        }
    }

    fun extractEmail(text: String): String {
        val phrase = "mi correo es"
        val startIndex = text.indexOf(phrase)

        if (startIndex != -1) {
            var emailText = text.substring(startIndex + phrase.length).trim()
            // Elimina todos los espacios en blanco y convierte a minúsculas
            emailText = emailText.replace("\\s".toRegex(), "").lowercase()

            // Regex pattern for matching an email address
            val emailRegex = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")

            val matchResult = emailRegex.find(emailText) // Find the first match

            if (matchResult != null) {
                return matchResult.value
            }
        }
        return "vacío"
    }

    fun extractName(text: String): String {
        val phrase = "mi nombre es"
        val startIndex = text.indexOf(phrase, ignoreCase = true)

        return if (startIndex != -1) {
            text.substring(startIndex + phrase.length).trim().split(" ")[0]
        } else {
            "vacío"
        }
    }

    fun extractLastName(text: String): String {
        val phrase = "mi apellido es"
        val startIndex = text.indexOf(phrase, ignoreCase = true)

        return if (startIndex != -1) {
            text.substring(startIndex + phrase.length).trim().split(" ")[0]
        } else {
            "vacío"
        }
    }

    fun extractEmergencyContactName(text: String): String {
        val phrase = "el nombre de mi contacto de emergencia es"
        val startIndex = text.indexOf(phrase, ignoreCase = true)

        return if (startIndex != -1) {
            text.substring(startIndex + phrase.length).trim().split(" ")[0]
        } else {
            "vacío"
        }
    }

    fun extractEmergencyContactPhone(text: String): String {
        // Filtra todos los dígitos de la cadena de texto
        val phoneNumber = text.filter { it.isDigit() }
        return if (phoneNumber.length == 10) phoneNumber else "vacío" // Verifica longitud de 10 dígitos
    }

    fun formatPhoneNumber(phoneNumber: String): String {
        return if (phoneNumber.length == 10) {
            "${phoneNumber.substring(0, 3)} ${phoneNumber.substring(3, 6)} ${phoneNumber.substring(6)}"
        } else {
            phoneNumber // Devuelve el número sin formato si no tiene 10 dígitos
        }
    }

    // Voz
    fun habla(voz: View?) {
        val habla = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-US")
        }
        startActivityForResult(habla, RECONOCEDOR_VOZ)
        enEscucha = true
    }

    fun preparaRespuesta(escuchado: String) {
        val normalizar = Normalizer.normalize(escuchado, Normalizer.Form.NFD)
        val sintilde = normalizar.replace("[^\\p{ASCII}]".toRegex(), "")

        var respuesta = respuest!![0].respuesta
        for (r in respuest!!) {
            val resultado = sintilde.lowercase(Locale.getDefault()).indexOf(r.cuestion)
            if (resultado != -1) {
                respuesta = r.respuesta
            }
        }

        if (sintilde.lowercase(Locale.getDefault()).contains("invitado")){
            responder("Procesando...")
            if(user == null){
                if (isNetworkAvailable(this)) {  // Verificar conexión a Internet
                    lifecycleScope.launch {
                        incognitoSingIn()
                    }
                } else {
                    responder("Comprueba tu conexión a internet y luego intentalo de nuevo.")
                }
            } else {
                responder("Ya estás en una sesión activa. Debes cerrar sesión antes de solicitar otro inicio de sesión.")
            }
            return
        }

        if (sintilde.lowercase(Locale.getDefault()).contains("celular es") ||
            sintilde.lowercase(Locale.getDefault()).contains("numero de celular es")){
            responder("Procesando...")
            if(user == null){
                if (isNetworkAvailable(this)) {  // Verificar conexión a Internet
                    inLogin = true
                    responder("Enviando información.")
                    phoneNumber = extractPhoneNumberAndAddPrefix(sintilde)
                    auth.sendSMS(phoneNumber)
                } else {
                    responder("Comprueba tu conexión a internet y luego intentalo de nuevo.")
                }
            } else {
                responder("Ya estás en una sesión activa. Debes cerrar sesión antes de solicitar otro inicio de sesión.")
            }

            return
        }

        if (inLogin){
            if (sintilde.lowercase(Locale.getDefault()).contains("codigo es")){

                val codigo = sintilde.filter { it.isDigit() }
                val codigoVerificacion: String = codigo

                if (codigoVerificacion.isNotEmpty()) {
                    try {
                        auth.singWhitNumPhone(codigoVerificacion)
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error al procesar el código de verificación", e)
                    }
                } else {
                    Log.d("MainActivity", "Ingrese el código de verificación")
                }
                return
            }
            responder("En este momento estas en un proceso activo de inicio de sesión. Debes completar este proceso para " +
                    "que la aplicación vuelva a su funcionamiento normal.")
            return
        }

        if(phoneNumber != null){
            if (sintilde.lowercase(Locale.getDefault()).contains("configurar perfil") ||
                sintilde.lowercase(Locale.getDefault()).contains("configurar mi perfil") ||
                sintilde.lowercase(Locale.getDefault()).contains("configuracion de perfil") ||
                sintilde.lowercase(Locale.getDefault()).contains("configuracion de mi perfil")){
                if (isNetworkAvailable(this)) {  // Verificar conexión a Internet
                    inConfigProfile = true
                    responder("Ahora estás en modo de configuración. Para configurar puedes acceder a editar el dato que desees. Para recordar los datos requeridos en el " +
                            "perfil puedes preguntar: ¿Cuáles son los datos requeridos para mi perfil?. Para consultar los datos almacenados en tu " +
                            "perfil solo solicita a la asistencia lo siguiente: dame mi información de perfil. Cuando hayas completado este proceso solo " +
                            "indica, terminar configuración, así saldrás del modo configuración.")
                } else {
                    responder("Configurar tu información de perfil requiere conexión a internet. Comprueba tu conexión e inténtalo de nuevo.")
                }
                return
            }

            if (sintilde.lowercase(Locale.getDefault()).contains("informacion de mi perfil") ||
                sintilde.lowercase(Locale.getDefault()).contains("informacion de perfil") ||
                sintilde.lowercase(Locale.getDefault()).contains("informacion registrada")){

                dataProfile.getDataProfile()
                val formattedPhoneNumber = dataProfile.numeroContacto?.let { formatPhoneNumber(it) }

                responder("Los datos almacenados en tu perfil son los siguientes: Correo electrónico: " + dataProfile.email + ". " +
                        "Nombre: " + dataProfile.nombre + ". Apellido: " + dataProfile.apellido + ". Nombre de contacto de emergencia: " + dataProfile.nombreContacto + ". " +
                        "Numero de celular de contacto de emergencia: " +formattedPhoneNumber+ ". Podev Web: " + dataProfile.PodevWeb + ". Si quieres editar esta información solo indica: configurar perfil.")
                return
            }
        }

        if(inConfigProfile){
            if (sintilde.lowercase(Locale.getDefault()).contains("¿Cuáles son los datos requeridos para mi perfil?") ||
                sintilde.lowercase(Locale.getDefault()).contains("datos requeridos para mi perfil") ||
                sintilde.lowercase(Locale.getDefault()).contains("informacion requerida para mi perfil")){
                responder("Los datos requeridos para tu perfil son. Correo electrónico, para registrarlo solo indica: " +
                        "mi correo es, y luego dices tu correo. El comando y tu correo en una misma frase. También tu nombre, para " +
                        "registrarlo solo tienes que decir: mi nombre es. Luego tu apellido, " +
                        "para registrarlo solo indica: mi apellido es. También está el nombre de un contacto de emergencia, " +
                        "para registrarlo solo tienes que decir: el nombre de mi contacto de emergencia es. Y por último el " +
                        "número de celular de un contacto de emergencia, para registrarlo indica: el número de celular de mi " +
                        "contacto de emergencia es.")
                return
            }

            if (sintilde.lowercase(Locale.getDefault()).contains("correo es")){
                var email = extractEmail(sintilde)
                if(phoneNumber != null && email != "vacío"){

                    dataProfile.upEmail(email)

                    responder("Se registró el correo " + email + ". Si está incorrecto, inténtalo de nuevo. " +
                            "Recuerda especificar como guion o guion bajo si tu correo cuenta con estos caracteres.")

                    lifecycleScope.launch {
                        delay(20000)
                        dataProfile.checkProfileComplete()
                    }
                } else {
                    responder("Hubo un error al escuchar la entrada. Por favor inténtalo de nuevo.")
                }
                return
            }

            if (sintilde.lowercase(Locale.getDefault()).contains("mi nombre es")){
                var nombre = extractName(sintilde)
                if(phoneNumber != null && nombre != "vacío"){

                    dataProfile.upNombre(nombre!!)

                    responder("Se registró el nombre " + nombre + ". Si está incorrecto, inténtalo de nuevo.")
                    lifecycleScope.launch {
                        delay(12000)
                        dataProfile.checkProfileComplete()
                    }
                } else {
                    responder("Hubo un error al escuchar la entrada. Por favor inténtalo de nuevo.")
                }
                return
            }

            if (sintilde.lowercase(Locale.getDefault()).contains("mi apellido es")){
                var apellido = extractLastName(sintilde)
                if(phoneNumber != null && apellido != "vacío"){

                    dataProfile.upApellido(apellido!!)

                    responder("Se registró el apellido " + apellido + ". Si está incorrecto, inténtalo de nuevo.")
                    lifecycleScope.launch {
                        delay(12000)
                        dataProfile.checkProfileComplete()
                    }
                } else {
                    responder("Hubo un error al escuchar la entrada. Por favor inténtalo de nuevo.")
                }
                return
            }

            if (sintilde.lowercase(Locale.getDefault()).contains("nombre de mi contacto de emergencia es") ||
                sintilde.lowercase(Locale.getDefault()).contains("nombre de contacto de emergencia")){
                var nombreContacto = extractEmergencyContactName(sintilde)
                if(phoneNumber != null && nombreContacto != "vacío"){

                    dataProfile.upNombreContacto(nombreContacto!!)

                    responder("Se registró el nombre de contacto " + nombreContacto + ". Si está incorrecto, inténtalo de nuevo.")
                    lifecycleScope.launch {
                        delay(12000)
                        dataProfile.checkProfileComplete()
                    }
                } else {
                    responder("Hubo un error al escuchar la entrada. Por favor inténtalo de nuevo.")
                }
                return
            }

            if (sintilde.lowercase(Locale.getDefault()).contains("numero de celular de mi contacto de emergencia es") ||
                sintilde.lowercase(Locale.getDefault()).contains("numero de celular de contacto de emergencia")){
                var numeroContacto = extractEmergencyContactPhone(sintilde)
                if(phoneNumber != null && numeroContacto != "vacío"){

                    dataProfile.upNumeroContacto(numeroContacto!!)
                    val numeroContactoForm = formatPhoneNumber(numeroContacto)

                    responder("Se registró el número de contacto " + numeroContactoForm + ". Si está incorrecto, inténtalo de nuevo.")
                    lifecycleScope.launch {
                        delay(15000)
                        dataProfile.checkProfileComplete()
                    }
                } else {
                    responder("Hubo un error al escuchar la entrada. Por favor inténtalo de nuevo.")
                }
                return
            }

            if (sintilde.lowercase(Locale.getDefault()).contains("desactivar podev web") ||
                sintilde.lowercase(Locale.getDefault()).contains("desactivar poder web") ||
                sintilde.lowercase(Locale.getDefault()).contains("desactivar")){
                dataProfile.upPodevWeb(false)
                dataProfile.PodevWeb = "Desactivado"
                responder("Se desactivó Podev web.")
                return
            }

            if (sintilde.lowercase(Locale.getDefault()).contains("activar podev web") ||
                sintilde.lowercase(Locale.getDefault()).contains("activar poder web") ||
                sintilde.lowercase(Locale.getDefault()).contains("activar")){
                dataProfile.upPodevWeb(true)
                dataProfile.PodevWeb = "Activado"
                responder("Se activó Podev web.")
                return
            }

            if (sintilde.lowercase(Locale.getDefault()).contains("terminar configuracion") ||
                sintilde.lowercase(Locale.getDefault()).contains("terminar configuracion de perfil") ||
                sintilde.lowercase(Locale.getDefault()).contains("salir de configuracion")){
                dataProfile.checkProfileComplete()
                responder("Configuración de perfil terminada.")
                return
            }

            responder("En este momento estas en un proceso activo de configuración de perfil. Para que la aplicación " +
                    "vuelva a su funcionamiento normal debes completar este proceso o indicar el comando, terminar configuración.")
            return
        }

        if (!inConfigProfile && !inLogin && user != null ){
            if (sintilde.lowercase(Locale.getDefault()).contains("cerrar sesion")){
                logout()
                responder("Se cerró la sesión.")
                return
            }

            if (sintilde.lowercase(Locale.getDefault()).contains("eliminar cuenta") && phoneNumber != null){
                if (isNetworkAvailable(this)) {  // Verificar conexión a Internet
                    inConfigProfile = true
                    responder("Procesando...")
                    dataProfile.deleteUser(phoneNumber.toString())
                } else {
                    responder("Eliminar tu cuenta requiere conexión a internet. Comprueba tu conexión e inténtalo de nuevo.")
                }
                return
            }

            if (sintilde.lowercase(Locale.getDefault()).contains("hora")) {
                val date = Date()
                val h = SimpleDateFormat("h:mm a", Locale.getDefault())
                val ahora = h.format(date)

                // Dividir la hora completa en horas, minutos y período (a. m. o p. m.)
                val partes = ahora.split(":")
                val partes2 = partes[1].split(" ")
                val hora = partes[0] // Contiene la hora
                val minutos = partes2[0] // Contiene los minutos
                val periodo = partes2[1] // Contiene el período (a. m. o p. m.)

                responder("son las $hora y $minutos $periodo")
                return
            }

            if (sintilde.lowercase(Locale.getDefault()).contains("fecha")) {
                val date = Date() // Obtiene la fecha actual

                // Define el formato de fecha deseado
                val formatoFecha = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                val fechaActual = formatoFecha.format(date)

                responder("La fecha de hoy es: $fechaActual.")
                return
            }

            if (sintilde.lowercase().contains("ubicacion") ||
                sintilde.lowercase().contains("direccion") ||
                sintilde.lowercase().contains("lugar estoy") ||
                sintilde.lowercase().contains("donde estoy")) {
                geocodingService.showCurrentLocation()
                responder(geocodingService.direccion)
                return
            }

            if (sintilde.lowercase().contains("indicaciones")) {
                responder(geocodingService.direccionPlus)
                return
            }
        }

        if (sintilde.lowercase(Locale.getDefault()).contains("salir") ||
            sintilde.lowercase(Locale.getDefault()).contains("cerrar app") ||
            sintilde.lowercase(Locale.getDefault()).contains("cerrar aplicacion")) {
            if (isTtsInitialized) {
                textToSpeech?.speak("Bye", TextToSpeech.QUEUE_FLUSH, null, null)
            }
            closeApp()
        }

        if (sintilde.lowercase(Locale.getDefault()).contains("comentario") ||
            sintilde.lowercase(Locale.getDefault()).contains("comentar")){
            responder("Procesando...")
            if (isNetworkAvailable(this)) {  // Verificar conexión a Internet
                dataProfile.upComment(escuchado.toString(), user)
            } else {
                responder("Comprueba tu conexión a internet y luego intentalo de nuevo.")
            }
            return
        }

        if (sintilde.lowercase(Locale.getDefault()).contains("repetirme") ||
            sintilde.lowercase(Locale.getDefault()).contains("repite") ||
            sintilde.lowercase(Locale.getDefault()).contains("repitas") ||
            sintilde.lowercase(Locale.getDefault()).contains("repeticion")
        ) {
            responder("Por supuesto, lo que dije fue: " + respActual)
            return
        }

        if (sintilde.lowercase(Locale.getDefault()).contains("anterior") ||
            sintilde.lowercase(Locale.getDefault()).contains("antes") ||
            sintilde.lowercase(Locale.getDefault()).contains("respuesta anterior")
        ) {
            responder("Anteriormente dije: " + respAnterior)
            return
        }

        if (inLogin){
            responder("En este momento estas en un proceso activo de inicio de sesión. Debes completar este proceso para " +
                    "que la aplicación vuelva a su funcionamiento normal.")
            return
        }

        responder(respuesta)
    }

    fun responder(respuestita: String) {
        accionEscucha?.text = respuestita
        enEscucha = false
        respAnterior = respActual
        respActual = respuestita
    }

    fun proveerDatos(): ArrayList<Respuestas> {
        val respuestas = ArrayList<Respuestas>()
        respuestas.add(Respuestas("defecto", "Perdón, eso no lo reconozco o al parecer no escuche bien."))
        respuestas.add(Respuestas("hola", "Hola, ¿qué tal te va?."))
        respuestas.add(Respuestas("bien", "Me alegra escuchar eso. Ten un lindo día."))
        respuestas.add(Respuestas("chiste", "¿Cómo se despiden los químicos?... Ácido un placer."))
        respuestas.add(Respuestas("chistes", "¿Cómo se llama el primo vegano de Brúce Li?... Brocó Li."))
        respuestas.add(Respuestas("chistosa", "Escucha esto. ¿Dónde caga Batman?... ¿Se te ocurre algo?... ¿Nada?... En el Bat-er."))
        respuestas.add(Respuestas("eres", "Soy una inteligencia artificial diseñada para auxiliar la movilidad de personas con discapacidad visual, esto para mejorar su independencia y su calidad de vida."))
        respuestas.add(Respuestas("llamas", "Me llaman podev, pero puedes llamarme como quieras."))
        respuestas.add(Respuestas("tu nombre", "Me llaman podev, pero puedes llamarme como quieras."))
        respuestas.add(Respuestas("como estas", "Soy una inteligencia artificial, así que no tengo emociones. Pero estoy aquí para ayudarte en lo que necesites."))
        respuestas.add(Respuestas("otro idioma", "Por ahora solo hablo español."))
        respuestas.add(Respuestas("Como funciona una inteligencia artificial", "Una inteligencia artificial utiliza algoritmos y datos para realizar tareas y tomar decisiones de manera autónoma."))
        respuestas.add(Respuestas("riesgos puede presentar la inteligencia artificial", "La inteligencia artificial puede presentar riesgos en cuanto a la seguridad y privacidad de los datos, la discriminación algorítmica y el impacto en el mercado laboral, entre otros. Es importante considerar estos riesgos y tomar medidas para mitigarlos."))
        respuestas.add(Respuestas("te creo", "Fui creada por MARLEÍSHÓN y todo el equipo de; podev app."))
        respuestas.add(Respuestas("te hizo", "Fui creada por MARLEÍSHÓN y todo el equipo de; podev app."))
        respuestas.add(Respuestas("te construyo", "Fui creada por MARLEÍSHÓN y todo el equipo de; podev app."))
        respuestas.add(Respuestas("te invento", "Fui creada por MARLEÍSHÓN y todo el equipo de; podev app."))
        respuestas.add(Respuestas("inventaron", "Fui creada por MARLEÍSHÓN y todo el equipo de; podev app."))
        respuestas.add(Respuestas("construyeron", "Fui creada por MARLEÍSHÓN y todo el equipo de; podev app."))
        respuestas.add(Respuestas("hicieron", "Fui creada por MARLEÍSHÓN y todo el equipo de; podev app."))
        respuestas.add(Respuestas("crearon", "Fui creada por MARLEÍSHÓN y todo el equipo de; podev app."))
        respuestas.add(Respuestas("puedes ayudarme", "Puedo ayudarte en tu movilidad en entornos urbanos, especialmente. Puedo reconocer posibles obstáculos o facilitadores para tu desplazamiento."))
        respuestas.add(Respuestas("comandos", "A continuación voy a listar los comandos disponibles para acceder a " +
                "los tutoriales y las diferentes funciones de la aplicación. La palabra clave, introducción, te dará información general " +
                "respecto al propósito y objetivo de la aplicación. La palabra clave, tutorial, te dará información general del funcionamiento " +
                "y uso de la aplicación. Las palabras claves, funciones adicionales de registro, ampliarán la información respecto a funciones " +
                "adicionales que solo estarán disponibles si te registras con un número de celular. La palabra clave, repetir o repíteme, te " +
                "retornará nuevamente la última respuesta de la asistencia. La palabra clave, anterior, te retornará la penúltima respuesta de " +
                "la asistencia. Las palabras claves, subir comentario, te permitirán enviar comentarios a los desarrolladores. Di el comando y tu comentario en una sola frase. " +
                "Las palabras claves, iniciar sesión, " +
                "activarán un proceso de inicio de sesión y brindarán instrucciones de como hacerlo, las demás funciones de la app se " +
                "suspenderán temporalmente hasta dar por terminado este proceso. Las palabras claves, mi celular es, y junto a estas tu número " +
                "de celular te permitirán iniciar sesión con tu número de celular. Debes decir el comando y tu número de celular en una sola frase. " +
                "La palabra clave, invitado, te permitirá iniciar sesión como invitado. Las palabras claves, datos requeridos para mi perfil, te " +
                "mencionará los datos necesarios para completar tu perfil. Las palabras claves, configurar perfil, activarán un proceso de " +
                "configuración en el que podrás editar los datos registrados en tu perfil. Las palabras claves, información de mi perfil, te " +
                "retornará los datos que están registrados en tu perfil. Las palabras claves, terminar configuración, permitirán " +
                "finalizar el proceso de configuración de perfil y el funcionamiento de la aplicación volverá a la normalidad. La palabra clave, " +
                "hora, te dará información puntual respecto a la hora. " +
                "La palabra clave, fecha, te brindará información puntual de la fecha. La palabra clave, ubicación, brindará información sobre " +
                "la dirección en la que te encuentras. La palabra clave, indicaciones, brindará más posibles direcciones relacionadas con tu " +
                "ubicación. Las palabras claves, activar Podev web, permitirán activar las funciones adicionales de Podev, entre las más " +
                "fundamentales, permitir que tus familiares accedan a tu ubicación mientras usas la aplicación. Las palabras claves, desactivar " +
                "Podev web, permitirán desactivar las funciones adicionales de Podev, es decir, que tus familiares ya no podrán saber tu " +
                "ubicación. Las palabras claves, cerrar sesión, permitirán cerrar la sesión actual, esto hará que la aplicación entre en un " +
                "estado nulo y las funciones se suspendan hasta volver a iniciar sesión. Las palabras claves, cerrar aplicación, permitirán " +
                "salir de la aplicación automáticamente."))
        respuestas.add(Respuestas("inicio", "Hola, Soy Podev, tu asistente. Si eres un usuario nuevo, puedo darte una introducción de lo que puedo hacer. Si ya tienes una cuenta, inicia sesión y comencemos."))
        respuestas.add(Respuestas("user", "Hola, Soy Podev, tu asistente. ¿Por favor déjame saber, Como puedo ayudarte?."))
        respuestas.add(Respuestas("introduccion", "Bienvenido, La función principal de Podev " +
                "es asistir al usuario con discapacidad visual en su movilidad en entornos urbanos, ayudándole a identificar obstáculos " +
                "o facilitadores de movilidad. La inteligencia artificial de este aplicativo actualmente está entrenada para identificar sendas podo táctiles, " +
                "semáforos peatonales en verde o en rojo, cebras peatonales, señales de pare, señales de cruce peatonal, paradas de bus y buses del transporte " +
                "público de San Juan de Pasto. El aplicativo Podev cuenta con una sencilla interacción por voz que facilita su uso. La asistencia por voz resuelve " +
                "cualquier función o configuración que incluya el aplicativo. Esta se activa con el botón inferior de recepción de orden. Cuando presionas el botón, " +
                "la aplicación escucha y responde. Es el único botón en el aplicativo, por lo que facilita su uso con lectores de pantalla como Talkback. " +
                "La asistencia puede suministrar información de relevancia, como lo es: dirección en la que se ubica e indicaciones adicionales y datos horarios. " +
                "Para comenzar, es necesario iniciar sesión, esto con el fin de recepcionar y controlar los posibles errores y eventos de la aplicación. " +
                "Si no tienes una cuenta, puedes registrarte con tu número de celular o ingresar como invitado. Ten en cuenta que si te registras con tu número de " +
                "celular, podrás acceder a funciones adicionales. Si deseas saber más, indica, información sobre funciones adicionales de registro." +
                "Si deseas un tutorial de uso del aplicativo, indica, tutorial. Para salir solo tienes que indicarlo diciendo, cerrar aplicacion."))
        respuestas.add(Respuestas("funciones adicionales de registro", "Cuando te registras con tu número de celular y brindas " +
                "información adicional, podemos identificarte como usuario y brindarte funciones especiales. Entre las funciones adicionales más relevantes " +
                "está el acceso a la web Podev para familiares y amigos. Sabemos que para las personas cercanas a ti eres muy importante y tenemos en cuenta la preocupación " +
                "de ellos cuando te enfrentas a entornos urbanos de manera independiente. Así que brindamos información de tu ubicación en tiempo real para tranquilizar " +
                "a las personas que te quieren. Este es un proceso sencillo, tus familiares solo tendrán que ingresar tu número de celular en la web de Podev para poder " +
                "consultar tu información y tu ubicación. Esta función la puedes desactivar o activar cuando lo desees. Solo tienes que indicarlo con las palabras claves. " +
                "Activar Podev web, o, desactivar Podev web. Por defecto, esta función está activada en un inicio hasta que la desactives."))
        respuestas.add(Respuestas("tutorial", "La asistencia de voz de Podev funciona correctamente cuando das instrucciones específicas y sigues " +
                "las recomendaciones de la misma asistencia. Apenas inicies sesión, la inteligencia artificial comienza a recibir imágenes de la cámara trasera de tu dispositivo " +
                "móvil y empezará a identificar los obstáculos o facilitadores de movilidad en los que está entrenada. La inteligencia artificial y otras funciones importantes solo " +
                "responderán si inicias sesión. Para iniciar sesión recuerda que puedes hacerlo con tu número de celular o también puedes hacerlo como invitado. Para iniciar sesión " +
                "con tu número de celular solo indica, mi celular es, y luego especificas tu número de celular. Di el comando y tu número de celular en una sola frase. Para iniciar " +
                "sesión como invitado solo indica, iniciar sesión como invitado. Si deseas solicitar información sobre la dirección " +
                "en la que te ubicas, solo indica, información sobre mi ubicación. Si deseas saber la hora o la fecha pregunta a la asistencia con instrucciones específicas " +
                "como: ¿que hora es?. Respecto a la información de tu usuario, será tan sencillo como solicitar. Información de perfil. Si deseas cerrar sesión indica, cerrar sesión. " +
                "Si deseas hacer un comentario, recomendación o reportar algún error de la aplicación, comienza indicando, subir comentario y seguido lo que deseas registrar. " +
                "Un ejemplo sería: subir comentario, mi comentario es que me siento muy bien con la aplicación. Di el comando y tu comentario en una sola frase. Esto se enviará a nuestro servidor y empezaremos a trabajar " +
                "para mejorar. Si deseas que te repita la última respuesta que te di solo solicita, repíteme. Si deseas escuchar nuevamente la penúltima respuesta que te di solo " +
                "debes solicitar, dame la anterior respuesta. Si deseas salir de la aplicación, solo debes indicar, cerrar aplicación. Por último, si deseas un listado completo de " +
                "los comandos de la aplicación, debes solicitarlo con la palabra clave, comandos."))
        respuestas.add(Respuestas("iniciar sesion", "Para iniciar sesión lo puedes hacer con tu número de celular. Solo debes decir, " +
                "mi celular es, y seguido dictas tu número de celular. Di el comando y tu número de celular en una sola frase. Recuerda que si inicias sesión con tu número de celular podremos " +
                "brindarte funciones adicionales. También puedes iniciar sesión como invitado pero este método no " +
                "cuenta con funciones adicionales. Para iniciar sesión como invitado solo indica: ingresar como invitado."))

                return respuestas
    }

    // Verificar la conexión a Internet
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                else -> false
            }
        } else {
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            return networkInfo.isConnected
        }
    }

    // App cicle
    fun stopTextToSpeech() {
        if (isTtsInitialized) {
            textToSpeech!!.stop()
        }
    }

    private fun stopCamera() {
        if (::cameraProvider.isInitialized) {
            cameraProvider.unbindAll()
        }
    }

    private fun closeApp() {
        stopCamera()
        finishAffinity()
    }

    override fun onInit(p0: Int) {}

    override fun onPause() {
        super.onPause()
        stopTextToSpeech()
    }

    override fun onStop() {
        super.onStop()
        stopTextToSpeech()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
        cameraExecutor.shutdown()
        stopListeningForSms()
        stopAuthStateListener()
        geocodingService.stopUpdatingLocation()
        stopTextToSpeech()
    }
}