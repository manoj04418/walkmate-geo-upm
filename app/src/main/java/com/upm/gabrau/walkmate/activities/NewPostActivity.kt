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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.GeoPoint
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
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.*
import com.mapbox.mapboxsdk.utils.ColorUtils
import com.upm.gabrau.walkmate.databinding.ActivityNewPostBinding
import com.upm.gabrau.walkmate.R
import com.upm.gabrau.walkmate.firebase.Queries
import com.upm.gabrau.walkmate.models.Post
import com.upm.gabrau.walkmate.utils.AddressAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NewPostActivity : AppCompatActivity(), AddressAdapter.OnAddressClicked,
    ActivityCompat.OnRequestPermissionsResultCallback {

    private lateinit var binding: ActivityNewPostBinding
    private val isCurrentLocationRequested: MutableLiveData<Boolean> = MutableLiveData()
    private val changedUI: MutableLiveData<String> = MutableLiveData()

    private lateinit var mapboxMap: MapboxMap
    private lateinit var circleManager: CircleManager
    private var currentCircle: Circle? = null
    private var addresses: List<Address>? = arrayListOf()
    private var selectedAddress: GeoPoint? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))

        binding = ActivityNewPostBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        changedUI.value = Style.OUTDOORS

        toolbar()
        binding.gatheredAddresses.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        initAdapter()
        initFABStyle()
        initLocationEditText(view)

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync {
            this.mapboxMap = it
            changedUI.observe(this, { style ->
                mapboxMap.setStyle(style)
            })

            mapboxMap.cameraPosition = CameraPosition.Builder()
                .zoom(3.0).target(LatLng(40.4169019,-3.7056721))
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

                circleManager = CircleManager(binding.mapView, mapboxMap, style)

                observeLocationEnabled(locationComponent)
            }

            mapboxMap.addOnMapClickListener { point -> drawCircle(point) }
        }

        setUpLocationFAB()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.toolbar_done -> {
                CoroutineScope(Dispatchers.Main).launch {
                    if (selectedAddress != null && binding.editTextTitle.text?.trim()?.isNotEmpty() == true) {
                        selectedAddress?.let {
                            binding.editTextTitle.text?.let{ title ->
                                val post = Post(name = title.toString(),
                                    geoPoint = GeoPoint(it.latitude, it.longitude))
                                val success = Queries().uploadPost(post)
                                if (success) finish()
                                else Toast.makeText(baseContext, "Something went wrong",
                                    Toast.LENGTH_SHORT).show()
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
        setSupportActionBar(binding.toolbar.root)
        supportActionBar?.title = "New Post"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.root.setNavigationOnClickListener { onBackPressed() }
    }

    private fun initAdapter() {
        binding.gatheredAddresses.adapter = AddressAdapter(addresses!!, this)
    }

    private fun initLocationEditText(view: View) {
        binding.editTextLocation.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val inputMM = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager?
                inputMM?.hideSoftInputFromWindow(view.windowToken, 0)
                binding.gatheredAddresses.visibility = View.VISIBLE
                gatheredAddresses()
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

    private fun drawCircle(location: LatLng): Boolean {
        currentCircle?.let { circleManager.delete(it) }
        val cameraPosition = CameraPosition.Builder()
            .zoom(if (mapboxMap.cameraPosition.zoom > 10.0) mapboxMap.cameraPosition.zoom else 10.0)
            .target(location)
            .build()

        val circleOptions = CircleOptions()
            .withCircleColor(ColorUtils.colorToRgbaString(resources.getColor(R.color.purple_500, resources.newTheme())))
            .withCircleRadius(6f)
            .withLatLng(location)

        currentCircle = circleManager.create(circleOptions)
        selectedAddress = GeoPoint(location.latitude, location.longitude)
        mapboxMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 2000)
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

    override fun onAddressClicked(address: Address) {
        drawCircle(LatLng(address.latitude, address.longitude))
    }
}