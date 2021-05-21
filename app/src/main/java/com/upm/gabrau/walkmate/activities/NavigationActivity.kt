package com.upm.gabrau.walkmate.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color.parseColor
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.LocationComponentOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.expressions.Expression.*
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.Property.LINE_CAP_ROUND
import com.mapbox.mapboxsdk.style.layers.Property.LINE_JOIN_ROUND
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.*
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonOptions
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.mapboxsdk.utils.BitmapUtils
import com.mapbox.navigation.base.internal.extensions.applyDefaultParams
import com.mapbox.navigation.base.internal.extensions.coordinates
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesRequestCallback
import com.upm.gabrau.walkmate.R
import com.upm.gabrau.walkmate.models.Post
import com.upm.gabrau.walkmate.utils.AddressAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class NavigationActivity : AppCompatActivity(), OnMapReadyCallback, MapboxMap.OnMapClickListener,
    AddressAdapter.OnAddressClicked {

    private val isCurrentLocationRequested: MutableLiveData<Boolean> = MutableLiveData()
    private val changedUI: MutableLiveData<String> = MutableLiveData()
    private val changedRouteMode: MutableLiveData<String> = MutableLiveData()
    private lateinit var post: Post

    private lateinit var mapView: MapView
    private lateinit var toolbar: Toolbar
    private lateinit var data: Chip
    private lateinit var carMode: FloatingActionButton
    private lateinit var cycleMode: FloatingActionButton
    private lateinit var walkMode: FloatingActionButton
    private lateinit var locationFab: FloatingActionButton
    private lateinit var fab: FloatingActionButton
    private lateinit var addresses: RecyclerView
    private lateinit var locationLayout: TextInputLayout
    private lateinit var locationEditText: TextInputEditText

    private var mapboxNavigation: MapboxNavigation? = null
    private var mapboxMap: MapboxMap? = null
    private var selectedPoint: LatLng? = null

    private var gatheredAddresses: List<Address>? = arrayListOf()

    private val sourceRoute = "ROUTE_SOURCE"
    private val sourceClick = "CLICK_SOURCE"
    private val sourceOrigin = "ORIGIN_SOURCE"

    private val layerRoute = "ROUTE_LAYER"
    private val layerClick = "CLICK_LAYER"
    private val layerOrigin = "ORIGIN_LAYER"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        post = intent.getParcelableExtra("post")!!
        changedUI.value = Style.OUTDOORS
        changedRouteMode.value = DirectionsCriteria.PROFILE_DRIVING
        isCurrentLocationRequested.value = false

        data = findViewById(R.id.dataRoute)
        data.visibility = View.INVISIBLE

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        setUpObservers()
        toolbar()
        initFABStyle()
        setUpLocationFAB()
        initChipModes()

        addresses = findViewById(R.id.gathered_addresses)
        addresses.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        initAdapter()

        initLocationEditText()

        val mapboxNavigationOptions = MapboxNavigation
            .defaultNavigationOptionsBuilder(this, getString(R.string.mapbox_access_token))
            .build()

        mapboxNavigation = MapboxNavigation(mapboxNavigationOptions)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        menu?.findItem(R.id.toolbar_done)?.isVisible = false
        menu?.findItem(R.id.toolbar_logout)?.isVisible = false
        return true
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        val locationComponent = mapboxMap.locationComponent
        val locationComponentOptions = LocationComponentOptions.builder(this)
            .pulseEnabled(true)
            .build()

        mapboxMap.setStyle(changedUI.value) {
            this.mapboxMap = mapboxMap

            if (selectedPoint == null) {
                val p = post.geoPoint
                selectedPoint = p?.let { point -> LatLng(point.latitude, point.longitude) }
                mapboxMap.cameraPosition = CameraPosition.Builder()
                    .zoom(15.0).target(selectedPoint)
                    .build()
            } else {
                drawPoint(selectedPoint!!)
                isCurrentLocationRequested.value?.let { loc ->
                    if (loc) drawRouteWithLocation()
                    else drawRoute(Point.fromLngLat(selectedPoint!!.longitude, selectedPoint!!.latitude),
                        getPointFromPostAddress()!!)
                }
            }

            val locationComponentActivationOptions = LocationComponentActivationOptions
                .builder(this, it)
                .locationComponentOptions(locationComponentOptions)
                .build()

            locationComponent.activateLocationComponent(locationComponentActivationOptions)
            observeLocationEnabled(locationComponent)

            initSourceAndLayers(it)
            drawInitialPoint()
            mapboxMap.addOnMapClickListener(this)
        }
    }

    private fun setUpObservers() {
        changedUI.observe(this, {
            mapboxMap?.let { onMapReady(it) }
            data.visibility = View.INVISIBLE
            carMode.visibility = View.INVISIBLE
            cycleMode.visibility = View.INVISIBLE
            walkMode.visibility = View.INVISIBLE
        })

        changedRouteMode.observe(this, {
            carMode.backgroundTintList = ColorStateList.valueOf(getColor(R.color.teal_500))
            cycleMode.backgroundTintList = ColorStateList.valueOf(getColor(R.color.teal_500))
            walkMode.backgroundTintList = ColorStateList.valueOf(getColor(R.color.teal_500))

            when (it) {
                DirectionsCriteria.PROFILE_DRIVING -> carMode.backgroundTintList = ColorStateList.valueOf(getColor(R.color.purple_500))
                DirectionsCriteria.PROFILE_CYCLING -> cycleMode.backgroundTintList = ColorStateList.valueOf(getColor(R.color.purple_500))
                DirectionsCriteria.PROFILE_WALKING -> walkMode.backgroundTintList = ColorStateList.valueOf(getColor(R.color.purple_500))
            }
            selectedPoint?.let { point ->
                isCurrentLocationRequested.value?.let { locEnabled ->
                    if (!locEnabled) onMapClick(point)
                    else drawRouteWithLocation()
                }
            }
        })
    }

    private fun toolbar() {
        toolbar = findViewById(R.id.toolbar)
        findViewById<ImageView>(R.id.backpack).visibility = View.GONE
        setSupportActionBar(toolbar)
        supportActionBar?.title = post.name
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener{ onBackPressed() }
    }

    private fun initFABStyle() {
        fab = findViewById(R.id.fab_map_style)
        fab.setOnClickListener {
            if (changedUI.value == Style.SATELLITE) {
                changedUI.value = Style.OUTDOORS
                fab.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_satellite))
            } else {
                changedUI.value = Style.SATELLITE
                fab.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_street))
            }
        }
    }

    private fun initChipModes() {
        carMode = findViewById(R.id.mode_car)
        cycleMode = findViewById(R.id.mode_cycle)
        walkMode = findViewById(R.id.mode_walk)

        carMode.visibility = View.INVISIBLE
        cycleMode.visibility = View.INVISIBLE
        walkMode.visibility = View.INVISIBLE

        carMode.setOnClickListener { changedRouteMode.value = DirectionsCriteria.PROFILE_DRIVING }
        cycleMode.setOnClickListener { changedRouteMode.value = DirectionsCriteria.PROFILE_CYCLING }
        walkMode.setOnClickListener { changedRouteMode.value = DirectionsCriteria.PROFILE_WALKING }
    }

    private fun initSourceAndLayers(it: Style) {
        it.addSource(GeoJsonSource(sourceClick))
        it.addSource(GeoJsonSource(sourceOrigin))
        it.addSource(GeoJsonSource(sourceRoute, GeoJsonOptions().withLineMetrics(true)))

        val marker = ContextCompat.getDrawable(this, R.drawable.mapbox_marker_icon_default)
        it.addImage("ICON_ID", BitmapUtils.getBitmapFromDrawable(marker)!!)

        val goal = ContextCompat.getDrawable(this, R.drawable.ic_goal)
        it.addImage("DEST_ID", BitmapUtils.getBitmapFromDrawable(goal)!!)

        it.addLayerBelow(LineLayer(layerRoute, sourceRoute)
            .withProperties(lineCap(LINE_CAP_ROUND), lineJoin(LINE_JOIN_ROUND),
                lineWidth(6f), lineGradient(interpolate(linear(), lineProgress(),
                    stop(0f, color(parseColor("#FF6200EE"))),
                    stop(1f, color(parseColor("#FF009688")))))),
            "mapbox-location-shadow-layer")

        it.addLayerAbove(SymbolLayer(layerOrigin, sourceOrigin)
            .withProperties(iconImage("DEST_ID")), layerRoute)

        it.addLayerAbove(SymbolLayer(layerClick, sourceClick)
            .withProperties(iconImage("ICON_ID")), layerOrigin)
    }

    private fun initAdapter() { addresses.adapter = AddressAdapter(gatheredAddresses!!, this) }

    private fun initLocationEditText() {
        locationLayout = findViewById(R.id.layout_location)
        locationEditText = findViewById(R.id.edit_text_location)
        locationEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val inputMM = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager?
                inputMM?.hideSoftInputFromWindow(window.decorView.rootView.windowToken, 0)
                addresses.visibility = View.VISIBLE
                CoroutineScope(Dispatchers.Main).launch { gatheredAddresses() }
                true
            } else false
        }
    }

    private fun drawInitialPoint() {
        val p = post.geoPoint
        val origin = p?.let { point -> LatLng(point.latitude, point.longitude) }
        mapboxMap?.getStyle {
            val clickPointSource = it.getSourceAs<GeoJsonSource>(sourceOrigin)
            clickPointSource?.setGeoJson(Point.fromLngLat(origin!!.longitude, origin.latitude))
        }
    }

    private fun drawRoute(origin: Point, dest: Point) {
        mapboxNavigation?.requestRoutes(
            RouteOptions.builder().applyDefaultParams()
                .accessToken(getString(R.string.mapbox_access_token))
                .coordinates(origin, null, dest)
                .alternatives(true)
                .profile(changedRouteMode.value!!)
                .build(), routesReqCallback)
    }

    private fun drawRouteWithLocation() {
        mapboxMap?.locationComponent?.lastKnownLocation?.let { originLocation ->
            selectedPoint = LatLng(originLocation.latitude, originLocation.longitude)
            val loc = Point.fromLngLat(originLocation.longitude, originLocation.latitude)
            val dest = getPointFromPostAddress()
            drawRoute(loc, dest!!)
        }
    }

    private fun drawPoint(latLng: LatLng) : Point {
        val origin = Point.fromLngLat(latLng.longitude, latLng.latitude)
        mapboxMap?.getStyle {
            val clickPointSource = it.getSourceAs<GeoJsonSource>(sourceClick)
            clickPointSource?.setGeoJson(origin)
        }
        return origin
    }

    override fun onMapClick(latLng: LatLng): Boolean {
        selectedPoint = latLng
        val origin = drawPoint(latLng)
        val dest = getPointFromPostAddress()
        isCurrentLocationRequested.value?.let { if (!it) { drawRoute(origin, dest!!) } }
        return true
    }

    private val routesReqCallback = object : RoutesRequestCallback {
        override fun onRoutesReady(routes: List<DirectionsRoute>) {
            if (routes.isNotEmpty()) {
                mapboxMap?.getStyle {
                    val clickPointSource = it.getSourceAs<GeoJsonSource>(sourceRoute)
                    val routeLineString = LineString.fromPolyline(routes[0].geometry()!!, 6)
                    clickPointSource?.setGeoJson(routeLineString)

                    data.visibility = View.VISIBLE
                    carMode.visibility = View.VISIBLE
                    cycleMode.visibility = View.VISIBLE
                    walkMode.visibility = View.VISIBLE

                    val route = mapboxNavigation?.getRoutes()?.get(0)
                    route?.let { t ->
                        val d = "ETA: ${(t.duration() / 60.0).roundToInt()} min, " +
                                "Distance: ${(t.distance() / 1000).roundToInt()} km"
                        data.text = d
                    }
                }
            }
        }
        override fun onRoutesRequestFailure(throwable: Throwable, routeOptions: RouteOptions) {}
        override fun onRoutesRequestCanceled(routeOptions: RouteOptions) {}
    }

    private fun getPointFromPostAddress(): Point? {
        val p = post.geoPoint
        return p?.let { Point.fromLngLat(it.longitude, it.latitude) }
    }

    private fun gatheredAddresses(): Boolean {
        return try {
            val geocoder = Geocoder(this)
            gatheredAddresses = geocoder.getFromLocationName(locationEditText.text.toString(), 5)
            gatheredAddresses?.let { initAdapter() }
            true
        } catch (e: Exception) {
            false
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
                locationComponent.zoomWhileTracking(15.0, 1000L)
                drawRouteWithLocation()
            } else {
                locationComponent.isLocationComponentEnabled = false
            }
        })
    }

    private fun setUpLocationFAB() {
        locationFab = findViewById(R.id.fab_location)
        locationFab.setOnClickListener {
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
        val loc = LatLng(address.latitude, address.longitude)
        drawPoint(loc)
        drawRoute(Point.fromLngLat(loc.longitude, loc.latitude), getPointFromPostAddress()!!)
        selectedPoint = loc
        mapboxMap?.cameraPosition = CameraPosition.Builder().zoom(12.0).target(loc).build()
    }
}