package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Chapter
import com.example.ui.BookViewModel
import com.example.ui.theme.CreamBackground
import com.example.ui.theme.DarkCharcoal
import com.example.ui.theme.DesertSand
import com.example.ui.theme.OliveGreen
import com.example.ui.theme.SoftSage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagePagesDialog(
    chapter: Chapter,
    onDismiss: () -> Unit,
    viewModel: BookViewModel
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Adjust Chapter Pages",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = DarkCharcoal
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Rotate pages clockwise or rearrange their order to assemble the final PDF exactly how you want it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SoftSage
                )

                if (chapter.scannedImagePaths.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No pages to manage.", style = MaterialTheme.typography.bodyMedium, color = SoftSage)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(chapter.scannedImagePaths) { index, imgPath ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, DesertSand),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.6f)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Thumbnail
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                            .background(Color.LightGray)
                                    ) {
                                        AsyncImage(
                                            model = imgPath,
                                            contentDescription = "Page ${index + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(topEnd = 8.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Page ${index + 1}",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Action buttons for this page
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFFAFAFA))
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Move Left
                                        IconButton(
                                            onClick = {
                                                if (index > 0) {
                                                    val newList = chapter.scannedImagePaths.toMutableList()
                                                    val temp = newList[index]
                                                    newList[index] = newList[index - 1]
                                                    newList[index - 1] = temp
                                                    viewModel.reorderPages(chapter, newList)
                                                }
                                            },
                                            enabled = index > 0,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowBack,
                                                contentDescription = "Move Left",
                                                modifier = Modifier.size(14.dp),
                                                tint = if (index > 0) OliveGreen else Color.Gray.copy(alpha = 0.4f)
                                            )
                                        }

                                        // Rotate
                                        IconButton(
                                            onClick = {
                                                viewModel.rotatePage(chapter, imgPath)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RotateRight,
                                                contentDescription = "Rotate 90°",
                                                modifier = Modifier.size(14.dp),
                                                tint = OliveGreen
                                            )
                                        }

                                        // Move Right
                                        IconButton(
                                            onClick = {
                                                if (index < chapter.scannedImagePaths.size - 1) {
                                                    val newList = chapter.scannedImagePaths.toMutableList()
                                                    val temp = newList[index]
                                                    newList[index] = newList[index + 1]
                                                    newList[index + 1] = temp
                                                    viewModel.reorderPages(chapter, newList)
                                                }
                                            },
                                            enabled = index < chapter.scannedImagePaths.size - 1,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowForward,
                                                contentDescription = "Move Right",
                                                modifier = Modifier.size(14.dp),
                                                tint = if (index < chapter.scannedImagePaths.size - 1) OliveGreen else Color.Gray.copy(alpha = 0.4f)
                                            )
                                        }

                                        // Delete
                                        IconButton(
                                            onClick = {
                                                val newList = chapter.scannedImagePaths.filter { it != imgPath }
                                                viewModel.reorderPages(chapter, newList)
                                                val f = File(imgPath)
                                                if (f.exists()) f.delete()
                                                Toast.makeText(context, "Page deleted", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Page",
                                                modifier = Modifier.size(14.dp),
                                                tint = Color.Red.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = OliveGreen),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = CreamBackground,
        shape = RoundedCornerShape(28.dp)
    )
}
