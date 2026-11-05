package com.example.budget.presentation.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.budget.domain.model.CategorySum
import com.example.budget.utils.CurrencyFormatter
import java.text.DateFormatSymbols

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val timeState by viewModel.timeState.collectAsState()
    val categorySums by viewModel.categorySums.collectAsState()

    val monthName = remember(timeState.month) {
        DateFormatSymbols().months[timeState.month]
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Month Picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.previousMonth() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Month")
                }
                Text(
                    text = "$monthName ${timeState.year}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { viewModel.nextMonth() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Month")
                }
            }

            if (categorySums.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No expenses recorded for this month.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                val totalSpend = categorySums.sumOf { it.sumAmount }

                // Donut Chart
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    DonutChart(categorySums = categorySums, totalSpend = totalSpend)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total Spend", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = CurrencyFormatter.formatToIDR(totalSpend),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Category List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(categorySums) { sum ->
                        CategorySumRow(
                            categorySum = sum,
                            color = getCategoryColor(sum.expenseCategory)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DonutChart(categorySums: List<CategorySum>, totalSpend: Double) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 40.dp.toPx()
        val radius = size.minDimension / 2 - strokeWidth
        
        var startAngle = -90f // Start from top
        
        categorySums.forEach { categorySum ->
            val sweepAngle = ((categorySum.sumAmount / totalSpend) * 360).toFloat()
            val color = getCategoryColor(categorySum.expenseCategory)
            
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(
                    (size.width - radius * 2) / 2,
                    (size.height - radius * 2) / 2
                ),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            
            startAngle += sweepAngle
        }
    }
}

@Composable
fun CategorySumRow(categorySum: CategorySum, color: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(color = color, shape = CircleShape)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(categorySum.expenseCategory, fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = CurrencyFormatter.formatToIDR(categorySum.sumAmount),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Procedural color generation based on string hash to ensure consistent colors per category
fun getCategoryColor(categoryName: String): Color {
    val colors = listOf(
        Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8), 
        Color(0xFF9575CD), Color(0xFF7986CB), Color(0xFF64B5F6), 
        Color(0xFF4FC3F7), Color(0xFF4DD0E1), Color(0xFF4DB6AC), 
        Color(0xFF81C784), Color(0xFFAED581), Color(0xFFFF8A65)
    )
    val hash = categoryName.hashCode()
    val index = kotlin.math.abs(hash) % colors.size
    return colors[index]
}
