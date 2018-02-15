package cat.xojan.random1.domain.interactor
import cat.xojan.random1.domain.model.Program
import cat.xojan.random1.domain.model.Section
import cat.xojan.random1.domain.model.SectionType
import cat.xojan.random1.domain.repository.ProgramRepository
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import javax.inject.Inject

class ProgramDataInteractor @Inject constructor(private val programRepo: ProgramRepository) {

    fun loadPrograms(): Single<List<Program>> {
        return programRepo.getPrograms()
                .flatMap {
                    programs -> Observable.just(programs)
                        .flatMapIterable { p -> p }
                        .filter { p -> p.active }
                        .toList()
                }
    }

    fun hasSections(programId: String): Single<Boolean> {
        return programRepo.hasSections(programId)
    }

    fun loadSections(programId: String): Single<List<Section>> {
        val program = programRepo.getProgram(programId)
        val sectionList = programRepo.getSections(programId)

        return Single.zip(program, sectionList,
                BiFunction<Program, List<Section>, List<Section>> { program, sectionList ->
                    for (s in sectionList) {
                        s.programId = program.id
                        s.imageUrl = program.imageUrl()
                    }
                    sectionList
                })
                .flatMap {
                    sections -> Observable.just(sections)
                        .flatMapIterable { s -> s }
                        .filter { s -> s.active }
                        .filter { s -> s.type == SectionType.SECTION }
                        .toList()
                }
    }
}