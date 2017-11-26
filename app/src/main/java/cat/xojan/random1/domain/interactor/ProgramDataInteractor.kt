package cat.xojan.random1.domain.interactor
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.text.TextUtils
import android.util.Log
import cat.xojan.random1.data.SharedPrefDownloadPodcastRepository
import cat.xojan.random1.domain.entities.*
import cat.xojan.random1.domain.repository.ProgramRepository
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import javax.inject.Inject

class ProgramDataInteractor @Inject constructor(
        private val programRepo: ProgramRepository,
        private val downloadRepo: SharedPrefDownloadPodcastRepository,
        private val context: Context,
        private val downloadManager: DownloadManager,
        private val eventLogger: EventLogger) {

    companion object {
        val EXTENSION = ".mp3"
    }

    private val PREF_NAME = "shared_preferences"
    private val PREF_SECTION = "pref_section"
    private val TAG = ProgramDataInteractor::class.java.simpleName

    private val mDownloadedPodcastsSubject: PublishSubject<List<Podcast>> = PublishSubject.create()

    fun loadPrograms(): Single<List<Program>> {
        return programRepo.getPrograms()
                .flatMap {
                    podcasts -> Observable.just(podcasts)
                        .flatMapIterable { p -> p }
                        .filter { p -> p.active }
                        .toList()
                }
    }

    fun hasSections(programId: String): Boolean {
        return programRepo.hasSections(programId)
    }

    fun loadSections(programId: String): Single<List<Section>> {
        val program = programRepo.getProgram(programId)
        return programRepo.getSections(programId)
                .flatMap {
                    sections -> Observable.just(sections)
                        .flatMapIterable { s -> s }
                        .filter { s -> s.active }
                        .filter { s -> s.type == SectionType.SECTION }
                        .map { s ->
                            s.imageUrl = program?.imageUrl()
                            s
                        }
                        .toList()
                }
    }

    fun loadPodcasts(program: Program, section: Section?,
                     refresh: Boolean): Flowable<List<Podcast>> {
        /*currentProgram = program
        try {
            if (section != null) {
                if (podcastsBySection == null || refresh || section != section) {
                    mSection = section
                    podcastsBySection = programRepo.getPodcasts(program.id, section.id)
                }
                return podcastsBySection as Flowable<List<Podcast>>
            } else {
                if (podcastsByProgram == null || refresh || mProgram != program) {
                    mProgram = program
                    podcastsByProgram = programRepo.getPodcasts(program.id, null)
                }
                return podcastsByProgram as Flowable<List<Podcast>>
            }
        } catch (e: IOException) {
            return Flowable.error(e)
        }*/
        return Flowable.error(Throwable())
    }

    fun addDownload(audioId: String) {
        val from = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString() +
                File.separator + audioId + EXTENSION)
        val to = File(context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS).toString() +
                File.separator + audioId + EXTENSION)

        if (from.renameTo(to)) {
            Log.d(TAG, "moving download from " + from.path + " to " + to.path)
            downloadRepo.setPodcastAsDownloaded(audioId, to.path)
        } else {
            from.delete()
            to.delete()
        }
    }

    /*fun getHourByHourPodcasts(programId: String): Flowable<List<Podcast>> =
            programRepo.getPodcasts(programId)*/

    fun getDownloadedPodcasts(): Single<List<Podcast>> {
        return Single.just(fetchDownloadedPodcasts())
    }

    fun getDownloadedPodcastsUpdates(): PublishSubject<List<Podcast>> {
        return mDownloadedPodcastsSubject
    }

    fun refreshDownloadedPodcasts() {
        mDownloadedPodcastsSubject.onNext(fetchDownloadedPodcasts())
    }

    private fun fetchDownloadedPodcasts(): List<Podcast> {
        val podcastList = HashSet<Podcast>()
        val downloading = downloadRepo.getDownloadingPodcasts()
        val downloaded = downloadRepo.getDownloadedPodcasts()
        podcastList.addAll(downloading)
        podcastList.addAll(downloaded)

        Log.d(TAG, "Downloading: " + downloading.size + ", downloaded: " + downloaded.size)
        return ArrayList(podcastList)
    }

    fun deleteDownload(podcast: Podcast) {
        val file = File(podcast.filePath!!)
        if (file.delete()) {
            downloadRepo.deleteDownloadedPodcast(podcast)
        }
    }

    fun download(podcast: Podcast) {
        val uri = Uri.parse(podcast.path)
        val request = DownloadManager.Request(uri)
                .setTitle(podcast.title)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS,
                        podcast.audioId!! + EXTENSION)
                .setVisibleInDownloadsUi(true)

        val reference = downloadManager.enqueue(request)
        podcast.downloadReference = reference
        addDownloadingPodcast(podcast)
    }

    private fun addDownloadingPodcast(podcast: Podcast): Boolean {
        return downloadRepo.addDownloadingPodcast(podcast)
    }

    fun getDownloadedPodcastTitle(audioId: String): String? {
        return downloadRepo.getDownloadedPodcastTitle(audioId)
    }

    fun deleteDownloading(reference: Long) {
        var podcast: Podcast? = null
        for (pod in downloadRepo.getDownloadingPodcasts()) {
            if (reference == pod.downloadReference) {
                podcast = pod
            }
        }
        downloadRepo.deleteDownloadingPodcast(podcast!!)
    }

    fun exportPodcasts(): Observable<Boolean> {
        eventLogger.logExportedPodcastAction()
        return Observable.create { e ->
            val iternalFileDir = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS)
            val externalFilesDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PODCASTS)

            externalFilesDir.mkdirs()

            for (podcastFile in iternalFileDir!!.listFiles()) {
                val audioId = podcastFile.getPath()
                        .split((Environment.DIRECTORY_PODCASTS + "/").toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()[1].replace(".mp3", "")
                var podcastTitle = getDownloadedPodcastTitle(audioId)

                if (!TextUtils.isEmpty(podcastTitle)) {
                    podcastTitle = podcastTitle!!.replace("/", "-")
                    val dest = File(externalFilesDir, podcastTitle + ".mp3")
                    copy(podcastFile, dest)
                    eventLogger.logExportedPodcast(podcastTitle)
                }
            }
            e.onNext(true)
        }
    }

    private fun copy(src: File, dst: File) {
        try {
            val inStream = FileInputStream(src)
            val outStream = FileOutputStream(dst)
            val inChannel = inStream.channel
            val outChannel = outStream.channel
            inChannel.transferTo(0, inChannel.size(), outChannel)
            inStream.close()
            outStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}