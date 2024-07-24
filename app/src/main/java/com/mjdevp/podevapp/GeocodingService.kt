package com.mjdevp.podevapp

import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.*

class GeocodingService(private val context: Context, private val dataProfile: DataProfile, private val fusedLocationProviderClient: FusedLocationProviderClient, private val lifecycleOwner: LifecycleOwner) {

    var txtLatitud: String? = null
    var txtLongitud: String? = null
    var direccion: String = "Aún no hay información de la ubicación. Inténtalo de nuevo en un momento."
    var direccionPlus: String = "Sin información adicional de la ubicación. Inténtalo de nuevo en un momento."

    private var updatingLocation = true

    // Geocodificacion
    fun convertCoordinatesToAddress() {
        val latitudString = txtLatitud
        val longitudString = txtLongitud

        if (!latitudString.isNullOrEmpty() && !longitudString.isNullOrEmpty()) {
            val latitude = latitudString.toDouble()
            val longitude = longitudString.toDouble()

            val geocoder = Geocoder(context, Locale.getDefault())
            try {
                val addresses: List<Address> = geocoder.getFromLocation(latitude, longitude, 5)!!
                if (addresses.isNotEmpty()) {
                    val address = addresses[0]
                    var addressText = address.getAddressLine(0)
                    addressText = addressText.replace("Cra.", "Carrera").replace("Cl.", "Calle").replace("Cl", "Calle")

                    val address2 = addresses[1]
                    var addressText2 = address2.getAddressLine(0)
                    addressText2 = addressText2.replace("Cra.", "Carrera").replace("Cl.", "Calle").replace("Cl", "Calle")

                    val address3 = addresses[2]
                    var addressText3 = address3.getAddressLine(0)
                    addressText3 = addressText3.replace("Cra.", "Carrera").replace("Cl.", "Calle").replace("Cl", "Calle")

                    direccion = "Dirección aproximada: $addressText. Si no es clara la dirección puedes solicitar más indicaciones."
                    direccionPlus = "Mas información de localización: $addressText2 o $addressText3"
                } else {
                    //Toast.makeText(context, "No se encontró ninguna dirección para las coordenadas proporcionadas.", Toast.LENGTH_SHORT).show()
                    direccion = "No se encontró ninguna dirección para las coordenadas proporcionadas."
                }
            } catch (e: IOException) {
                e.printStackTrace()
                //Toast.makeText(context, "Error al convertir coordenadas a dirección.", Toast.LENGTH_SHORT).show()
                direccion = "Error al convertir coordenadas a dirección. Puede que la conexión a internet esté fallando. Comprueba tu conexión e inténtalo de nuevo."
            }
        } else {
            //Toast.makeText(context, "Error al obtener latitud y longitud. Por favor, inténtalo de nuevo.", Toast.LENGTH_SHORT).show()
            direccion = "Error al obtener latitud y longitud. Por favor, inténtalo de nuevo."
        }
    }

    fun showCurrentLocation() {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    txtLatitud = currentLatLng.latitude.toString()
                    txtLongitud = currentLatLng.longitude.toString()
                    convertCoordinatesToAddress()
                } else {
                    //Toast.makeText(context, "No se puede obtener la ubicación actual.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun updateLocation() {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    txtLatitud = currentLatLng.latitude.toString()
                    dataProfile.upLatitud(txtLatitud!!)
                    txtLongitud = currentLatLng.longitude.toString()
                    dataProfile.upLongitud(txtLongitud!!)
                } else {
                    //Toast.makeText(context, "No se puede obtener la ubicación actual.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun startUpdatingLocation() {
        updatingLocation = true
        lifecycleOwner.lifecycleScope.launch {
            while (updatingLocation) {
                updateLocation()
                delay(300000)
            }
        }
    }

    fun stopUpdatingLocation() {
        updatingLocation = false
    }
}

