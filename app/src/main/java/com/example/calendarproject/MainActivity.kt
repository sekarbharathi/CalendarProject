package com.example.calendarproject

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

import android.widget.Toast
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.navigation.compose.currentBackStackEntryAsState
import java.text.SimpleDateFormat
import java.util.*
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import coil.compose.rememberAsyncImagePainter
import coil.compose.rememberImagePainter
import kotlinx.coroutines.delay
import java.io.File

// Main Activity
class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
//                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
//            }
//        }

        createNotificationChannel(this)

        setContent {
            CalendarNotesApp(mainViewModel)
        }

    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "weather_alerts"
            val channelName = "Weather Alerts"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Channel for weather alerts"
            }

            // Register the channel with the system
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

// Animated Splash Screen
@Composable
fun AnimatedSplashScreen(navigateToMain: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000) // Fade-in duration
    )

    // Zoom animation
    val scaleAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1.5f else 1f,
        animationSpec = keyframes {
            durationMillis = 2000 // Total duration of the animation
            1.3f at 1000 // Zoom in to 1.5x at 1000ms
            1f at 2000 // Zoom back to 1x at 2000ms
        }
    )

    LaunchedEffect(key1 = true) {
        startAnimation = true
        delay(3000) // Adjust the delay as needed
        navigateToMain()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.secondary),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo), // Replace with your logo
            contentDescription = "App Logo",
            modifier = Modifier
                .size(300.dp)
                .alpha(alphaAnim.value)
                .scale(scaleAnim.value) // Apply the zoom animation
        )
    }
}

// Navigation Setup
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarNotesApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    var showSplash by remember { mutableStateOf(true) }
    val context = LocalContext.current

    // Observe the current back stack entry
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    Log.d("Navigation", "Current Route: $currentRoute") // Log the current route

    // Request notification permission after splash screen
    LaunchedEffect(showSplash) {
        if (!showSplash) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(context as ComponentActivity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                }
            }
        }
    }

    if (showSplash) {
        AnimatedSplashScreen {
            showSplash = false
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Add the text
                            Text(
                                text = "Calendar Notes",
                            )
                        }
                    },
                    navigationIcon = {
                        // Show the back arrow if the current route starts with "eventDetails/"
                        if (currentRoute?.startsWith("eventDetails/") == true) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                NavHost(navController, startDestination = "calendar") {
                    composable("calendar") { CalendarScreen(navController, viewModel) }
                    composable("eventDetails/{date}") { backStackEntry ->
                        val date = backStackEntry.arguments?.getString("date")
                        date?.let { EventDetailsScreen(it, viewModel, navController) }
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarScreen(navController: NavController, viewModel: MainViewModel) {
    // State to track the current month and year
    var currentMonth by remember { mutableStateOf(Calendar.getInstance()) }

    // Fetch all events
    val allEvents by viewModel.getAllEvents().observeAsState(emptyList())

    // List of day names
    val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    // State for delete dialog
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedEvent by remember { mutableStateOf<Event?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Month and year display with arrow icons for navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                // Navigate to the previous month
                val newMonth = currentMonth.clone() as Calendar
                newMonth.add(Calendar.MONTH, -1)
                currentMonth = newMonth
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Previous Month")
            }
            Text(
                "${SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(currentMonth.time)}",
                style = MaterialTheme.typography.titleLarge
            )
            IconButton(onClick = {
                // Navigate to the next month
                val newMonth = currentMonth.clone() as Calendar
                newMonth.add(Calendar.MONTH, 1)
                currentMonth = newMonth
            }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Next Month")
            }
        }

        // Display day names
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            dayNames.forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Generate the list of days for the current month
        val daysList = remember(currentMonth) {
            val calendar = currentMonth.clone() as Calendar
            calendar.set(Calendar.DAY_OF_MONTH, 1) // Set to the first day of the month
            val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            val firstDayOfMonth = calendar.get(Calendar.DAY_OF_WEEK)

            // Create a list of days with empty strings for padding
            val days = mutableListOf<String>()
            for (i in 1 until firstDayOfMonth) {
                days.add("")
            }
            for (i in 1..daysInMonth) {
                days.add(i.toString())
            }
            days
        }

        // Calendar grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.weight(1f)
        ) {
            items(daysList.size) { index ->
                val day = daysList[index]
                if (day.isNotEmpty()) {
                    val clickedDate = currentMonth.clone() as Calendar
                    clickedDate.set(Calendar.DAY_OF_MONTH, day.toInt())
                    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(clickedDate.time)
                    val event = allEvents.find { it.date == date }

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable {
                                navController.navigate("eventDetails/$date")
                            }
                            .padding(4.dp)
                            .background(
                                if (event != null) Color.LightGray else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            )
                            .wrapContentSize(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(day, style = MaterialTheme.typography.bodyLarge)
                            if (event != null) {
                                Text("*", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.aspectRatio(1f))
                }
            }
        }

        // Display all events (including past events)
        val allEventsList = allEvents
        if (allEventsList.isNotEmpty()) {
            Text(
                "My Notes:",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(allEventsList.size) { index ->
                    val event = allEventsList[index]
                    Card(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clickable {
                                selectedEvent = event
                                showDeleteDialog = true
                            }
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Date: ${event.date}", style = MaterialTheme.typography.bodyLarge)
                            Text("Note: ${event.note}", style = MaterialTheme.typography.bodyMedium)
                            if (event.imageUri != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(event.imageUri),
                                    contentDescription = "Event Image",
                                    modifier = Modifier.fillMaxWidth().height(100.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Note") },
            text = { Text("Are you sure you want to delete this note?") },
            confirmButton = {
                Button(
                    onClick = {
                        selectedEvent?.let { event ->
                            viewModel.deleteEvent(event)
                            showDeleteDialog = false
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Event Details Screen
@Composable
fun EventDetailsScreen(date: String, viewModel: MainViewModel, navController: NavController) {
    var note by remember { mutableStateOf(TextFieldValue()) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    // Launcher for picking images from the gallery
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val filePath = saveImageToInternalStorage(context, it)
            imageUri = Uri.parse(filePath)
        }
    }

    // Temporary file to store the captured image
    val photoFile = remember { File.createTempFile("IMG_", ".jpg", context.externalCacheDir) }
    val photoUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile) }

    // Launcher for taking photos with the camera
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            // Image captured successfully, force recomposition
            imageUri = photoUri
        } else {
            // Handle failure
            Log.e("Camera", "Failed to capture image")
        }
    }

    // Launcher for requesting camera permissions
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            // Permission granted, launch the camera
            cameraLauncher.launch(photoUri)
        } else {
            // Permission denied, show a message or handle it
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    val event by viewModel.getEventByDate(date).observeAsState()
    val weatherForecast by viewModel.weatherForecast.observeAsState()

    LaunchedEffect(event) {
        event?.let {
            note = TextFieldValue(it.note)
            it.imageUri?.let { uri ->
                imageUri = Uri.parse(uri)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Events for $date", style = MaterialTheme.typography.titleLarge)

        // Text Field with Gallery and Camera buttons inside
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Notes") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            leadingIcon = {
                // Display the selected image inside the text field
                imageUri?.let { uri ->
                    Image(
                        painter = rememberAsyncImagePainter(uri),
                        contentDescription = "Selected Image",
                        modifier = Modifier
                            .size(40.dp) // Set the size of the image
                            .padding(end = 8.dp) // Add padding between the image and the text
                    )
                }
            },
            trailingIcon = {
                Row(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 8.dp), // Add padding to the left and right of the buttons
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Gallery Button
                    IconButton(
                        onClick = { imagePicker.launch("image/*") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.gallery), // Replace with your gallery icon
                            contentDescription = "Gallery"
                        )
                    }

                    // Camera Button
                    IconButton(
                        onClick = {
                            // Check if camera permission is granted
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                // Permission already granted, launch the camera
                                cameraLauncher.launch(photoUri)
                            } else {
                                // Request camera permission
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.camera), // Replace with your camera icon
                            contentDescription = "Camera"
                        )
                    }
                }
            }
        )

        // Remove Image button (wrapped and above Save Note button)
        imageUri?.let {
            Button(
                onClick = { imageUri = null },
                modifier = Modifier
                    .wrapContentWidth() // Wrap the button width
                    .align(Alignment.CenterHorizontally) // Center the button horizontally
                    .padding(bottom = 8.dp) // Add padding below the button
            ) {
                Text("Remove Image")
            }
        }

        // Save Note button (smaller size)
        Button(
            onClick = {
                viewModel.updateEvent(date, note.text, imageUri?.toString())
            },
            modifier = Modifier
                .wrapContentSize() // Make the button wrap its content
                .align(Alignment.CenterHorizontally) // Center the button horizontally
                .padding(bottom = 8.dp) // Add padding below the button
        ) {
            Text("Save Note")
        }

        // Weather button (smaller size)
        Button(
            onClick = { viewModel.fetchWeatherData(context, "Oulu", date) },
            modifier = Modifier
                .wrapContentSize() // Make the button wrap its content
                .align(Alignment.CenterHorizontally) // Center the button horizontally
        ) {
            Text("Weather")
        }

        // Display the weather forecast
        event?.weatherForecast?.let { forecast ->
            Text(
                text = forecast,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

// Save the image to internal storage and return the file path
fun saveImageToInternalStorage(context: Context, uri: Uri): String {
    val directory = File(context.filesDir, "images")
    if (!directory.exists()) directory.mkdir() // Ensure directory exists

    val file = File(directory, "image_${System.currentTimeMillis()}.jpg")
    val inputStream = context.contentResolver.openInputStream(uri)
    inputStream?.use { input ->
        file.outputStream().use { output ->
            input.copyTo(output)
        }
    }

    val fileUri = file.toURI().toString() // Convert file path to a valid URI
    Log.d("Database", "Image saved to: $fileUri")
    return fileUri
}

// ViewModel with Room DB and Weather API
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EventRepository(application)
    private val _weatherForecast = MutableLiveData<String>()
    val weatherForecast: LiveData<String> get() = _weatherForecast

    fun addEvent(date: String, note: String, imageUri: String?) {
        viewModelScope.launch {
            Log.d("Database", "Saving event: date=$date, note=$note, imageUri=$imageUri")
            val event = Event(date = date, note = note, imageUri = imageUri)
            repository.insertEvent(event)
        }
    }

    fun updateEvent(date: String, note: String, imageUri: String?) {
        viewModelScope.launch {
            val existingEvent = repository.getEventByDate(date).value
            if (existingEvent != null) {
                // Update the existing event
                val updatedEvent = existingEvent.copy(note = note, imageUri = imageUri)
                repository.insertEvent(updatedEvent)  // This will update the event in the database
            } else {
                // If no event exists, create a new one
                val newEvent = Event(date = date, note = note, imageUri = imageUri)
                repository.insertEvent(newEvent)
            }
        }
    }

    fun deleteEvent(event: Event) {
        viewModelScope.launch {
            repository.deleteEvent(event)
        }
    }

    fun getAllEvents(): LiveData<List<Event>> {
        return repository.getAllEvents()
    }

    fun getEventByDate(date: String): LiveData<Event?> {
        Log.d("Database", "Fetching event for date: $date")
        return repository.getEventByDate(date)
    }

    fun fetchWeatherData(context: Context, city: String, selectedDate: String) {
        viewModelScope.launch {
            try {
                val response = WeatherServiceImpl.create().getWeatherForecast(city, "29226d19ee679f93c9f041c83dbf5105")
                val forecast = processForecast(response, selectedDate)
                _weatherForecast.value = forecast

                // Store the weather forecast in the database
                repository.updateWeatherForecast(selectedDate, forecast)

                // Trigger notification if it might rain
                if (forecast.contains("rain", ignoreCase = true) || forecast.contains("snow", ignoreCase = true)) {
                    val alertMessage = if (forecast.contains("rain", ignoreCase = true)) {
                        "It might rain on $selectedDate."
                    } else {
                        "It might snow on $selectedDate."
                    }
                    sendNotification(context, alertMessage)
                }
            } catch (e: Exception) {
                Log.e("Weather", "Error fetching data", e)
                _weatherForecast.value = "Error fetching weather data"
            }
        }
    }

    private fun processForecast(response: WeatherForecastResponse, selectedDate: String): String {
        val forecastList = response.list
        val cityName = response.city.name
        val country = response.city.country

        // Filter forecast items for the selected date
        val filteredForecast = forecastList.filter { it.dt_txt.startsWith(selectedDate) }

        if (filteredForecast.isEmpty()) {
            return "No weather data available for $selectedDate"
        }

        val forecastString = StringBuilder()
        forecastString.append("Weather forecast for $cityName, $country on $selectedDate:\n\n")

        filteredForecast.forEach { item ->
            val time = item.dt_txt.substring(11, 16) // Extract time (HH:mm)
            val weatherDescription = item.weather.firstOrNull()?.description ?: "No data"
            val temperature = item.main.temp
            forecastString.append("$time: $weatherDescription, Temp: $temperature°C\n")
        }

        return forecastString.toString()
    }
}

// Database Entities and DAO
@Entity(tableName = "events")
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val note: String,
    val imageUri: String? = null, // New column
    val weatherForecast: String? = null
)

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: Event)

    @androidx.room.Query("SELECT * FROM events WHERE date = :date")
    fun getEventByDate(date: String): LiveData<Event?>

    @androidx.room.Query("SELECT * FROM events")
    fun getAllEvents(): LiveData<List<Event>>

    @androidx.room.Query("UPDATE events SET weatherForecast = :forecast WHERE date = :date")
    suspend fun updateWeatherForecast(date: String, forecast: String)

    @Delete
    suspend fun delete(event: Event)
}

@Database(entities = [Event::class], version = 3) // Incremented version number
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "events.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3) // Add both migrations
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Migration for database version 1 to 2
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE events ADD COLUMN imageUri TEXT")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE events ADD COLUMN weatherForecast TEXT")
    }
}

// Repository
class EventRepository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.eventDao()

    suspend fun insertEvent(event: Event) = dao.insert(event)

    suspend fun deleteEvent(event: Event) = dao.delete(event)

    fun getEventByDate(date: String): LiveData<Event?> = dao.getEventByDate(date)

    fun getAllEvents(): LiveData<List<Event>> = dao.getAllEvents()

    suspend fun updateWeatherForecast(date: String, forecast: String) {
        dao.updateWeatherForecast(date, forecast)
    }
}

data class WeatherForecastResponse(
    val list: List<ForecastItem>,
    val city: City
)

data class ForecastItem(
    val dt: Long, // Timestamp
    val main: Main,
    val weather: List<Weather>,
    val dt_txt: String // Date and time in text format (e.g., "2023-03-08 12:00:00")
)

data class City(
    val name: String,
    val country: String
)

data class Main(
    val temp: Float,
    val humidity: Int
)

data class Weather(
    val description: String
)

interface WeatherService {
    @GET("forecast")
    suspend fun getWeatherForecast(
        @Query("q") city: String,
        @Query("appid") apiKey: String
    ): WeatherForecastResponse
}

object WeatherServiceImpl {
    private const val BASE_URL = "https://api.openweathermap.org/data/2.5/"

    fun create(): WeatherService {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(WeatherService::class.java)
    }
}

// Permissions Handling
fun checkPermissions(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}

fun requestPermissions(activity: ComponentActivity) {
    ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
}

fun sendNotification(context: Context, message: String) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "weather_alerts"

    // Build the notification
    val notification = NotificationCompat.Builder(context, channelId)
        .setContentTitle("Weather Alert")
        .setContentText(message)
        .setSmallIcon(R.drawable.weather_alert_icon_vector) // Ensure this drawable exists
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    // Show the notification
    notificationManager.notify(1, notification)
}