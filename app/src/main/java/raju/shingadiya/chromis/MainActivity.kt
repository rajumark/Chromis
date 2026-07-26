package raju.shingadiya.chromis

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import raju.shingadiya.chromis.engine.DDColorEngine
import raju.shingadiya.chromis.ui.theme.Accent
import raju.shingadiya.chromis.ui.theme.Primary
import raju.shingadiya.chromis.ui.theme.TextHint
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
            Log.e("Chromis", "Engine init failed", e)
        }

        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            var inputBitmap by remember { mutableStateOf<Bitmap?>(null) }
            var outputBitmap by remember { mutableStateOf<Bitmap?>(null) }
            var isProcessing by remember { mutableStateOf(false) }
            var errorMessage by remember { mutableStateOf<String?>(null) }

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
                        inputBitmap = bitmap
                        outputBitmap = null
                        isProcessing = true

                        scope.launch {
                            try {
                                val engine = colorEngine
                                if (engine == null) {
                                    errorMessage = "Model not loaded.\n\nPlace ddcolor-tiny-fp16.onnx in app/src/main/assets/"
                                    isProcessing = false
                                    return@launch
                                }
                                val result = withContext(Dispatchers.Default) {
                                    engine.colorize(bitmap)
                                }
                                outputBitmap = result
                            } catch (e: Exception) {
                                val sw = StringWriter()
                                e.printStackTrace(PrintWriter(sw))
                                errorMessage = "${e.message}\n\n${sw.toString()}"
                            } finally {
                                isProcessing = false
                            }
                        }
                    } catch (e: Exception) {
                        val sw = StringWriter()
                        e.printStackTrace(PrintWriter(sw))
                        errorMessage = "Failed to load image:\n${e.message}\n\n${sw.toString()}"
                    }
                }
            }

            ChromisScreen(
                inputBitmap = inputBitmap,
                outputBitmap = outputBitmap,
                isProcessing = isProcessing,
                errorMessage = errorMessage,
                onPickImage = { imageLauncher.launch("image/*") },
                onDismissError = { errorMessage = null },
            )
        }
    }
}

@Composable
fun ChromisScreen(
    inputBitmap: Bitmap?,
    outputBitmap: Bitmap?,
    isProcessing: Boolean,
    errorMessage: String?,
    onPickImage: () -> Unit,
    onDismissError: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF8FAFE),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (inputBitmap == null) {
                // INITIAL STATE: single center button
                PickImageButton(onClick = onPickImage)
            } else {
                // IMAGE LOADED: side by side + loading
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isProcessing) "Colorizing..." else "Done",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isProcessing) Color(0xFFFFA000) else Color(0xFF34A853),
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Input
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Original",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF5F6368),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFE8EEF6)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    bitmap = inputBitmap.asImageBitmap(),
                                    contentDescription = "Input",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }

                        // Output
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Colorized",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF5F6368),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFE8EEF6)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(40.dp),
                                        color = Accent,
                                        strokeWidth = 3.dp,
                                    )
                                } else if (outputBitmap != null) {
                                    Image(
                                        bitmap = outputBitmap.asImageBitmap(),
                                        contentDescription = "Output",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Fit,
                                    )
                                } else {
                                    Text(
                                        text = "Waiting...",
                                        color = TextHint,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Pick another button at bottom
                    PickAnotherButton(onClick = onPickImage)

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // Error dialog
            if (errorMessage != null) {
                ErrorDialog(
                    message = errorMessage,
                    onDismiss = onDismissError,
                )
            }
        }
    }
}

@Composable
private fun PickImageButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        ),
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Pick Image",
                tint = Color.White,
                modifier = Modifier.size(48.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Pick Image",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Primary,
        )
    }
}

@Composable
private fun PickAnotherButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Pick Another",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color(0xFFFF6B6B),
        textContentColor = Color(0xFFCCCCCC),
        title = {
            Text(
                text = "Error",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        },
        text = {
            Text(
                text = message,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = Color(0xFFCCCCCC),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "OK",
                    color = Accent,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
    )
}
