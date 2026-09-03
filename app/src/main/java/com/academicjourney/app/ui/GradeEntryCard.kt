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

    if (course.passedWithoutGrade) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("حالة المقرر", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Surface(color = Color(0xFFDDF7E6), shape = MaterialTheme.shapes.medium) {
                    Text(
                        "ناجح بالترفيع دون علامة",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        color = Color(0xFF146C38),
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    course.notes.ifBlank { "تم اعتماد نجاح هذا المقرر دون إدخال علامة." },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "يُحتسب المقرر ضمن المواد الناجحة والساعات المنجزة، ولا يدخل في حساب المعدل.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

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
    val previewCourse = if (validation == null) {
        when {
            andalus && firstNumber != null && secondNumber != null && thirdNumber != null ->
                course.copy(
                    studentWorkGrade = firstNumber,
                    practicalExamGrade = secondNumber,
                    practicalGrade = firstNumber + secondNumber,
                    theoryGrade = thirdNumber
                )
            svu && firstNumber != null && secondNumber != null ->
                course.copy(assignmentGrade = firstNumber, examGrade = secondNumber)
            !svu && firstNumber != null && secondNumber != null ->
                course.copy(practicalGrade = firstNumber, theoryGrade = secondNumber)
            else -> null
        }
    } else null
    val preview = previewCourse?.let { GradeCalculator.calculate(it, program) }

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
                    Text(
                        "أي كسر في المحصلة يُجبر إلى العدد الصحيح الأعلى؛ مثال: 76.1 تصبح 77.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (GradeCalculator.maximumAssistance(program) > 0) {
                        Text(
                            "تُضاف تلقائيًا درجة أو درجتا مساعدة فقط عندما تكفيان للوصول إلى حد النجاح، ويظهر ذلك بوضوح في النتيجة والتقرير.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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

            preview?.let { previewResult ->
                val finalGrade = previewResult.finalGrade ?: return@let
                val passed = previewResult.isPassed == true
                Surface(
                    color = if (passed) Color(0xFFDDF7E6) else Color(0xFFFFE3E1),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("المجموع النهائي", style = MaterialTheme.typography.labelMedium)
                            Text("${formatEntryGrade(finalGrade)}/100", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            val raw = previewResult.rawGrade
                            val rounded = previewResult.roundedGrade
                            if (raw != null && rounded != null && raw != rounded) {
                                Text(
                                    "المحصلة ${formatEntryGrade(raw)} ← بعد جبر الكسر ${formatEntryGrade(rounded)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (previewResult.assistancePoints > 0) {
                                Text(
                                    "مساعدة +${previewResult.assistancePoints}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF146C38)
                                )
                            }
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

            InteractiveButton(
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
                InteractiveOutlinedButton(
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
                        InteractiveTextButton(onClick = {
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
                    dismissButton = { InteractiveTextButton(onClick = { showClearDialog = false }) { Text("إلغاء") } }
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
