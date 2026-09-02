package com.academicjourney.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val andalus = program.gradingScheme == GradeCalculator.ANDALUS_SPLIT_PRACTICAL_THEORY

    val existingFirst = when {
        svu -> course.assignmentGrade
        andalus -> course.studentWorkGrade ?: course.practicalGrade
        else -> course.practicalGrade
    }
    val existingSecond = when {
        svu -> course.examGrade
        andalus -> course.practicalExamGrade ?: if (course.practicalGrade != null) 0.0 else null
        else -> course.theoryGrade
    }
    val existingThird = if (andalus) course.theoryGrade else null

    var first by remember(course.id, existingFirst) { mutableStateOf(existingFirst?.cleanText().orEmpty()) }
    var second by remember(course.id, existingSecond) { mutableStateOf(existingSecond?.cleanText().orEmpty()) }
    var third by remember(course.id, existingThird) { mutableStateOf(existingThird?.cleanText().orEmpty()) }
    var error by remember(course.id) { mutableStateOf<String?>(null) }
    var saved by remember(course.id, existingFirst, existingSecond, existingThird) { mutableStateOf(false) }
    var showClearDialog by remember(course.id) { mutableStateOf(false) }

    val firstNumber = first.toDoubleOrNull()
    val secondNumber = second.toDoubleOrNull()
    val thirdNumber = third.toDoubleOrNull()
    val validation = when {
        andalus && firstNumber != null && secondNumber != null && thirdNumber != null ->
            GradeCalculator.validateAndalus(firstNumber, secondNumber, thirdNumber)
        svu && firstNumber != null && secondNumber != null ->
            GradeCalculator.validateSvu(firstNumber, secondNumber)
        !svu && !andalus && firstNumber != null && secondNumber != null ->
            GradeCalculator.validatePracticalTheory(firstNumber, secondNumber)
        else -> null
    }
    val preview = if (validation == null) {
        when {
            andalus && firstNumber != null && secondNumber != null && thirdNumber != null ->
                firstNumber + secondNumber + thirdNumber
            svu && firstNumber != null && secondNumber != null ->
                firstNumber * program.assignmentWeight / 100.0 +
                    secondNumber * program.examWeight / 100.0
            !svu && firstNumber != null && secondNumber != null -> firstNumber + secondNumber
            else -> null
        }
    } else null

    val hasExisting = existingFirst != null || existingSecond != null || existingThird != null

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("إدخال الدرجات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                when {
                    andalus -> "أدخل أعمال الطالب والامتحان العملي والنظري. يجب ألا يتجاوز مجموعها النهائي 100."
                    svu -> "أدخل درجة الوظيفة ودرجة الامتحان، وسيحسب التطبيق النتيجة حسب وزن البرنامج."
                    else -> "أدخل درجتي العملي والنظري، وسيحسب التطبيق المجموع النهائي تلقائيًا."
                },
                style = MaterialTheme.typography.bodyMedium
            )

            GradeInputField(
                value = first,
                onValueChange = { first = it; error = null; saved = false },
                label = when {
                    svu -> "درجة الوظيفة"
                    andalus -> "أعمال الطالب"
                    else -> "درجة العملي"
                }
            )
            GradeInputField(
                value = second,
                onValueChange = { second = it; error = null; saved = false },
                label = when {
                    svu -> "درجة الامتحان"
                    andalus -> "الامتحان العملي"
                    else -> "درجة النظري"
                }
            )
            if (andalus) {
                GradeInputField(
                    value = third,
                    onValueChange = { third = it; error = null; saved = false },
                    label = "درجة النظري"
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("قاعدة الحساب", fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            andalus -> "المجموع العملي = أعمال الطالب + الامتحان العملي. النتيجة النهائية = المجموع العملي + النظري، وبين 0 و100."
                            svu -> "${program.assignmentWeight.toInt()}% وظيفة + ${program.examWeight.toInt()}% امتحان. كل خانة بين 0 و100."
                            else -> "النتيجة النهائية = العملي + النظري، ويجب أن تكون بين 0 و100."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (andalus && firstNumber != null && secondNumber != null && gradeFieldError(first).isNullOrEmpty() && gradeFieldError(second).isNullOrEmpty()) {
                Text(
                    "المجموع العملي: ${formatEntryGrade(firstNumber + secondNumber)}/100",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            validation?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }

            preview?.let {
                val passed = it >= program.passingGrade
                Surface(
                    color = if (passed) Color(0xFFDDF7E6) else Color(0xFFFFE3E1),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("المجموع النهائي", style = MaterialTheme.typography.labelMedium)
                            Text("${formatEntryGrade(it)}/100", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("الحالة", style = MaterialTheme.typography.labelMedium)
                            Text(
                                if (passed) "ناجح" else "راسب",
                                fontWeight = FontWeight.Bold,
                                color = if (passed) Color(0xFF146C38) else Color(0xFFB3261E)
                            )
                        }
                    }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
            if (saved) Text("تم حفظ الدرجة بنجاح.", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)

            Button(
                onClick = {
                    val firstValue = first.toDoubleOrNull()
                    val secondValue = second.toDoubleOrNull()
                    val thirdValue = third.toDoubleOrNull()
                    val missingOrInvalid = gradeFieldError(first) != null ||
                        gradeFieldError(second) != null ||
                        (andalus && gradeFieldError(third) != null) ||
                        firstValue == null || secondValue == null || (andalus && thirdValue == null)

                    if (missingOrInvalid) {
                        error = "يجب أن تكون كل درجة بين 0 و100."
                    } else {
                        val x = requireNotNull(firstValue)
                        val y = requireNotNull(secondValue)
                        error = when {
                            andalus -> GradeCalculator.validateAndalus(x, y, requireNotNull(thirdValue))
                            svu -> GradeCalculator.validateSvu(x, y)
                            else -> GradeCalculator.validatePracticalTheory(x, y)
                        }
                        if (error == null) {
                            onSave(
                                when {
                                    andalus -> course.copy(
                                        studentWorkGrade = x,
                                        practicalExamGrade = y,
                                        practicalGrade = x + y,
                                        theoryGrade = thirdValue
                                    )
                                    svu -> course.copy(assignmentGrade = x, examGrade = y)
                                    else -> course.copy(practicalGrade = x, theoryGrade = y)
                                }
                            )
                            saved = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
            ) { Text(if (hasExisting) "تحديث الدرجة" else "حفظ الدرجة", fontWeight = FontWeight.Bold) }

            if (hasExisting) {
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
                                    practicalGrade = null,
                                    theoryGrade = null,
                                    assignmentGrade = null,
                                    examGrade = null,
                                    studentWorkGrade = null,
                                    practicalExamGrade = null
                                )
                            )
                            first = ""
                            second = ""
                            third = ""
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

@Composable
private fun GradeInputField(value: String, onValueChange: (String) -> Unit, label: String) {
    val fieldError = gradeFieldError(value)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = { Text(fieldError ?: "يجب أن تكون الدرجة بين 0 و100.") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = fieldError != null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun gradeFieldError(value: String): String? {
    if (value.isBlank()) return null
    val number = value.toDoubleOrNull()
    return if (number == null || number !in 0.0..100.0) "يجب أن تكون الدرجة بين 0 و100." else null
}

private fun formatEntryGrade(value: Double): String = String.format(Locale.US, "%.2f", value)

private fun Double.cleanText(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()
