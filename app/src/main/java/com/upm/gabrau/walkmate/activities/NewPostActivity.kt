package com.upm.gabrau.walkmate.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.GeoPoint
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.LocationComponentOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.mapboxsdk.utils.BitmapUtils
import com.upm.gabrau.walkmate.databinding.ActivityNewPostBinding
import com.upm.gabrau.walkmate.R
import com.upm.gabrau.walkmate.firebase.Queries
import com.upm.gabrau.walkmate.models.Post
import com.upm.gabrau.walkmate.utils.AddressAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NewPostActivity : AppCompatActivity(), OnMapReadyCallback, MapboxMap.OnMapClickListener,
    AddressAdapter.OnAddressClicked {

    private lateinit var binding: ActivityNewPostBinding
    private val isCurrentLocationRequested: MutableLiveData<Boolean> = MutableLiveData()
    private val changedUI: MutableLiveData<String> = MutableLiveData()

    private var mapboxMap: MapboxMap? = null
    private var addresses: List<Address>? = arrayListOf()
    private var selectedPoint: LatLng? = null

    private val sourceOrigin = "ORIGIN_SOURCE"
    private val layerOrigin = "ORIGIN_LAYER"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))

        binding = ActivityNewPostBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        changedUI.value = Style.OUTDOORS
        changedUI.observe(this, { mapboxMap?.let { onMapReady(it) } })

        toolbar()
        binding.gatheredAddresses.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        initAdapter()
        initFABStyle()
        initLocationEditText(view)

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)

        setUpLocationFAB()
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        val locationComponent = mapboxMap.locationComponent
        val locationComponentOptions = LocationComponentOptions.builder(this)
            .pulseEnabled(true)
            .build()

        mapboxMap.setStyle(changedUI.value) { style ->
            this.mapboxMap = mapboxMap

            if (selectedPoint != null) { drawMarker(selectedPoint!!, false) }
            else {
                selectedPoint = LatLng(40.4169019,-3.7056721)
                mapboxMap.cameraPosition = CameraPosition.Builder()
                    .zoom(8.0).target(selectedPoint)
                    .build()
            }

            val locationComponentActivationOptions = LocationComponentActivationOptions
                .builder(this, style)
                .locationComponentOptions(locationComponentOptions)
                .build()

            locationComponent.activateLocationComponent(locationComponentActivationOptions)
            observeLocationEnabled(locationComponent)

            style.addSource(GeoJsonSource(sourceOrigin))

            val marker = ContextCompat.getDrawable(this, R.drawable.mapbox_marker_icon_default)
            style.addImage("ICON_ID", BitmapUtils.getBitmapFromDrawable(marker)!!)

            style.addLayer(SymbolLayer(layerOrigin, sourceOrigin).withProperties(iconImage("ICON_ID")))

            mapboxMap.addOnMapClickListener(this)
        }
    }

    override fun onMapClick(point: LatLng): Boolean {
        drawMarker(LatLng(point.latitude, point.longitude))
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        menu?.findItem(R.id.toolbar_logout)?.isVisible = false
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.toolbar_done -> {
                CoroutineScope(Dispatchers.Main).launch {
                    if (selectedPoint != null && binding.editTextTitle.text?.trim()?.isNotEmpty() == true) {
                        selectedPoint?.let {
                            binding.editTextTitle.text?.let{ title ->
                                val post = Post(name = title.toString(), geoPoint = GeoPoint(it.latitude, it.longitude))
                                val success = Queries().uploadPost(post)
                                if (success) finish()
                                else Toast.makeText(baseContext, "Something went wrong", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(baseContext, "Please, fill up all the fields before creating a new post",
                            Toast.LENGTH_SHORT).show()
                    }
                }
                true
            }
            else -> true
        }
    }

    private fun toolbar() {
        findViewById<ImageView>(R.id.backpack).visibility = View.GONE
        setSupportActionBar(binding.toolbar.root)
        supportActionBar?.title = "New Location"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.root.setNavigationOnClickListener { onBackPressed() }
    }

    private fun initAdapter() { binding.gatheredAddresses.adapter = AddressAdapter(addresses!!, this) }

    private fun initLocationEditText(view: View) {
        binding.editTextLocation.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val inputMM = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager?
                inputMM?.hideSoftInputFromWindow(view.windowToken, 0)
                binding.gatheredAddresses.visibility = View.VISIBLE
                CoroutineScope(Dispatchers.Main).launch { gatheredAddresses() }
                true
            } else false
        }
    }

    private fun gatheredAddresses(): Boolean {
        return try {
            val geocoder = Geocoder(this)
            addresses = geocoder.getFromLocationName(binding.editTextLocation.text.toString(), 5)
            addresses?.let { initAdapter() }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun drawMarker(location: LatLng, moveCamera: Boolean = true): Boolean {
        mapboxMap?.let { map ->
            val origin = Point.fromLngLat(location.longitude, location.latitude)
            map.getStyle {
                val clickPointSource = it.getSourceAs<GeoJsonSource>(sourceOrigin)
                clickPointSource?.setGeoJson(origin)
            }

            selectedPoint = location

            if (moveCamera) {
                val cameraPosition = CameraPosition.Builder()
                    .zoom(if (map.cameraPosition.zoom > 8.0) map.cameraPosition.zoom else 8.0)
                    .target(location)
                    .build()
                map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 2000)
            }
        }
        return true
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

    @SuppressLint("MissingPermission")
    private fun observeLocationEnabled(locationComponent: LocationComponent) {
        locationComponent.isLocationComponentEnabled = false
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

    private fun setUpLocationFAB() {
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

    override fun onAddressClicked(address: Address) { drawMarker(LatLng(address.latitude, address.longitude)) }
}