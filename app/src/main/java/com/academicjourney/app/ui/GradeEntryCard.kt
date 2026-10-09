package com.academicjourney.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.academicjourney.app.data.CourseEntity
import com.academicjourney.app.data.ProgramEntity
import com.academicjourney.app.domain.GradeCalculator
import com.academicjourney.app.domain.PartialGradePreviewBuilder
import com.academicjourney.app.domain.ProjectGradePolicy
import java.util.Locale

@Composable
fun GradeEntryCard(course: CourseEntity, program: ProgramEntity, onSave: (CourseEntity) -> Unit) {
    val svu = program.gradingScheme == GradeCalculator.SVU_WEIGHTED
    val andalus = program.gradingScheme == GradeCalculator.ANDALUS_SPLIT_PRACTICAL_THEORY
    val direct = program.gradingScheme == GradeCalculator.SINGLE_FINAL_GRADE
    val project = ProjectGradePolicy.usesSingleProjectGrade(course, program)
    val singleField = direct || project
    val firstLabel = when {
        project -> "درجة المشروع"
        direct -> "الدرجة النهائية"
        svu -> "درجة الوظيفة"
        andalus -> "أعمال الطالب"
        else -> "درجة العملي"
    }
    val secondLabel = when {
        svu -> "درجة الامتحان"
        andalus -> "الامتحان العملي"
        else -> "درجة النظري"
    }
    val thirdLabel = "درجة النظري"

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
        project -> ProjectGradePolicy.displayedGrade(course, program)
        direct -> course.directGrade
        svu -> course.assignmentGrade
        andalus -> course.studentWorkGrade ?: course.practicalGrade
        else -> course.practicalGrade
    }
    val existingSecond = when {
        singleField -> null
        svu -> course.examGrade
        andalus -> course.practicalExamGrade ?: if (course.practicalGrade != null) 0.0 else null
        else -> course.theoryGrade
    }
    val existingThird = if (andalus) course.theoryGrade else null

    var first by rememberSaveable(course.id, existingFirst) { mutableStateOf(existingFirst?.cleanText().orEmpty()) }
    var second by rememberSaveable(course.id, existingSecond) { mutableStateOf(existingSecond?.cleanText().orEmpty()) }
    var third by rememberSaveable(course.id, existingThird) { mutableStateOf(existingThird?.cleanText().orEmpty()) }
    var baselineFirst by rememberSaveable(course.id) { mutableStateOf(existingFirst) }
    var baselineSecond by rememberSaveable(course.id) { mutableStateOf(existingSecond) }
    var baselineThird by rememberSaveable(course.id) { mutableStateOf(existingThird) }
    // A restored draft belongs to the saved grades it was started from. External restore/undo
    // invalidates it, while collapsing and reopening the same unchanged course keeps it.
    LaunchedEffect(existingFirst, existingSecond, existingThird) {
        if (baselineFirst != existingFirst || baselineSecond != existingSecond || baselineThird != existingThird) {
            first = existingFirst?.cleanText().orEmpty()
            second = existingSecond?.cleanText().orEmpty()
            third = existingThird?.cleanText().orEmpty()
            baselineFirst = existingFirst
            baselineSecond = existingSecond
            baselineThird = existingThird
        }
    }
    var error by remember(course.id) { mutableStateOf<String?>(null) }
    var saved by remember(course.id) { mutableStateOf(false) }
    var showClearDialog by remember(course.id) { mutableStateOf(false) }

    val firstNumber = first.toDoubleOrNull()
    val secondNumber = second.toDoubleOrNull()
    val thirdNumber = third.toDoubleOrNull()
    val hasInvalidField = gradeFieldError(first) != null ||
        (!singleField && gradeFieldError(second) != null) ||
        (andalus && gradeFieldError(third) != null)
    val hasAnyInput = first.isNotBlank() || (!singleField && second.isNotBlank()) || (andalus && third.isNotBlank())
    val entryComplete = when {
        singleField -> firstNumber != null
        andalus -> firstNumber != null && secondNumber != null && thirdNumber != null
        else -> firstNumber != null && secondNumber != null
    }
    val validation = if (hasInvalidField) null else when {
        project -> GradeCalculator.validateProjectGrade(firstNumber)
        direct -> GradeCalculator.validateDirectGrade(firstNumber)
        andalus -> GradeCalculator.validatePartialAndalus(firstNumber, secondNumber, thirdNumber)
        svu -> GradeCalculator.validatePartialSvu(firstNumber, secondNumber)
        else -> GradeCalculator.validatePartialPracticalTheory(firstNumber, secondNumber)
    }
    val previewCourse = if (validation == null && entryComplete) {
        when {
            project -> ProjectGradePolicy.withProjectGrade(course, firstNumber)
            direct -> course.copy(directGrade = firstNumber)
            andalus ->
                course.copy(
                    studentWorkGrade = firstNumber,
                    practicalExamGrade = secondNumber,
                    practicalGrade = requireNotNull(firstNumber) + requireNotNull(secondNumber),
                    theoryGrade = thirdNumber
                )
            svu ->
                course.copy(assignmentGrade = firstNumber, examGrade = secondNumber)
            else ->
                course.copy(practicalGrade = firstNumber, theoryGrade = secondNumber)
        }
    } else null
    val preview = previewCourse?.let { GradeCalculator.calculate(it, program) }
    val partialPreview = if (direct && first.isBlank() && course.directGrade == null) {
        PartialGradePreviewBuilder.forCourse(course, program)
    } else if (!singleField && !hasInvalidField && validation == null) {
        PartialGradePreviewBuilder.build(
            buildList {
                add(firstLabel to firstNumber)
                add(secondLabel to secondNumber)
                if (andalus) add(thirdLabel to thirdNumber)
            }
        )
    } else {
        null
    }

    val hasExisting = existingFirst != null || existingSecond != null || existingThird != null || partialPreview != null

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("إدخال الدرجات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                when {
                    project -> "أدخل درجة المشروع النهائية مباشرةً في حقل واحد بين 0 و100."
                    direct -> "أدخل الدرجة النهائية للمقرر في حقل واحد؛ النجاح يبدأ من ${program.passingGrade.toInt()}/100."
                    andalus -> "يمكن حفظ كل درجة منفردة؛ تظهر القيم المدخلة فورًا مع توضيح الدرجات الناقصة."
                    svu -> "يمكن حفظ الوظيفة أو الامتحان منفردًا؛ تظهر الدرجة المدخلة فورًا والنتيجة النهائية بعد اكتمالهما."
                    else -> "يمكن حفظ العملي دون النظري؛ تظهر درجة العملي فورًا مع تنبيه بأن النظري لم يُدخل بعد."
                },
                style = MaterialTheme.typography.bodyMedium
            )

            GradeInputField(
                value = first,
                onValueChange = { first = it; error = null; saved = false },
                label = firstLabel
            )
            if (!singleField) {
                GradeInputField(
                    value = second,
                    onValueChange = { second = it; error = null; saved = false },
                    label = secondLabel
                )
            }
            if (andalus) {
                GradeInputField(
                    value = third,
                    onValueChange = { third = it; error = null; saved = false },
                    label = thirdLabel
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
                            project -> "درجة المشروع هي الدرجة النهائية للمقرر، وتُدخل مباشرةً من 0 إلى 100."
                            direct -> "درجة المقرر النهائية تُدخل مباشرةً في حقل واحد بين 0 و100."
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

            partialPreview?.let { partial ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (direct) Text("هذه القيم محفوظة من النظام السابق للمراجعة. أدخل الدرجة النهائية في الحقل الموحد أعلاه.", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "النتيجة الحالية (غير مكتملة)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        partial.entered.forEach { component ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(component.label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    formatEntryGrade(component.value),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            partial.missingNotice,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            "لا تُحسب حالة النجاح أو الرسوب حتى تكتمل جميع الدرجات المطلوبة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
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
            if (saved) {
                Text(
                    if (entryComplete) {
                        if (singleField) "تم حفظ الدرجة النهائية بنجاح." else "تم حفظ الدرجات بنجاح."
                    } else {
                        "تم حفظ الدرجة المتاحة؛ يمكنك إكمال بقية الدرجات لاحقًا."
                    },
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            InteractiveButton(
                onClick = {
                    if (hasInvalidField) {
                        error = "يجب أن تكون كل درجة بين 0 و100."
                    } else if (!hasAnyInput) {
                        error = "أدخل درجة واحدة على الأقل، أو استخدم زر مسح الدرجات المحفوظة."
                    } else if (validation != null) {
                        error = validation
                    } else {
                        onSave(
                            when {
                                project -> ProjectGradePolicy.withProjectGrade(course, firstNumber)
                                direct -> course.copy(directGrade = firstNumber)
                                andalus -> course.copy(
                                    studentWorkGrade = firstNumber,
                                    practicalExamGrade = secondNumber,
                                    practicalGrade = if (firstNumber != null && secondNumber != null) {
                                        firstNumber + secondNumber
                                    } else {
                                        null
                                    },
                                    theoryGrade = thirdNumber
                                )
                                svu -> course.copy(
                                    assignmentGrade = firstNumber,
                                    examGrade = secondNumber
                                )
                                else -> course.copy(
                                    practicalGrade = firstNumber,
                                    theoryGrade = secondNumber
                                )
                            }
                        )
                        error = null
                        // Success is reported by the ViewModel only after the database commit.
                        saved = false
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
            ) {
                Text(
                    when {
                        project && hasExisting -> "تحديث درجة المشروع"
                        project -> "حفظ درجة المشروع"
                        direct && hasExisting -> "تحديث الدرجة النهائية"
                        direct -> "حفظ الدرجة النهائية"
                        hasExisting -> "تحديث الدرجات المتاحة"
                        else -> "حفظ الدرجات المتاحة"
                    },
                    fontWeight = FontWeight.Bold
                )
            }

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
                                    practicalExamGrade = null,
                                    directGrade = null
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
        supportingText = { Text(fieldError ?: "يمكن حفظ هذه الخانة منفردة؛ القيمة بين 0 و100.") },
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
