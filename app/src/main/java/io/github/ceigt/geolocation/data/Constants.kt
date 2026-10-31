//Constants.kt
package io.github.ceigt.geolocation.data

// APP
const val MANAGER_APP_PACKAGE_NAME = "io.github.ceigt.geolocation"
const val SHARED_PREFS_FILE = "xposed_shared_prefs"
const val REMOTE_PREFS_GROUP = "settings"

// KEYS
const val KEY_IS_PLAYING = "is_playing"

const val KEY_LAST_CLICKED_LOCATION = "last_clicked_location"

const val KEY_USE_ACCURACY = "use_accuracy"
const val KEY_ACCURACY  = "accuracy"

const val KEY_USE_ALTITUDE = "use_altitude"
const val KEY_ALTITUDE  = "altitude"

const val KEY_USE_RANDOMIZE  = "use_randomize"
const val KEY_RANDOMIZE_RADIUS = "randomize_radius"

const val KEY_USE_VERTICAL_ACCURACY = "use_vertical_accuracy"
const val KEY_VERTICAL_ACCURACY = "vertical_accuracy"

const val KEY_USE_MEAN_SEA_LEVEL = "use_mean_sea_level"
const val KEY_MEAN_SEA_LEVEL = "mean_sea_level"

const val KEY_USE_MEAN_SEA_LEVEL_ACCURACY = "use_mean_sea_level_accuracy"
const val KEY_MEAN_SEA_LEVEL_ACCURACY = "mean_sea_level_accuracy"

const val KEY_USE_SPEED = "use_speed"
const val KEY_SPEED = "speed"

const val KEY_USE_SPEED_ACCURACY = "use_speed_accuracy"
const val KEY_SPEED_ACCURACY = "speed_accuracy"

const val KEY_FAVORITES = "favorites"

const val KEY_TARGET_APPS = "target_apps"
const val KEY_APP_COORDINATE_SYSTEMS = "app_coordinate_systems"
const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"

const val KEY_HIDE_FAKE_LOCATION_TOAST = "hide_fake_location_toast"

const val KEY_ENABLE_BROADCAST_CONTROL = "enable_broadcast_control"
const val KEY_LANGUAGE_TAG = "language_tag"
const val KEY_BAIDU_MAP_AK = "baidu_map_ak"
const val KEY_MAP_PROVIDER = "map_provider"
const val KEY_OSM_TILE_SERVER = "osm_tile_server"
const val KEY_AMAP_WEB_KEY = "amap_web_key"
const val KEY_AMAP_SECURITY_CODE = "amap_security_code"
const val KEY_GOOGLE_MAPS_API_KEY = "google_maps_api_key"

const val KEY_ENABLE_SYSTEM_HOOKS = "enable_system_hooks"
const val KEY_ENABLE_GLOBAL_HOOKS = "enable_global_hooks"
const val KEY_ENABLE_MOCK_PROVIDER = "enable_mock_provider"
const val KEY_ENABLE_ROOT_MODE = "enable_root_mode"
const val KEY_LOCATION_MODE = "location_mode"
const val KEY_MOCK_FLAG_POLICY = "mock_flag_policy"
const val KEY_HOOK_INSTALL_POLICY = "hook_install_policy"
const val KEY_APP_HOOK_INSTALL_POLICIES = "app_hook_install_policies"

// GNSS & Jitter Keys
const val KEY_GNSS_CONSTELLATION_PRESET = "gnss_constellation_preset"
const val KEY_GNSS_SIGNAL_STRENGTH_PRESET = "gnss_signal_strength_preset"
const val KEY_GNSS_VISIBLE_SATELLITES = "gnss_visible_satellites"
const val KEY_GNSS_USED_SATELLITES = "gnss_used_satellites"
const val KEY_GNSS_JITTER_ENABLED = "gnss_jitter_enabled"
const val KEY_GNSS_JITTER_ALGORITHM = "gnss_jitter_algorithm"
const val KEY_GNSS_JITTER_RADIUS = "gnss_jitter_radius"
const val KEY_GNSS_JITTER_SMOOTHING = "gnss_jitter_smoothing"
const val KEY_GNSS_JITTER_DRIFT_SPEED = "gnss_jitter_drift_speed"

// Channel Settings Keys
const val KEY_CHANNEL_SETTINGS = "channel_settings"
const val KEY_LOCATION_ADDRESS_METADATA = "location_address_metadata"

// Cadence Keys
const val KEY_GLOBAL_CADENCE_MODE = "global_cadence_mode"
const val KEY_GLOBAL_CADENCE_CHANNEL = "global_cadence_channel"
const val KEY_GLOBAL_CUSTOM_SPM = "global_custom_spm"
const val KEY_APP_CADENCE_CONFIGS = "app_cadence_configs"

// Environment Keys
const val KEY_VIRTUAL_WIFI_ENABLED = "virtual_wifi_enabled"
const val KEY_VIRTUAL_CELL_ENABLED = "virtual_cell_enabled"
const val KEY_ENVIRONMENT_PROFILES = "environment_profiles"
const val KEY_ACTIVE_ENVIRONMENT_PROFILE_ID = "active_environment_profile_id"

// BLE Keys
const val KEY_BLE_PROFILES = "ble_profiles"
const val KEY_ACTIVE_BLE_PROFILE_ID = "active_ble_profile_id"
const val KEY_BLE_DELIVERY_MODE = "ble_delivery_mode"

// NFC Keys
const val KEY_NFC_PROFILES = "nfc_profiles"
const val KEY_ACTIVE_NFC_PROFILE_ID = "active_nfc_profile_id"

// Route Favorites Keys
const val KEY_ROUTE_FAVORITES = "route_favorites"

const val KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted"

// Packages added/removed from module scope when system-level hooks are toggled.
val SYSTEM_HOOK_PACKAGES = listOf("system", "android", "com.android.phone")

 // DEFAULT VALUES
const val DEFAULT_USE_ACCURACY = false
const val DEFAULT_ACCURACY = 0.0

const val DEFAULT_USE_ALTITUDE = false
const val DEFAULT_ALTITUDE = 0.0

const val DEFAULT_USE_RANDOMIZE = false
const val DEFAULT_RANDOMIZE_RADIUS = 0.0

const val DEFAULT_USE_VERTICAL_ACCURACY = false
const val DEFAULT_VERTICAL_ACCURACY = 0.0f

const val DEFAULT_USE_MEAN_SEA_LEVEL = false
const val DEFAULT_MEAN_SEA_LEVEL = 0.0

const val DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY = false
const val DEFAULT_MEAN_SEA_LEVEL_ACCURACY = 0.0f

const val DEFAULT_USE_SPEED = false
const val DEFAULT_SPEED = 0.0f

const val DEFAULT_USE_SPEED_ACCURACY = false
const val DEFAULT_SPEED_ACCURACY = 0.0f

const val DEFAULT_HIDE_FAKE_LOCATION_TOAST = true

const val DEFAULT_ENABLE_BROADCAST_CONTROL = false
const val DEFAULT_LANGUAGE_TAG = ""
const val DEFAULT_BAIDU_MAP_AK = ""
const val DEFAULT_MAP_PROVIDER = "osmdroid"
const val DEFAULT_OSM_TILE_SERVER = "openstreetmap"
const val DEFAULT_AMAP_WEB_KEY = ""
const val DEFAULT_AMAP_SECURITY_CODE = ""
const val DEFAULT_GOOGLE_MAPS_API_KEY = ""

const val DEFAULT_ENABLE_SYSTEM_HOOKS = false
const val DEFAULT_ENABLE_GLOBAL_HOOKS = false
const val DEFAULT_ENABLE_MOCK_PROVIDER = false
const val DEFAULT_ENABLE_ROOT_MODE = false
const val DEFAULT_LOCATION_MODE = "app_hook"
const val DEFAULT_MOCK_FLAG_POLICY = "hide_all"
const val DEFAULT_HOOK_INSTALL_POLICY = "immediate"

const val DEFAULT_GNSS_CONSTELLATION_PRESET = "auto_mixed"
const val DEFAULT_GNSS_SIGNAL_STRENGTH_PRESET = "normal"
const val DEFAULT_GNSS_VISIBLE_SATELLITES = 24
const val DEFAULT_GNSS_USED_SATELLITES = 18
const val DEFAULT_GNSS_JITTER_ENABLED = false
const val DEFAULT_GNSS_JITTER_ALGORITHM = "first_order_markov"
const val DEFAULT_GNSS_JITTER_RADIUS = 1.5
const val DEFAULT_GNSS_JITTER_SMOOTHING = 0.85
const val DEFAULT_GNSS_JITTER_DRIFT_SPEED = 0.3

const val DEFAULT_GLOBAL_CADENCE_MODE = "auto"
const val DEFAULT_GLOBAL_CADENCE_CHANNEL = "accelerometer"
const val DEFAULT_GLOBAL_CUSTOM_SPM = 165

// MATH & PHYS
const val PI = 3.14159265359
const val RADIUS_EARTH = 6378137.0 // Approximately Earth's radius in meters

// MAP SETTINGS
const val DEFAULT_MAP_ZOOM = 18.0
const val WORLD_MAP_ZOOM = 2.0
const val LOCATION_DETECTION_MAX_ATTEMPTS = 80
const val LOCATION_DETECTION_DELAY_MS = 100L
