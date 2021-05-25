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
    /** Variable to see in RunTime if the location has been requested */
    private val isCurrentLocationRequested: MutableLiveData<Boolean> = MutableLiveData()
    /** Variable to manage the Map Style in RunTime: OUTDOORS, SATELLITE */
    private val changedUI: MutableLiveData<String> = MutableLiveData()

    /** Holds the MapBox map instance */
    private var mapboxMap: MapboxMap? = null
    /** Holds the addresses extracted from the [Geocoder] when a text search is done */
    private var addresses: List<Address>? = arrayListOf()
    /** Holds the selected point at any given time: clicked on map, current location or text searched */
    private var selectedPoint: LatLng? = null

    private val sourceOrigin = "ORIGIN_SOURCE"
    private val layerOrigin = "ORIGIN_LAYER"

    /**
     * Initializes the [changedUI] and [isCurrentLocationRequested] for further use
     * in latter code.
     *
     * Also, sets up all the required additional views such as the observers for the [MutableLiveData]
     * objects, the toolbar, EditTexts, FABs.
     * */
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

    /**
     * Function called when the MapBox map is actually ready to be initialized with custom data.
     *
     * Here we get the [LocationComponent] in order to manage the user's location in the map. This will
     * be used for painting the location in the map depending on the [isCurrentLocationRequested] value
     * and the state of the location permission.
     *
     * Also, we set the map style to the [changedUI] value and, when the style is ready and set, we
     * make the layers of the map to draw all the things in an ordered way and set up the map click listener.
     *
     * Finally, as the [changedUI] controls the style, everytime it is changed, setStyle is called
     * thus erasing the content of all layers. We managed the re-draw of all content at the beginning
     * of the setStyle function in order to make it seem to the user that when changing styles, all is preserved.
     * */
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
        menu?.findItem(R.id.toolbar_search)?.isVisible = false
        return true
    }

    /**
     * Handles the DONE button from the toolbar to actually upload the post when validated.
     * It calls the function uploadPost from [Queries].
     * */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.toolbar_done -> {
                CoroutineScope(Dispatchers.Main).launch {
                    if (selectedPoint != null && binding.editTextTitle.text?.trim()?.isNotEmpty() == true) {
                        selectedPoint?.let {
                            binding.editTextTitle.text?.let{ title ->
                                val post = Post(
                                    name = title.toString(),
                                    creator = Queries().getCurrentUserId(),
                                    geoPoint = GeoPoint(it.latitude, it.longitude))
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

    /** Sets up the toolbar */
    private fun toolbar() {
        findViewById<ImageView>(R.id.backpack).visibility = View.GONE
        setSupportActionBar(binding.toolbar.root)
        supportActionBar?.title = "New Location"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        binding.toolbar.root.setNavigationOnClickListener { onBackPressed() }
    }

    /** Updates the adapter with new addresses to paint on the UI when necessary */
    private fun initAdapter() { binding.gatheredAddresses.adapter = AddressAdapter(addresses!!, this) }

    /**
     * Initializes the editing done of the text view. When the user is done typing, [gatheredAddresses]
     * is called for retrieving the most likely addresses for the user to choose.
     * */
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

    /**
     * Function that gets the location text that the user has written and extracts at most
     * 5 different results with [Geocoder]. When retrieved, updates the adapter with [initAdapter]
     * to paint the different addresses below the location edit text
     * */
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

    /**
     * Draws the marker when the user clicked the map in a certain location. It is drawn in the
     * [SymbolLayer].
     *
     * @param location point extracted from the map
     * @param moveCamera whether to move the camera to the point or not
     * */
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

    /**
     * Sets the click listener for the Map Style. When clicked, it actually changes the value of
     * [changedUI], triggering the observer from [setUpObservers] and changes the UI of the FAB itself
     * */
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

    /**
     * Set ups the observer for [isCurrentLocationRequested]. It will enable or disable
     * the [locationComponent] depending of the value.
     *
     * @param locationComponent from the MapBox map to enable and show the location in the map
     * */
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

    /**
     * Sets up the [locationFab] click listener. It will check if the location is enabled or not, and
     * then proceed and request the permission to use it. Upon handling, [isCurrentLocationRequested]
     * will update its value.
     * */
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