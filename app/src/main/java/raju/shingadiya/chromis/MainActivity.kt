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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
    var currentScreen by remember { mutableStateOf<String>("home") }
    var activeImage by remember { mutableStateOf<ColorizedImage?>(null) }
    val history = remember { mutableStateListOf<ColorizedImage>() }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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

                val result = engine?.colorize(bitmap)
                if (result != null) {
                    val item = ColorizedImage(bitmap, result)
                    history.add(0, item)
                    activeImage = item
                    currentScreen = "image"
                } else {
                    errorMessage = "Model not loaded.\n\nMake sure ddcolor-tiny-fp16.onnx is in assets."
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
            )
            "image" -> ImageScreen(
                image = activeImage,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    history: List<ColorizedImage>,
    onPickImage: () -> Unit,
    onImageClick: (ColorizedImage) -> Unit,
) {
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPickImage,
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F3F5)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = Color(0xFF9AA0A6),
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Pick a black & white photo",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF5F6368),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Chromis will colourize it instantly",
                        fontSize = 13.sp,
                        color = Color(0xFF9AA0A6),
                    )
                }
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
                            .clickable { onImageClick(item) },
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageScreen(
    image: ColorizedImage?,
    onBack: () -> Unit,
    onPickAnother: () -> Unit,
    onError: (String) -> Unit,
) {
    var showColorized by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var isSharing by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (image == null) {
        onBack()
        return
    }

    Scaffold(
        containerColor = Color(0xFF1A1C1E),
        topBar = {
            TopAppBar(
                title = { },
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
                    OutlinedButton(
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
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF1A73E8),
                            )
                        } else {
                            Text("Save", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            isSharing = true
                            shareImage(context, image.colorized) {
                                isSharing = false
                            }
                        },
                        enabled = !isSharing,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (isSharing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF1A73E8),
                            )
                        } else {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share", fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
                bitmap = (if (showColorized) image.colorized else image.original).asImageBitmap(),
                contentDescription = if (showColorized) "Colorized" else "Original",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            showColorized = false
                            waitForUpOrCancellation()
                            showColorized = true
                        }
                    },
                contentScale = ContentScale.Fit,
            )

            val label = if (showColorized) "Colorized" else "Original (hold to compare)"
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(999.dp),
                color = Color(0xCC000000),
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
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

private fun shareImage(context: Context, bitmap: Bitmap, onDone: () -> Unit) {
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
        context.startActivity(Intent.createChooser(intent, "Share colourized photo"))
} catch (e: Exception) {
    e.printStackTrace()
}
onDone()
}
