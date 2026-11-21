package com.smartumbrella.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.smartumbrella.app.data.model.LocationCoordinate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class LocationHelper(private val context: Context) {

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Result<LocationCoordinate> = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            return@withContext Result.failure(
                SecurityException("Location permission not granted. Please grant permission or enter location manually.")
            )
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext Result.failure(IllegalStateException("Location service unavailable."))

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        var bestLocation: Location? = null
        for (provider in providers) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null && (bestLocation == null || loc.accuracy < bestLocation.accuracy)) {
                        bestLocation = loc
                    }
                }
            } catch (_: Exception) {}
        }

        val loc = bestLocation ?: return@withContext Result.failure(
            IllegalStateException("Unable to retrieve device GPS coordinates. Please ensure Location is enabled or enter your city manually.")
        )

        val lat = loc.latitude
        val lon = loc.longitude
        var placeName = "Current Location"
        var admin1: String? = null
        var country: String? = null

        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    placeName = addr.locality ?: addr.subAdminArea ?: addr.featureName ?: "Current Location"
                    admin1 = addr.adminArea
                    country = addr.countryName
                }
            }
        } catch (_: Exception) {
            // Geocoder failure is non-fatal; we retain coordinates
        }

        Result.success(
            LocationCoordinate(
                name = placeName,
                latitude = lat,
                longitude = lon,
                admin1 = admin1,
                country = country
            )
        )
    }
}
