package com.mjdevp.podevapp

import android.util.Log
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataProfile(private val activity: MainActivity) {

    private val db = FirebaseFirestore.getInstance()
    var phoneNumber: String? = null
    var email: String? = null
    var nombre: String? = null
    var apellido: String? = null
    var nombreContacto: String? = null
    var numeroContacto: String? = null
    var PodevWeb: String? = null

    fun checkProfileComplete() {
        phoneNumber?.let { phone ->
            // Consulta Firestore para verificar si existe un registro con el número de teléfono del usuario
            db.collection("users").document(phone).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val missingFields = mutableListOf<String>()

                        if (document.contains("email") && document.contains("nombre") && document.contains("apellido") && document.contains("nombreContacto") && document.contains("numeroContacto")) {
                            activity.inConfigProfile = false
                            activity.completedProfile = true
                            phoneNumber = phone
                            activity.preparaRespuesta("user")
                        } else {
                            activity.newUserProfile()
                        }

                        // Verificar y agregar campos faltantes a la lista
                        if (!document.contains("email")) {
                            missingFields.add("correo electrónico")
                        } else {
                            email = document.getString("email")
                        }

                        if (!document.contains("nombre")) {
                            missingFields.add("nombre")
                        } else {
                            nombre = document.getString("nombre")
                        }

                        if (!document.contains("apellido")) {
                            missingFields.add("apellido")
                        } else {
                            apellido = document.getString("apellido")
                        }

                        if (!document.contains("nombreContacto")) {
                            missingFields.add("nombre de contacto de emergencia")
                        } else {
                            nombreContacto = document.getString("nombreContacto")
                        }

                        if (!document.contains("numeroContacto")) {
                            missingFields.add("número de celular de contacto de emergencia")
                        } else {
                            numeroContacto = document.getString("numeroContacto")
                        }

                        if (!document.contains("PodevWeb")) {
                            missingFields.add("Activar o desactivar Podev Web")
                        } else {
                            if(document.getBoolean("PodevWeb") == true){
                                PodevWeb = "Activado"
                            }else{
                                PodevWeb = "Desactivado"
                            }
                        }

                        // Imprimir todos los campos faltantes
                        if (missingFields.isNotEmpty()) {
                            activity.responder("Faltan los siguientes datos en tu perfil: ${missingFields.joinToString(", ")}. Completa tu perfil para continuar. ")
                        }
                    } else {
                        Log.d("DataProfile", "No se encontró ningún perfil con el número de teléfono $phone. Completa tu perfil para continuar.")
                        activity.newUserProfile()
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("DataProfile", "Error al verificar el perfil: ", exception)
                }
        }
    }

    // Informacion en base de datos
    fun upEmail(newEmail: String){
        db.collection("users").document(phoneNumber!!).set(
            mapOf(
                "email" to newEmail
            ), SetOptions.merge()
        ).addOnSuccessListener { Log.d("DataProfile", "¡DocumentSnapshot email escrito con éxito!") }
            .addOnFailureListener { e -> Log.w("DataProfile", "Error al redactar el documento email", e) }
    }

    fun upNombre(newNombre: String){
        phoneNumber?.let {
            db.collection("users").document(phoneNumber!!).set(
                mapOf(
                    "nombre" to newNombre
                ), SetOptions.merge()
            ).addOnSuccessListener { Log.d("DataProfile", "¡DocumentSnapshot nombre escrito con éxito!") }
                .addOnFailureListener { e -> Log.w("DataProfile", "Error al redactar el documento nombre", e) }
        } ?: run {
            Log.e("DataProfile", "Error: phoneNumber es null")
        }
    }

    fun upApellido(newApellidos: String){
        db.collection("users").document(phoneNumber!!).set(
            mapOf(
                "apellido" to newApellidos
            ), SetOptions.merge()
        ).addOnSuccessListener { Log.d("DataProfile", "¡DocumentSnapshot apellido escrito con éxito!") }
            .addOnFailureListener { e -> Log.w("DataProfile", "Error al redactar el documento apellido", e) }
    }

    fun upNombreContacto(newNombreContacto: String){
        db.collection("users").document(phoneNumber!!).set(
            mapOf(
                "nombreContacto" to newNombreContacto
            ), SetOptions.merge()
        ).addOnSuccessListener { Log.d("DataProfile", "¡DocumentSnapshot nombreContacto escrito con éxito!") }
            .addOnFailureListener { e -> Log.w("DataProfile", "Error al redactar el documento nombreContacto", e) }
    }

    fun upNumeroContacto(newNumeroContacto: String){
        db.collection("users").document(phoneNumber!!).set(
            mapOf(
                "numeroContacto" to newNumeroContacto
            ), SetOptions.merge()
        ).addOnSuccessListener { Log.d("DataProfile", "¡DocumentSnapshot numeroContacto escrito con éxito!") }
            .addOnFailureListener { e -> Log.w("DataProfile", "Error al redactar el documento numeroContacto", e) }
    }

    fun upLatitud(latitud: String){
        phoneNumber?.let {
            db.collection("users").document(it).set(
                mapOf(
                    "latitud" to latitud
                ), SetOptions.merge()
            ).addOnSuccessListener { Log.d("DataProfile", "¡DocumentSnapshot latitud escrito con éxito!") }
                .addOnFailureListener { e -> Log.w("DataProfile", "Error al redactar el documento latitud", e) }
        } ?: run {
            Log.e("DataProfile", "Error: phoneNumber es null")
        }
    }

    fun upLongitud(longitud: String){
        phoneNumber?.let {
            db.collection("users").document(phoneNumber!!).set(
                mapOf(
                    "longitud" to longitud
                ), SetOptions.merge()
            ).addOnSuccessListener { Log.d("DataProfile", "¡DocumentSnapshot longitud escrito con éxito!") }
                .addOnFailureListener { e -> Log.w("DataProfile", "Error al redactar el documento longitud", e) }
        } ?: run {
            Log.e("DataProfile", "Error: phoneNumber es null")
        }
    }

    fun upPodevWeb(podevWeb: Boolean){
        db.collection("users").document(phoneNumber!!).set(
            mapOf(
                "PodevWeb" to podevWeb
            ), SetOptions.merge()
        ).addOnSuccessListener { Log.d("DataProfile", "¡DocumentSnapshot PodevWeb escrito con éxito!") }
            .addOnFailureListener { e -> Log.w("DataProfile", "Error al redactar el documento PodevWeb", e) }
    }

    fun upTokenNotification(token: String){
        db.collection("users").document(phoneNumber!!).set(
            mapOf(
                "tokenNotification" to token
            ), SetOptions.merge()
        ).addOnSuccessListener { Log.d("DataProfile", "¡DocumentSnapshot tokenNotification escrito con éxito!") }
            .addOnFailureListener { e -> Log.w("DataProfile", "Error al redactar el documento tokenNotification", e) }
    }

    // Nuevo método para subir comentarios
    fun upComment(comentario: String, user: FirebaseUser?) {
        val currentUser = user
        val currentTime = System.currentTimeMillis()

        // Formatear la marca de tiempo en un formato legible
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(currentTime))

        // Crear un ID aleatorio para el comentario
        val comentarioId = db.collection("comments").document().id

        // Crear el mapa de datos del comentario
        val comentarioData = hashMapOf(
            "comentario" to comentario,
            "timestamp" to formattedDate // Guardar la fecha y hora formateada
        )

        // Añadir el UID o el número de teléfono del usuario si está disponible
        if (currentUser?.phoneNumber != null || currentUser?.phoneNumber == "") {
            comentarioData["uid"] = currentUser.uid
            comentarioData["phoneNumber"] = currentUser.phoneNumber
        } else if (currentUser?.uid != null) {
            comentarioData["uid"] = currentUser.uid
        }

        // Guardar el comentario en la colección de comentarios
        db.collection("comments").document(comentarioId).set(comentarioData)
            .addOnSuccessListener {
                activity.responder("Comentario subido con éxito.")
            }
            .addOnFailureListener { e ->
                activity.responder("Error al subir el comentario. Por favor, inténtalo de nuevo.")
            }
    }

    fun getDataProfile() {
        phoneNumber?.let { phone ->
            db.collection("users").document(phone).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        email = document.getString("email")
                        nombre = document.getString("nombre")
                        apellido = document.getString("apellido")
                        nombreContacto = document.getString("nombreContacto")
                        numeroContacto = document.getString("numeroContacto")
                        PodevWeb = if (document.getBoolean("PodevWeb") == true) "Activado" else "Desactivado"
                    } else {
                        //Log.d("DataProfile", "No se encontró ningún perfil con el número de teléfono $phone.")
                        activity.responder("No se encontró tu información de perfil. Vuelve a consultar tu información cuando tengas conexión a internet.")
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("DataProfile", "Error al obtener el perfil: ", exception)
                }
        } ?: run {
            Log.e("DataProfile", "Error: phoneNumber es null")
        }
    }

    fun deleteDataProfile() {
        email = "vacío"
        nombre = "vacío"
        apellido = "vacío"
        nombreContacto = "vacío"
        numeroContacto = "vacío"
        PodevWeb = "vacío"
        phoneNumber = null
        activity.phoneNumber = null
    }

    fun deleteUser(phoneNumber: String) {
        db.collection("users").document(phoneNumber).delete()
            .addOnSuccessListener {
                activity.logout()
                activity.user = null
                activity.responder("Usuario eliminado con éxito.")
            }
            .addOnFailureListener { e ->
                Log.w("DataProfile", "Error al eliminar el usuario", e)
                activity.responder("Error al eliminar el usuario. Por favor, inténtalo de nuevo.")
            }
    }
}