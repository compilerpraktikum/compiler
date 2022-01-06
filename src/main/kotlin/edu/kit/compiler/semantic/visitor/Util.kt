
package edu.kit.compiler.semantic.visitor

import edu.kit.compiler.source.AnnotatableFile
import edu.kit.compiler.source.AnnotationType
import edu.kit.compiler.source.SourceFile
import edu.kit.compiler.source.SourceNote
import edu.kit.compiler.source.SourceRange

fun AnnotatableFile.error(lazyAnnotation: () -> AnnotationBuilder) = this.annotate(
    lazyAnnotation().toAnnotation(AnnotationType.ERROR)
)
fun AnnotatableFile.errorIf(condition: Boolean, lazyAnnotation: () -> AnnotationBuilder) {
    if (condition) {
        this.annotate(lazyAnnotation().toAnnotation(AnnotationType.ERROR))
    }
}
fun AnnotatableFile.errorIfNot(condition: Boolean, lazyAnnotation: () -> AnnotationBuilder) = errorIf(!condition, lazyAnnotation)

class AnnotationBuilder(
    val range: SourceRange,
    val message: String,
) {
    var notes: List<SourceNote>? = null

    fun toAnnotation(type: AnnotationType) = SourceFile.Annotation(type, range, message, notes ?: emptyList())
    fun toNote(): SourceNote {
        check(notes == null)
        return SourceNote(range, message)
    }
}

infix fun String.at(range: SourceRange) = AnnotationBuilder(range, this)
infix fun AnnotationBuilder.note(note: AnnotationBuilder) = this.apply { notes = listOf(note.toNote()) }
infix fun AnnotationBuilder.note(notes: List<AnnotationBuilder>) = this.apply { this.notes = notes.map(AnnotationBuilder::toNote) }
