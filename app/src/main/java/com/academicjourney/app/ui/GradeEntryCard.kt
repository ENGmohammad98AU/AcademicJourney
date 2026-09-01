package com.academicjourney.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.academicjourney.app.data.CourseEntity
import com.academicjourney.app.data.ProgramEntity
import com.academicjourney.app.domain.GradeCalculator
import java.util.Locale

@Composable
fun GradeEntryCard(course: CourseEntity, program: ProgramEntity, onSave: (CourseEntity) -> Unit) {
    val svu = program.gradingScheme == GradeCalculator.SVU_WEIGHTED
    val existingFirst = if (svu) course.assignmentGrade else course.practicalGrade
    val existingSecond = if (svu) course.examGrade else course.theoryGrade
    var first by remember(course.id, existingFirst) { mutableStateOf(existingFirst?.cleanText() ?: "") }
    var second by remember(course.id, existingSecond) { mutableStateOf(existingSecond?.cleanText() ?: "") }
    var error by remember(course.id) { mutableStateOf<String?>(null) }
    var saved by remember(course.id, existingFirst, existingSecond) { mutableStateOf(false) }
    var showClearDialog by remember(course.id) { mutableStateOf(false) }

    val a = first.toDoubleOrNull()
    val b = second.toDoubleOrNull()
    val validation = if (a != null && b != null) {
        if (svu) GradeCalculator.validateSvu(a, b) else GradeCalculator.validatePracticalTheory(a, b)
    } else null
    val preview = if (a != null && b != null && validation == null) {
        if (svu) a * program.assignmentWeight / 100.0 + b * program.examWeight / 100.0 else a + b
    } else null

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("إدخال الدرجات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                if (svu) "أدخل درجة الوظيفة ودرجة الامتحان، وسيحسب التطبيق النتيجة تلقائيًا حسب وزن هذا البرنامج."
                else "أدخل درجتي العملي والنظري، وسيحسب التطبيق المجموع النهائي تلقائيًا.",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                value = first,
                onValueChange = { first = it; error = null; saved = false },
                label = { Text(if (svu) "درجة الوظيفة من 100" else "درجة العملي") },
                supportingText = if (!svu) {{ Text("قيمة بين 0 و100") }} else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = a != null && a !in 0.0..100.0,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = second,
                onValueChange = { second = it; error = null; saved = false },
                label = { Text(if (svu) "درجة الامتحان من 100" else "درجة النظري") },
                supportingText = if (!svu) {{ Text("قيمة بين 0 و100، ومجموع العملي والنظري لا يتجاوز 100") }} else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = b != null && b !in 0.0..100.0,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("قاعدة الحساب", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (svu) "${program.assignmentWeight.toInt()}% وظيفة + ${program.examWeight.toInt()}% امتحان. كل خانة تقبل من 0 إلى 100."
                        else "الدرجة النهائية = العملي + النظري. كل خانة من 0 إلى 100، والمجموع النهائي لا يجوز أن يتجاوز 100.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            preview?.let {
                val passed = it >= program.passingGrade
                Surface(
                    color = if (passed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("المجموع المتوقع", style = MaterialTheme.typography.labelMedium)
                            Text("${String.format(Locale.US, "%.2f", it)}/100", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("الحالة", style = MaterialTheme.typography.labelMedium)
                            Text(if (passed) "ناجح" else "راسب", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
            if (saved) Text("تم حفظ الدرجة بنجاح.", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)

            Button(
                onClick = {
                    val x = first.toDoubleOrNull()
                    val y = second.toDoubleOrNull()
                    if (x == null || y == null) {
                        error = "أدخل درجتين رقميتين صحيحتين."
                    } else {
                        error = if (svu) GradeCalculator.validateSvu(x, y) else GradeCalculator.validatePracticalTheory(x, y)
                        if (error == null) {
                            onSave(
                                if (svu) course.copy(assignmentGrade = x, examGrade = y)
                                else course.copy(practicalGrade = x, theoryGrade = y)
                            )
                            saved = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
            ) { Text(if (existingFirst != null || existingSecond != null) "تحديث الدرجة" else "حفظ الدرجة", fontWeight = FontWeight.Bold) }

            if (existingFirst != null || existingSecond != null) {
                OutlinedButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("مسح الدرجة المحفوظة") }
            }

            if (showClearDialog) {
                AlertDialog(
                    onDismissRequest = { showClearDialog = false },
                    title = { Text("مسح الدرجة؟") },
                    text = { Text("سيتم حذف درجات هذه المادة وإعادتها إلى حالة غير مُقيّمة. لن تُحذف الملاحظات.") },
                    confirmButton = {
                        TextButton(onClick = {
                            onSave(
                                course.copy(
                                    practicalGrade = null, theoryGrade = null,
                                    assignmentGrade = null, examGrade = null
                                )
                            )
                            first = ""
                            second = ""
                            saved = false
                            error = null
                            showClearDialog = false
                        }) { Text("مسح") }
                    },
                    dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("إلغاء") } }
                )
            }
        }
    }
}

private fun Double.cleanText(): String = if (this % 1.0 == 0.0) this.toInt().toString() else this.toString()
