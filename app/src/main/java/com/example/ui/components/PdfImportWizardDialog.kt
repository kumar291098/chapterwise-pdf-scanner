package com.example

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.utils.GeminiService
import com.example.utils.PdfExtractor
import kotlinx.coroutines.launch

@Composable
fun PdfImportWizardDialog(
    meta: PdfExtractor.PdfMeta,
    onDismiss: () -> Unit,
    onImportComplete: (List<String>, String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedPages by remember { mutableStateOf(setOf(0)) } // Default selection is first page
    var generateAiNotes by remember { mutableStateOf(true) }
    var saveAsImages by remember { mutableStateOf(true) }
    var customPrompt by remember {
        mutableStateOf("Extract clear study summaries from these document pages. Highlight crucial core definitions, formulas, action equations, lists, and summary takeaways in clear markdown.")
    }

    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "AI PDF Study Extractor",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // PDF details card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = Color(0xFFC0392B),
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = meta.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${meta.pageCount} page(s) detected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (isProcessing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontStyle = FontStyle.Italic
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Page selection block
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                             Text(
                                text = "Select Pages to Catalog (${selectedPages.size} selected)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "All",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        selectedPages = (0 until meta.pageCount).toSet()
                                    }
                                )
                                Text(
                                    text = "None",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clickable {
                                        selectedPages = emptySet()
                                    }
                                )
                            }
                        }

                        // standard Grid of selectable page numbers which cannot break compilations
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 42.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 130.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(meta.pageCount) { i ->
                                val isSelected = selectedPages.contains(i)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                        .clickable {
                                            selectedPages = if (isSelected) {
                                                selectedPages - i
                                            } else {
                                                selectedPages + i
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (i + 1).toString(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // AI note options
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = generateAiNotes,
                                onCheckedChange = { generateAiNotes = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.testTag("ai_notes_checkbox")
                            )
                            Column {
                                Text(
                                    text = "🪄 Generate AI Study Notes",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Gemini analyzes selected pages to extract revision bullet points.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (generateAiNotes) {
                            OutlinedTextField(
                                value = customPrompt,
                                onValueChange = { customPrompt = it },
                                label = { Text("Study Focus Directions") },
                                textStyle = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("prompt_instructions_input"),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    cursorColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    // Append pages scans
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = saveAsImages,
                            onCheckedChange = { saveAsImages = it },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                        Column {
                            Text(
                                text = "🖼️ Save pages as visual chapter scans",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Appends page graphics into chapter snapshots to export again in final book PDF.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isProcessing) {
                Button(
                    onClick = {
                        if (selectedPages.isEmpty()) {
                            Toast.makeText(context, "Please select at least one page to import.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isProcessing = true
                        statusText = "Rendering selected PDF pages locally..."

                        coroutineScope.launch {
                            try {
                                val pageIndices = selectedPages.sorted()
                                val rendered = PdfExtractor.renderPdfPages(context, meta.tempFile, pageIndices)

                                if (rendered.isEmpty()) {
                                    Toast.makeText(context, "Failed to render PDF pages locally.", Toast.LENGTH_LONG).show()
                                    isProcessing = false
                                    return@launch
                                }

                                var aiResultNotes = ""
                                if (generateAiNotes) {
                                    statusText = "Gemini AI is analyzing scans & compiling study summaries..."
                                    val bitmaps = rendered.map { it.bitmap }
                                    aiResultNotes = GeminiService.generateNotesFromPages(bitmaps, customPrompt)
                                }

                                val paths = if (saveAsImages) {
                                    rendered.map { it.filePath }
                                } else {
                                    emptyList()
                                }

                                onImportComplete(paths, aiResultNotes)

                                // Clean up temp file
                                if (meta.tempFile.exists()) {
                                    meta.tempFile.delete()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(context, "Failed to complete PDF import: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            } finally {
                                isProcessing = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.testTag("pdf_wizard_confirm_button")
                ) {
                    Text("Import & Run AI", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            if (!isProcessing) {
                TextButton(
                    onClick = {
                        if (meta.tempFile.exists()) {
                            meta.tempFile.delete()
                        }
                        onDismiss()
                    }
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp)
    )
}
