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
import androidx.core.view.isVisible
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
import com.mapbox.navigation.ui.NavigationView
import com.mapbox.navigation.ui.NavigationViewOptions
import com.mapbox.navigation.ui.OnNavigationReadyCallback
import com.mapbox.navigation.ui.listeners.NavigationListener
import com.mapbox.navigation.ui.map.NavigationMapboxMap
import com.upm.gabrau.walkmate.R
import com.upm.gabrau.walkmate.models.Post
import com.upm.gabrau.walkmate.utils.AddressAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class NavigationActivity : AppCompatActivity(), OnMapReadyCallback, MapboxMap.OnMapClickListener,
    AddressAdapter.OnAddressClicked, OnNavigationReadyCallback, NavigationListener {

    /** Variable to see in RunTime if the location has been requested */
    private val isCurrentLocationRequested: MutableLiveData<Boolean> = MutableLiveData()
    /** Variable to manage the Map Style in RunTime: OUTDOORS, SATELLITE */
    private val changedUI: MutableLiveData<String> = MutableLiveData()
    /** Variable to manage the route mode in RunTime: CAR, CYCLE, WALK */
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

    /** Holds the navigation instance of the map and serves for getting the routes and directions */
    private var mapboxNavigation: MapboxNavigation? = null
    /** Holds the MapBox map instance */
    private var mapboxMap: MapboxMap? = null
    /** Holds the [NavigationView] UI reference */
    private var navigationView: NavigationView? = null
    private var navigationMapboxMap: NavigationMapboxMap? = null
    /** Holds the route extracted from [mapboxNavigation] */
    private var mapboxRoute: DirectionsRoute? = null
    /** Holds the selected point at any given time: clicked on map, current location or text searched */
    private var selectedPoint: LatLng? = null
    /** Holds the addresses extracted from the [Geocoder] when a text search is done */
    private var gatheredAddresses: List<Address>? = arrayListOf()

    private val sourceRoute = "ROUTE_SOURCE"
    private val sourceClick = "CLICK_SOURCE"
    private val sourceOrigin = "ORIGIN_SOURCE"

    private val layerRoute = "ROUTE_LAYER"
    private val layerClick = "CLICK_LAYER"
    private val layerOrigin = "ORIGIN_LAYER"

    /**
     * Initializes the [changedUI], [changedRouteMode] and [isCurrentLocationRequested] for further use
     * in latter code.
     *
     * Initializes all the views, as MapBox routes and navigation does not seem to go well with data binding.
     * Creates the [mapView] and the [mapboxNavigation] options.
     *
     * Also, sets up all the required additional views such as the observers for the [MutableLiveData]
     * objects, the toolbar, EditTexts, FABs and chips.
     * */
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
        navigationView = findViewById(R.id.navigationView)
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

    /**
     * Inflates the menu from the custom toolbar and hides all menu items.
     * */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        menu?.findItem(R.id.toolbar_done)?.isVisible = false
        menu?.findItem(R.id.toolbar_logout)?.isVisible = false
        menu?.findItem(R.id.toolbar_search)?.isVisible = false
        return true
    }

    /**
     * Function called when the MapBox map is actually ready to be initialized with custom data.
     *
     * Here we get the [LocationComponent] in order to manage the user's location in the map. This will
     * be used for painting the location in the map depending on the [isCurrentLocationRequested] value
     * and the state of the location permission.
     *
     * Also, we set the map style to the [changedUI] value and, when the style is ready and set, we
     * set up the goal (Geo point of [post]), make the layers of the map to draw all the things in
     * an ordered way and set up the map click listener.
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

    /**
     * Function that observes [changedUI] and [changedRouteMode] values and executes the closure code
     * when the value changes.
     *
     * [changedUI] calls again [onMapReady] and hides all extra UI when a route is present
     * [changedRouteMode] changes the color of the selected FAB and it automatically changes the route
     * */
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

    /** Initializes the custom toolbar */
    private fun toolbar() {
        toolbar = findViewById(R.id.toolbar)
        findViewById<ImageView>(R.id.backpack).visibility = View.GONE
        setSupportActionBar(toolbar)
        supportActionBar?.title = post.name
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener{ onBackPressed() }
    }

    /**
     * Manages the on back pressed:
     *
     * If the [navigationView] is visible and the navigation is active, closes the navigation and return
     * to initial interface. Else, finishes the activity.
     * */
    override fun onBackPressed() {
        navigationView?.let {
            if (it.isVisible) setRouteActive(false)
            else super.onBackPressed()
        }
    }

    /**
     * Sets the click listener for the Map Style. When clicked, it actually changes the value of
     * [changedUI], triggering the observer from [setUpObservers] and changes the UI of the FAB itself
     * */
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

    /**
     * Initializes the FABs for the route modes and sets the listeners. They actually change the value
     * of [changedRouteMode], triggering the observer from [setUpObservers]
     * */
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

    /**
     * Initializes the [GeoJsonSource] and the layers ([LineLayer] for routes, [SymbolLayer] for markers)
     * for the MapBox map.
     *
     * @param it style of the map to draw the layers in
     * */
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

    /** Updates the adapter with new addresses to paint on the UI when necessary */
    private fun initAdapter() { addresses.adapter = AddressAdapter(gatheredAddresses!!, this) }

    /**
     * Initializes the editing done of the text view. When the user is done typing, [gatheredAddresses]
     * is called for retrieving the most likely addresses for the user to choose.
     * */
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

    /**
     * Draws the initial geo point of the [post] in the marker layer of the map.
     * This point is static and final. It is not changed through out the life of the activity.
     */
    private fun drawInitialPoint() {
        val p = post.geoPoint
        val origin = p?.let { point -> LatLng(point.latitude, point.longitude) }
        mapboxMap?.getStyle {
            val clickPointSource = it.getSourceAs<GeoJsonSource>(sourceOrigin)
            clickPointSource?.setGeoJson(Point.fromLngLat(origin!!.longitude, origin.latitude))
        }
    }

    /**
     * Draws the route when a marker is added in the map. The route is established between the
     * initial point and the [selectedPoint]. It is drawn in the [LineLayer] of the map. Here is where
     * the [changedRouteMode] is used. It makes use of [routesReqCallback] to actually draw the route
     * when it is ready.
     *
     * @param origin point
     * @param dest point
     * */
    private fun drawRoute(origin: Point, dest: Point) {
        mapboxNavigation?.requestRoutes(
            RouteOptions.builder().applyDefaultParams()
                .accessToken(getString(R.string.mapbox_access_token))
                .coordinates(origin, null, dest)
                .alternatives(true)
                .profile(changedRouteMode.value!!)
                .build(), routesReqCallback)
    }

    /**
     * Draws the route when the location is active. The route is established between the
     * initial point and the user's location. It is drawn in the [LineLayer] of the map.
     * */
    private fun drawRouteWithLocation() {
        mapboxMap?.locationComponent?.lastKnownLocation?.let { originLocation ->
            selectedPoint = LatLng(originLocation.latitude, originLocation.longitude)
            val loc = Point.fromLngLat(originLocation.longitude, originLocation.latitude)
            val dest = getPointFromPostAddress()
            drawRoute(loc, dest!!)
        }
    }

    /**
     * Draws the marker when the user clicked the map in a certain location. It is drawn in the
     * [SymbolLayer].
     *
     * @param latLng point extracted from the map
     * */
    private fun drawPoint(latLng: LatLng) : Point {
        val origin = Point.fromLngLat(latLng.longitude, latLng.latitude)
        mapboxMap?.getStyle {
            val clickPointSource = it.getSourceAs<GeoJsonSource>(sourceClick)
            clickPointSource?.setGeoJson(origin)
        }
        return origin
    }

    /**
     * Draws the point with [drawPoint] and automatically draws the route with [drawRoute]
     *
     * @param latLng point extracted from the map
     * */
    override fun onMapClick(latLng: LatLng): Boolean {
        selectedPoint = latLng
        val origin = drawPoint(latLng)
        val dest = getPointFromPostAddress()
        isCurrentLocationRequested.value?.let { if (!it) { drawRoute(origin, dest!!) } }
        return true
    }

    /**
     * Callback that is called when the route is ready to be painted in the map.
     *
     * UI changes once the route is ready and sets the click listener to the [data] button. It makes it
     * so the user can initialize a route navigation view to go to the goal point. It only works when the
     * location is enabled.
     * */
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

                    mapboxRoute = mapboxNavigation?.getRoutes()?.get(0)
                    mapboxRoute?.let { t ->
                        val d = "ETA: ${(t.duration() / 60.0).roundToInt()} min, " +
                                "Distance: ${(t.distance() / 1000).roundToInt()} km"
                        data.text = d
                        data.setOnClickListener {
                            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                            var enabled = false

                            try {
                                enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                            } catch (ex: java.lang.Exception) { }

                            if (enabled) {
                                navigationView?.initialize(this@NavigationActivity)
                                setRouteActive(true)
                            } else {
                                Toast.makeText(baseContext, "You have to enable the location to navigate to the goal",
                                    Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
        override fun onRoutesRequestFailure(throwable: Throwable, routeOptions: RouteOptions) {}
        override fun onRoutesRequestCanceled(routeOptions: RouteOptions) {}
    }

    /**
     * Translates the geo point from the [post] to [Point]
     * */
    private fun getPointFromPostAddress(): Point? {
        val p = post.geoPoint
        return p?.let { Point.fromLngLat(it.longitude, it.latitude) }
    }

    /**
     * Function that gets the [locationEditText] text that the user has written and extracts at most
     * 5 different results with [Geocoder]. When retrieved, updates the adapter with [initAdapter]
     * to paint the different addresses below the [locationLayout]
     * */
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
                locationComponent.zoomWhileTracking(15.0, 1000L)
                drawRouteWithLocation()
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

    /**
     * Function that hides or shows the whole route view upon showing the [navigationView].
     *
     * @param activate the [navigationView] or not
     * */
    private fun setRouteActive(activate: Boolean) {
        if (activate) {
            navigationView?.visibility = View.VISIBLE
            mapView.visibility = View.INVISIBLE
            carMode.visibility = View.INVISIBLE
            cycleMode.visibility = View.INVISIBLE
            walkMode.visibility = View.INVISIBLE
            data.visibility = View.INVISIBLE
            fab.visibility = View.INVISIBLE
            locationFab.visibility = View.INVISIBLE
            locationLayout.visibility = View.INVISIBLE
            addresses.visibility = View.INVISIBLE
        } else {
            navigationView?.stopNavigation()
            navigationView?.visibility = View.INVISIBLE
            mapView.visibility = View.VISIBLE
            carMode.visibility = View.VISIBLE
            cycleMode.visibility = View.VISIBLE
            walkMode.visibility = View.VISIBLE
            data.visibility = View.VISIBLE
            fab.visibility = View.VISIBLE
            locationFab.visibility = View.VISIBLE
            locationLayout.visibility = View.VISIBLE
            addresses.visibility = View.VISIBLE
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

    /**
     * Called when the navigation view is actually ready to be shown and we set up all the necessary things
     * for the view to work.
     * */
    override fun onNavigationReady(isRunning: Boolean) {
        if (!isRunning) {
            if (navigationView?.retrieveNavigationMapboxMap() != null) {
                navigationMapboxMap = navigationView?.retrieveNavigationMapboxMap()!!
                navigationView?.retrieveMapboxNavigation()?.let { this.mapboxNavigation = it }
                val optionsBuilder = NavigationViewOptions.builder(this)
                optionsBuilder.navigationListener(this)
                optionsBuilder.directionsRoute(mapboxRoute)
                optionsBuilder.shouldSimulateRoute(false)
                navigationView?.startNavigation(optionsBuilder.build())
            }
        }
    }

    override fun onCancelNavigation() { setRouteActive(false) }

    override fun onNavigationFinished() { setRouteActive(false) }

    override fun onNavigationRunning() { }
}