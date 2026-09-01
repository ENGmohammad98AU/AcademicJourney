package com.academicjourney.app.domain
import com.academicjourney.app.data.*
data class GradeResult(val finalGrade:Double?,val isPassed:Boolean?)
object GradeCalculator{
const val SVU_WEIGHTED="SVU_WEIGHTED";const val PRACTICAL_THEORY="PRACTICAL_THEORY"
fun calculate(c:CourseEntity,p:ProgramEntity):GradeResult{val f=when(p.gradingScheme){SVU_WEIGHTED->{val a=c.assignmentGrade?:return GradeResult(null,null);val e=c.examGrade?:return GradeResult(null,null);if(!valid(a)||!valid(e))return GradeResult(null,null);a*p.assignmentWeight/100.0+e*p.examWeight/100.0};PRACTICAL_THEORY->{val pr=c.practicalGrade?:return GradeResult(null,null);val th=c.theoryGrade?:return GradeResult(null,null);if(!valid(pr)||!valid(th)||pr+th>100)return GradeResult(null,null);pr+th};else->null};return GradeResult(f,f?.let{it>=p.passingGrade})}
fun validatePracticalTheory(pr:Double,th:Double):String?=when{!valid(pr)||!valid(th)->"يجب أن تكون درجتا العملي والنظري بين 0 و100.";pr+th>100->"مجموع العملي والنظري لا يجوز أن يتجاوز 100.";else->null}
fun validateSvu(a:Double,e:Double):String?=if(!valid(a)||!valid(e))"يجب أن تكون درجتا الوظيفة والامتحان بين 0 و100." else null
fun semesterAverage(cs:List<CourseEntity>,p:ProgramEntity):Double?=cs.mapNotNull{calculate(it,p).finalGrade}.takeIf{it.isNotEmpty()}?.average()
private fun valid(v:Double)=v in 0.0..100.0}
