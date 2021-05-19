package com.upm.gabrau.walkmate.activities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.LocationComponentOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.CircleManager
import com.mapbox.mapboxsdk.plugins.annotation.CircleOptions
import com.mapbox.mapboxsdk.utils.ColorUtils
import com.upm.gabrau.walkmate.R
import com.upm.gabrau.walkmate.databinding.ActivityMapBinding
import com.upm.gabrau.walkmate.models.Post

class MapActivity : AppCompatActivity(), ActivityCompat.OnRequestPermissionsResultCallback {
    private lateinit var binding: ActivityMapBinding
    private val isCurrentLocationRequested: MutableLiveData<Boolean> = MutableLiveData()
    private val changedUI: MutableLiveData<String> = MutableLiveData()

    private lateinit var mapboxMap: MapboxMap
    private lateinit var circleManager: CircleManager
    private lateinit var post: Post
    private var currentPosition: LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))

        binding = ActivityMapBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        post = intent.getParcelableExtra("post")!!

        toolbar()
        initFABStyle()

        changedUI.value = Style.OUTDOORS

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync {
            this.mapboxMap = it
            changedUI.observe(this, { style ->
                mapboxMap.setStyle(style)
            })

            mapboxMap.cameraPosition = CameraPosition.Builder()
                .zoom(15.0).target(LatLng(post.geoPoint?.latitude!!, post.geoPoint?.longitude!!))
                .build()

            val locationComponent = mapboxMap.locationComponent
            val locationComponentOptions = LocationComponentOptions.builder(this)
                .pulseEnabled(true)
                .build()

            mapboxMap.setStyle(changedUI.value) { style ->
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
                        val last = locationComponent.lastKnownLocation
                        last?.let { l -> currentPosition = LatLng(l.latitude, l.longitude) }
                    } else {
                        locationComponent.isLocationComponentEnabled = false
                    }
                })

                val circleOptions = CircleOptions()
                    .withCircleColor(ColorUtils.colorToRgbaString(resources.getColor(R.color.purple_500, resources.newTheme())))
                    .withCircleRadius(8f)
                    .withLatLng(LatLng(post.geoPoint?.latitude!!, post.geoPoint?.longitude!!))

                circleManager.create(circleOptions)
            }
        }

        binding.fabLocation.setOnClickListener {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var enabled = false

            try {
                enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            } catch (ex: java.lang.Exception) { }

            if (!enabled) {
                Toast.makeText(this, "You do not have the location active!", Toast.LENGTH_SHORT).show()
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                        val permissions = Array(1) { Manifest.permission.ACCESS_FINE_LOCATION }
                        ActivityCompat.requestPermissions(this, permissions, 1)
                    } else {
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
    }

    private fun toolbar() {
        setSupportActionBar(binding.toolbar.root)
        supportActionBar?.title = post.name
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.root.setNavigationOnClickListener{ onBackPressed() }
    }

    private fun initFABStyle() {
        binding.fabMapStyle.setOnClickListener {
            if (changedUI.value == Style.SATELLITE) {
                changedUI.value = Style.OUTDOORS
                binding.fabMapStyle.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_satellite))
            } else {
                changedUI.value = Style.SATELLITE
                binding.fabMapStyle.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_street))
            }
        }
    }

    private fun calculateDistance() {
        // TODO: Calculate position between points
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
                        isCurrentLocationRequested.value = true
                    }
                } else {
                    Toast.makeText(applicationContext, "Grant location permission manually in settings", Toast.LENGTH_LONG).show()
                }
                return
            }
        }
    }
}