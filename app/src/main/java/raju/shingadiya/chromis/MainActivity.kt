package raju.shingadiya.chromis

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import raju.shingadiya.chromis.engine.DDColorEngine
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : ComponentActivity() {

    private var colorEngine: DDColorEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        OpenCVLoader.initDebug()
        try {
            colorEngine = DDColorEngine(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    primary = Color(0xFF1A73E8),
                    onPrimary = Color.White,
                    surface = Color(0xFFFCFCFD),
                    onSurface = Color(0xFF1A1C1E),
                    surfaceVariant = Color(0xFFF1F3F5),
                    outline = Color(0xFFE0E0E0),
                ),
            ) {
                ChromisApp(colorEngine)
            }
        }
    }
}

data class ColorizedImage(
    val original: Bitmap,
    val colorized: Bitmap,
    val timestamp: Long = System.currentTimeMillis(),
)

@Composable
fun ChromisApp(engine: DDColorEngine?) {
    var currentScreen by remember { mutableStateOf("home") }
    var activeImage by remember { mutableStateOf<ColorizedImage?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<ColorizedImage>() }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDemo by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val loaded = loadHistoryFromDisk(context)
            withContext(Dispatchers.Main) {
                history.addAll(loaded)
            }
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.ARGB_8888, true)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }

                val item = ColorizedImage(bitmap, bitmap)
                activeImage = item
                isProcessing = true
                currentScreen = "image"

                scope.launch(Dispatchers.Default) {
                    try {
                        val result = engine?.colorize(bitmap)
                        withContext(Dispatchers.Main) {
                            if (result != null) {
                                val final = ColorizedImage(bitmap, result)
                                withContext(Dispatchers.IO) {
                                    saveImagePair(context, final)
                                }
                                history.add(0, final)
                                activeImage = final
                            } else {
                                errorMessage = "Model not loaded.\n\nMake sure ddcolor-tiny-fp16.onnx is in assets."
                            }
                            isProcessing = false
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            val sw = StringWriter()
                            e.printStackTrace(PrintWriter(sw))
                            errorMessage = "${e.message}\n\n${sw.toString()}"
                            isProcessing = false
                        }
                    }
                }
            } catch (e: Exception) {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                errorMessage = "${e.message}\n\n${sw.toString()}"
            }
        }
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            fadeIn(tween(200)) togetherWith fadeOut(tween(200))
        },
        label = "screen",
    ) { screen ->
        when (screen) {
            "home" -> HomeScreen(
                history = history,
                onPickImage = { imageLauncher.launch("image/*") },
                onImageClick = { item ->
                    activeImage = item
                    currentScreen = "image"
                },
                onImageDelete = { item ->
                    deleteImagePair(context, item.timestamp)
                    history.remove(item)
                },
            )
            "image" -> ImageScreen(
                image = activeImage,
                isProcessing = isProcessing,
                onBack = { currentScreen = "home" },
                onPickAnother = { imageLauncher.launch("image/*") },
                onError = { errorMessage = it },
            )
        }
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color(0xFFFF6B6B),
            textContentColor = Color(0xFFCCCCCC),
            title = { Text("Error", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    text = errorMessage!!,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFCCCCCC),
                )
            },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("OK", color = Color(0xFF00BFA5), fontWeight = FontWeight.Medium)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    history: List<ColorizedImage>,
    onPickImage: () -> Unit,
    onImageClick: (ColorizedImage) -> Unit,
    onImageDelete: (ColorizedImage) -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<ColorizedImage?>(null) }

    Scaffold(
        containerColor = Color(0xFFFCFCFD),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Chromis",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFCFCFD),
                    titleContentColor = Color(0xFF1A1C1E),
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onPickImage,
                containerColor = Color(0xFF1A73E8),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(64.dp)
                    .shadow(8.dp, CircleShape),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Pick Image",
                    modifier = Modifier.size(28.dp),
                )
            }
        },
    ) { padding ->
        if (history.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                Text(
                    "See what Chromis can do",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1A1C1E),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Pick a photo below to try it yourself",
                    fontSize = 13.sp,
                    color = Color(0xFF9AA0A6),
                )
                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DemoCard(
                        label = "Original",
                        imageRes = R.drawable.demo_original,
                    )
                    DemoCard(
                        label = "Colourized",
                        imageRes = R.drawable.demo_colorized,
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = Color(0xFFD0D0D0),
                    modifier = Modifier.size(40.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Tap + to get started",
                    fontSize = 13.sp,
                    color = Color(0xFF9AA0A6),
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp,
            ) {
                items(history, key = { it.timestamp }) { item ->
                    val ratio = item.original.width.toFloat() / item.original.height.toFloat()
                    val displayHeight = (200 / ratio).coerceIn(180f, 320f).dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(displayHeight)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF1F3F5))
                            .combinedClickable(
                                onClick = { onImageClick(item) },
                                onLongClick = { deleteTarget = item },
                            ),
                    ) {
                        Image(
                            bitmap = item.colorized.asImageBitmap(),
                            contentDescription = "Colorized",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFCCCCCC),
            title = { Text("Delete image?", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text("This cannot be undone.", fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget?.let { onImageDelete(it) }
                    deleteTarget = null
                }) {
                    Text("Delete", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", color = Color(0xFF00BFA5), fontWeight = FontWeight.Medium)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageScreen(
    image: ColorizedImage?,
    isProcessing: Boolean,
    onBack: () -> Unit,
    onPickAnother: () -> Unit,
    onError: (String) -> Unit,
) {
    var showColorized by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (image == null) {
        onBack()
        return
    }

    Scaffold(
        containerColor = Color(0xFF1A1C1E),
        topBar = {
            TopAppBar(
                title = {
                    if (!isProcessing) {
                        Text(
                            "Hold to compare",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1C1E),
                ),
            )
        },
        bottomBar = {
            if (!isProcessing) {
                Surface(
                    color = Color(0xFF1A1C1E),
                    modifier = Modifier.navigationBarsPadding(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = {
                                isSaving = true
                                saveToDownloads(context, image.colorized) { success ->
                                    isSaving = false
                                    Toast.makeText(
                                        context,
                                        if (success) "Saved to Downloads" else "Save failed",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            enabled = !isSaving,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF1A1C1E),
                            ),
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF1A1C1E),
                                )
                            } else {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Button(
                            onClick = {
                                shareImage(context, image.colorized)
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1A73E8),
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF1A1C1E)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = (if (isProcessing || !showColorized) image.original else image.colorized).asImageBitmap(),
                contentDescription = if (isProcessing || !showColorized) "Original" else "Colorized",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .then(
                        if (!isProcessing) {
                            Modifier.pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    showColorized = false
                                    waitForUpOrCancellation()
                                    showColorized = true
                                }
                            }
                        } else Modifier
                    ),
                contentScale = ContentScale.Fit,
            )

            if (isProcessing) {
                val composition by rememberLottieComposition(LottieCompositionSpec.Asset("painting_loaders.json"))
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .navigationBarsPadding()
                        .padding(bottom = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LottieAnimation(
                        composition = composition,
                        iterations = LottieConstants.IterateForever,
                        reverseOnRepeat = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Colorizing...",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

private fun saveToDownloads(context: Context, bitmap: Bitmap, onResult: (Boolean) -> Unit) {
    try {
        val resolver = context.contentResolver
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "chromis_${System.currentTimeMillis()}.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { os ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
            }
            onResult(true)
        } else {
            onResult(false)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        onResult(false)
    }
}

private fun shareImage(context: Context, bitmap: Bitmap) {
    try {
        val file = File(context.cacheDir, "chromis_share.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share colorized photo"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun getImagesDir(context: Context): File {
    val dir = File(context.filesDir, "chromis")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

private fun saveImagePair(context: Context, item: ColorizedImage) {
    val dir = getImagesDir(context)
    val orig = File(dir, "${item.timestamp}_original.png")
    val color = File(dir, "${item.timestamp}_colorized.png")
    FileOutputStream(orig).use { item.original.compress(Bitmap.CompressFormat.PNG, 100, it) }
    FileOutputStream(color).use { item.colorized.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

private fun deleteImagePair(context: Context, id: Long) {
    val dir = getImagesDir(context)
    File(dir, "${id}_original.png").delete()
    File(dir, "${id}_colorized.png").delete()
}

private fun loadHistoryFromDisk(context: Context): List<ColorizedImage> {
    val dir = getImagesDir(context)
    val files = dir.listFiles() ?: return emptyList()
    val timestamps = files
        .mapNotNull { it.nameWithoutExtension.substringBeforeLast("_").toLongOrNull() }
        .distinct()
        .sortedDescending()

    return timestamps.mapNotNull { ts ->
        val origFile = File(dir, "${ts}_original.png")
        val colorFile = File(dir, "${ts}_colorized.png")
        if (origFile.exists() && colorFile.exists()) {
            val orig = android.graphics.BitmapFactory.decodeFile(origFile.absolutePath)
            val color = android.graphics.BitmapFactory.decodeFile(colorFile.absolutePath)
            if (orig != null && color != null) ColorizedImage(orig, color, ts) else null
        } else null
    }
}

@Composable
private fun DemoCard(label: String, imageRes: Int) {
    Column {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF5F6368),
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFF1F3F5)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = label,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.FillWidth,
            )
        }
    }
}
