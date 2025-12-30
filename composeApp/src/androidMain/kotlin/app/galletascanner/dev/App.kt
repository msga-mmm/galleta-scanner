package app.galletascanner.dev

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

@Composable
actual fun App() {
    val context = LocalContext.current
    var capturedImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorDetails by remember { mutableStateOf<String?>(null) }
    var pdfFile by remember { mutableStateOf<File?>(null) }

    val reportError: (Throwable) -> Unit = { throwable ->
        errorDetails = throwable.stackTraceToString()
        statusMessage = throwable.message ?: context.getString(R.string.unexpected_error)
    }

    val scanner =
        remember {
            val options =
                GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setPageLimit(1)
                    .setResultFormats(
                        GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                    )
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()
            GmsDocumentScanning.getClient(options)
        }

    val scanLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            try {
                if (result.resultCode != Activity.RESULT_OK) {
                    statusMessage = context.getString(R.string.scan_cancelled)
                    return@rememberLauncherForActivityResult
                }

                val scanResult =
                    GmsDocumentScanningResult.fromActivityResultIntent(
                        result.data,
                    )
                val page = scanResult?.pages?.firstOrNull()
                if (page == null) {
                    statusMessage = context.getString(R.string.no_scanned_pages)
                    return@rememberLauncherForActivityResult
                }

                val bitmap = loadBitmapFromUri(context, page.imageUri)
                if (bitmap == null) {
                    statusMessage = context.getString(R.string.unable_load_scanned_image)
                    return@rememberLauncherForActivityResult
                }

                capturedImage = bitmap.toBlackAndWhite().asImageBitmap()
                statusMessage = null
                errorDetails = null
                pdfFile = null
            } catch (t: Throwable) {
                reportError(t)
            }
        }

    val startScan: () -> Unit = {
        try {
            val activity = context.findActivity()
            if (activity == null) {
                statusMessage = context.getString(R.string.unable_start_scanner)
            } else {
                scanner.getStartScanIntent(activity)
                    .addOnSuccessListener { intentSender ->
                        val request =
                            IntentSenderRequest.Builder(
                                intentSender,
                            ).build()
                        scanLauncher.launch(request)
                    }
                    .addOnFailureListener { error ->
                        reportError(error)
                    }
            }
        } catch (t: Throwable) {
            reportError(t)
        }
    }

    val sharePdf: () -> Unit = sharePdf@{
        try {
            val image = capturedImage
            if (image == null) {
                statusMessage = context.getString(R.string.no_scan_yet)
                return@sharePdf
            }

            if (pdfFile == null) {
                pdfFile = createPdfFromBitmap(context, image.asAndroidBitmap())
            }

            val file = pdfFile
            if (file == null) {
                statusMessage = context.getString(R.string.pdf_create_failed)
                return@sharePdf
            }

            val uri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )

            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            val activity = context.findActivity()
            if (activity == null) {
                statusMessage = context.getString(R.string.unable_start_share)
                return@sharePdf
            }

            val chooser =
                Intent.createChooser(
                    shareIntent,
                    context.getString(R.string.share_pdf_chooser),
                )
            activity.startActivity(chooser)
            statusMessage = null
        } catch (t: Throwable) {
            reportError(t)
        }
    }

    MaterialTheme(colorScheme = cookieColorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .systemBarsPadding()
                        .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.scan_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                Button(onClick = startScan) {
                    Text(stringResource(R.string.scan_button))
                }

                Button(
                    onClick = sharePdf,
                    enabled = capturedImage != null,
                ) {
                    Text(stringResource(R.string.share_pdf_button))
                }

                if (errorDetails != null) {
                    ErrorPanel(errorDetails!!)
                }

                if (capturedImage != null) {
                    val image = capturedImage!!
                    val aspectRatio = image.width.toFloat() / image.height.toFloat()
                    Image(
                        bitmap = image,
                        contentDescription =
                            stringResource(
                                R.string.scanned_preview_desc,
                            ),
                        contentScale = ContentScale.FillWidth,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(aspectRatio)
                                .clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = statusMessage ?: stringResource(R.string.no_scan_yet),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (capturedImage != null && statusMessage != null) {
                    Text(
                        text = statusMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }
        current = current.baseContext
    }
    return null
}

private fun loadBitmapFromUri(
    context: Context,
    uri: Uri,
): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
                decoder.isMutableRequired = true
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }
    } catch (e: Exception) {
        null
    }
}

private fun Bitmap.toBlackAndWhite(): Bitmap {
    val width = width
    val height = height
    val totalPixels = width * height

    val pixels = IntArray(totalPixels)
    val grays = IntArray(totalPixels)
    getPixels(pixels, 0, width, 0, 0, width, height)

    val histogram = IntArray(256)
    for (i in pixels.indices) {
        val color = pixels[i]
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val gray =
            (0.299 * r + 0.587 * g + 0.114 * b).roundToInt().coerceIn(
                0,
                255,
            )
        grays[i] = gray
        histogram[gray]++
    }

    var sum = 0.0
    for (t in 0..255) {
        sum += t * histogram[t].toDouble()
    }

    var sumB = 0.0
    var weightB = 0.0
    var maxVariance = -1.0
    var threshold = 128
    val total = totalPixels.toDouble()

    for (t in 0..255) {
        weightB += histogram[t].toDouble()
        if (weightB == 0.0) {
            continue
        }
        val weightF = total - weightB
        if (weightF == 0.0) {
            break
        }

        sumB += t * histogram[t].toDouble()
        val meanB = sumB / weightB
        val meanF = (sum - sumB) / weightF
        val variance = weightB * weightF * (meanB - meanF) * (meanB - meanF)

        if (variance > maxVariance) {
            maxVariance = variance
            threshold = t
        }
    }

    val outputPixels = IntArray(totalPixels)
    for (i in grays.indices) {
        val value = if (grays[i] > threshold) 255 else 0
        outputPixels[i] = (0xFF shl 24) or (value shl 16) or (value shl 8) or value
    }

    val output = createBitmap(width, height)
    output.setPixels(outputPixels, 0, width, 0, 0, width, height)
    return output
}

@Composable
private fun ErrorPanel(details: String) {
    Text(
        text = stringResource(R.string.error_with_details, details),
        style = MaterialTheme.typography.bodySmall,
    )
}

private val cookieColorScheme =
    lightColorScheme(
        primary = Color(0xFFC98547),
        onPrimary = Color(0xFFF8EFE4),
        secondary = Color(0xFF8B5A2B),
        onSecondary = Color(0xFFF8EFE4),
        background = Color(0xFFFFF7EF),
        onBackground = Color(0xFF3B2A1A),
        surface = Color(0xFFFFF7EF),
        onSurface = Color(0xFF3B2A1A),
    )

private fun createPdfFromBitmap(
    context: Context,
    bitmap: Bitmap,
): File? {
    val pdfDocument = PdfDocument()
    return try {
        val pageInfo =
            PdfDocument.PageInfo.Builder(
                bitmap.width,
                bitmap.height,
                1,
            ).create()
        val page = pdfDocument.startPage(pageInfo)
        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
        pdfDocument.finishPage(page)

        val outputFile = File.createTempFile("scan_", ".pdf", context.cacheDir)
        FileOutputStream(outputFile).use { stream ->
            pdfDocument.writeTo(stream)
        }
        outputFile
    } catch (e: Exception) {
        null
    } finally {
        pdfDocument.close()
    }
}
