package com.upm.gabrau.walkmate.activities

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.animation.BounceInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdate
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.LocationComponentOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.*
import com.upm.gabrau.walkmate.R
import com.upm.gabrau.walkmate.databinding.ActivityNewPostBinding


class NewPostActivity : AppCompatActivity(), ActivityCompat.OnRequestPermissionsResultCallback {
    private lateinit var binding: ActivityNewPostBinding
    private val isCurrentLocationRequested: MutableLiveData<Boolean> = MutableLiveData()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))

        binding = ActivityNewPostBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        setSupportActionBar(binding.toolbar.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.root.setNavigationOnClickListener {
            onBackPressed()
        }

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync { mapboxMap ->
            lateinit var circleManager: CircleManager
            val locationComponent = mapboxMap.locationComponent
            val locationComponentOptions = LocationComponentOptions.builder(this)
                .pulseEnabled(true)
                .build()

            mapboxMap.setStyle(Style.MAPBOX_STREETS) { style ->
                val locationComponentActivationOptions = LocationComponentActivationOptions
                    .builder(this, style)
                    .locationComponentOptions(locationComponentOptions)
                    .build()

                locationComponent.activateLocationComponent(locationComponentActivationOptions)
                locationComponent.isLocationComponentEnabled = false

                circleManager = CircleManager(binding.mapView, mapboxMap, style)

                isCurrentLocationRequested.observe(this, { isUserLocationEnabled ->
                    if (isUserLocationEnabled) {
                        locationComponent.cameraMode = CameraMode.TRACKING
                        locationComponent.renderMode = RenderMode.NORMAL
                        locationComponent.isLocationComponentEnabled = true

                        locationComponent.zoomWhileTracking(15.0, 2000L)
                    } else {
                        locationComponent.isLocationComponentEnabled = false
                    }
                })
            }

            mapboxMap.addOnMapClickListener { point ->
                isCurrentLocationRequested.value = false
                val cameraPosition = CameraPosition.Builder()
                    .target(point)
                    .build()

                val circleOptions = CircleOptions()
                    .withLatLng(point)

                circleManager.create(circleOptions)

                mapboxMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 2000)

                true
            }
        }

        binding.fabLocation.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                ) {
                    Log.d("TAG", "onCreate: ni se cuando salta esto")
                    val permissions = Array(1) { Manifest.permission.ACCESS_FINE_LOCATION }
                    ActivityCompat.requestPermissions(this, permissions, 1)
                } else {
                    Log.d("TAG", "onCreate: ni esto tampoco")
                    val permissions = Array(1) { Manifest.permission.ACCESS_FINE_LOCATION }
                    ActivityCompat.requestPermissions(this, permissions, 1)
                }
            } else {
                isCurrentLocationRequested.value?.let {
                    isCurrentLocationRequested.value = isCurrentLocationRequested.value!!.not()
                } ?: run {
                    isCurrentLocationRequested.value = true
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
                        isCurrentLocationRequested.value = true
                    }
                } else {
                    val toast =
                        Toast.makeText(
                            applicationContext,
                            "Grant location permission manually in settings",
                            Toast.LENGTH_LONG
                        )
                    toast.show()
                }
                return
            }
        }
    }
}