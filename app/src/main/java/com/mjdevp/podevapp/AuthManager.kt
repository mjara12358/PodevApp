package com.mjdevp.podevapp

import android.os.Bundle
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthMissingActivityForRecaptchaException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

sealed class AuthRes<out T>{
    data class Success<T>(val data: T): AuthRes<T>()
    data class Error(val errorMessage: String): AuthRes<Nothing>()
}

class AuthManager(private val activity: MainActivity, private val geocodingService: GeocodingService, private val analytics: AnalyticsManager, private val dataProfile: DataProfile) {

    private val auth: FirebaseAuth by lazy { Firebase.auth}
    var storedVerificationId:String = ""

    // Login anonimo
    suspend fun singInAnonymously(): AuthRes<FirebaseUser>{
        return  try {
            val result = auth.signInAnonymously().await()
            activity.changeAvatarImage(R.drawable.imglogo)
            AuthRes.Success(result.user ?: throw Exception("Error al iniciar sesión"))
        }catch (e: Exception){
            AuthRes.Error(e.message ?: "Error al iniciar sesión. Por favor, intenta de nuevo.")
        }
    }

    // Login con telefono
    fun singWhitNumPhone(codigoVerificacion: String) {
        val credential = PhoneAuthProvider.getCredential(storedVerificationId, codigoVerificacion)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    activity.responder("Se inició sesión con el número de celular correctamente.")
                    activity.stopListeningForSms()
                    activity.inLogin = false
                    activity.changeAvatarImage(R.drawable.imglogo)
                    geocodingService.showCurrentLocation()
                    geocodingService.startUpdatingLocation()
                    activity.lifecycleScope.launch {
                        delay(5000)
                        dataProfile.checkProfileComplete()
                        dataProfile.getDataProfile()
                    }
                    val params = Bundle().apply {
                        putString("Ingreso", "Telefono Celular")
                    }
                    analytics.logEvent("LoginCel", params)
                } else {
                    Log.e("AuthManager",task.toString())
                    activity.responder("Error al iniciar sesión. Por favor, intenta de nuevo.")
                }
            }
    }

    fun sendSMS(phoneNumber: String?){
        if (phoneNumber != null) {
            dataProfile.phoneNumber = phoneNumber
            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber) // Phone number to verify
                .setTimeout(120L, TimeUnit.SECONDS) // Timeout and unit
                .setActivity(activity) // Activity (for callback binding)
                .setCallbacks(callbacks) // OnVerificationStateChangedCallbacks
                .build()
            auth.setLanguageCode("es")
            PhoneAuthProvider.verifyPhoneNumber(options)
        } else {
            activity.responder("Puede que el numero no se halla escuchado bien.") // Respond if no number is found
        }
    }

    // cerrar sesion
    fun singOut(){
        auth.signOut()
        geocodingService.stopUpdatingLocation()
        dataProfile.deleteDataProfile()
    }

    fun getCurrentUser(): FirebaseUser?{
        return auth.currentUser
    }

    fun addAuthStateListener(listener: FirebaseAuth.AuthStateListener) {
        auth.addAuthStateListener(listener)
    }

    fun removeAuthStateListener(listener: FirebaseAuth.AuthStateListener) {
        auth.removeAuthStateListener(listener)
    }

    var callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        // Override the required methods here
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // Called when verification is completed successfully
            //Iniciamos secion
            //SingWhitNumPhone(credential)
            Log.d("AuthManager", "Verification completed: $credential")
        }

        override fun onVerificationFailed(e: FirebaseException) {
            // Called when verification has failed
            activity.responder(e.toString())
            activity.responder("La verificación falló. Por favor, intenta de nuevo.")
            Log.e("AuthManager", "Verification failed", e)
            if (e is FirebaseAuthInvalidCredentialsException) {
                // Invalid request
                Log.e("AuthManager", "El numero es incorrecto.")
                activity.responder("El número es incorrecto. Por favor, intenta de nuevo.")
            } else if (e is FirebaseTooManyRequestsException) {
                // The SMS quota for the project has been exceeded
                Log.e("AuthManager", "Quota exceeded.")
                activity.responder("El servicio no está disponible ahora. Por favor, intenta de nuevo más tarde.")
            } else if (e is FirebaseAuthMissingActivityForRecaptchaException) {
                Log.e("AuthManager", "Error reCAPTCHA.")
                activity.responder("Hubo un error con la verificación de reCAPTCHA. Por favor, intenta de nuevo.")

            }
            // Inform the user of the error
            //Toast.makeText(this@AuthManager.activity, "Verification failed: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("AuthManager", "Verification failed: ${e.message}")
        }

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            // Called when the code has been sent to the user
            super.onCodeSent(verificationId, token)
            activity.responder("El codigo de verificacion fue enviado. Podev App debería escuchar y verificar el código automáticamente. Puede que la verificación " +
                    "abra automáticamente una pestaña de Google para la confirmación reCAPTCHA, además te llegara un mensaje de texto que talvez desvíe el foco del lector de " +
                    "pantalla Talkback. Solo espera alrededor de un minuto y toca la pantalla para leer la respuesta de Podev App.")
            activity.lifecycleScope.launch {
                delay(60000)
                if(activity.inLogin){
                    activity.responder("Si pasan más de dos min sin que la aplicación no actualice automáticamente el estado de inicio de sesión, tendrás que " +
                            "dirigirte a tus mensajes de texto y buscar el código de verificación que te enviamos. Luego vuelve a la aplicación e indica " +
                            "a la asistencia. Mi código es, y luego dictas el código que recibiste. El comando y el código en una misma frase.")
                }
            }
            activity.startListeningForSms()
            storedVerificationId = verificationId
        }
    }
}