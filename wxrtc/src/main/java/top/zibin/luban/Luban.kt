package top.zibin.luban

import android.content.Context
import android.net.Uri
import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.TextUtils
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

class Luban(
    builder: Builder
) : Handler.Callback {
    companion object {
        private const val TAG: String = "Luban"
        private const val DEFAULT_DISK_CACHE_DIR: String = "luban_disk_cache"

        private const val MSG_COMPRESS_SUCCESS: Int = 0
        private const val MSG_COMPRESS_START: Int = 1
        private const val MSG_COMPRESS_ERROR: Int = 2

        class Builder internal constructor(private val context: Context) {
            internal var mTargetDir: String? = null
            private var focusAlpha = false
            var mLeastCompressSize: Int = 100
            var mRenameListener: OnRenameListener? = null
            var mCompressListener: OnCompressListener? = null
            var mCompressionPredicate: CompressionPredicate? = null
            val mStreamProviders: MutableList<InputStreamProvider> = ArrayList()

            private fun build(): Luban {
                return Luban(this)
            }

            fun load(inputStreamProvider: InputStreamProvider): Builder {
                mStreamProviders.add(inputStreamProvider)
                return this
            }

            fun load(file: File): Builder {
                mStreamProviders.add(object : InputStreamProvider {
                    @Throws(IOException::class)
                    override fun open(): InputStream {
                        return FileInputStream(file)
                    }

                    override fun getPath(): String {
                        return file.absolutePath
                    }
                })
                return this
            }

            fun load(string: String): Builder {
                mStreamProviders.add(object : InputStreamProvider {
                    @Throws(IOException::class)
                    override fun open(): InputStream {
                        return FileInputStream(string)
                    }

                    override fun getPath(): String {
                        return string
                    }
                })
                return this
            }

            fun <T> load(list: List<T>): Builder {
                for (src in list) {
                    if (src is String) {
                        load(src as String)
                    } else if (src is File) {
                        load(src as File)
                    } else if (src is Uri) {
                        load(src as Uri)
                    } else {
                        throw IllegalArgumentException("Incoming data type exception, it must be String, File, Uri or Bitmap")
                    }
                }
                return this
            }

            fun load(uri: Uri): Builder {
                mStreamProviders.add(object : InputStreamProvider {
                    @Throws(IOException::class)
                    override fun open(): InputStream {
                        return context.contentResolver.openInputStream(uri)!!
                    }

                    override fun getPath(): String {
                        return uri.path!!
                    }
                })
                return this
            }

            fun putGear(gear: Int): Builder {
                return this
            }

            fun setRenameListener(listener: OnRenameListener?): Builder {
                this.mRenameListener = listener
                return this
            }

            fun setCompressListener(listener: OnCompressListener?): Builder {
                this.mCompressListener = listener
                return this
            }

            fun setTargetDir(targetDir: String?): Builder {
                this.mTargetDir = targetDir
                return this
            }

            /**
             * Do I need to keep the image's alpha channel
             *
             * @param focusAlpha
             *
             * true - to keep alpha channel, the compress speed will be slow.
             *
             *  false - don't keep alpha channel, it might have a black background.
             */
            fun setFocusAlpha(focusAlpha: Boolean): Builder {
                this.focusAlpha = focusAlpha
                return this
            }

            /**
             * do not compress when the origin image file size less than one value
             *
             * @param size the value of file size, unit KB, default 100K
             */
            fun ignoreBy(size: Int): Builder {
                this.mLeastCompressSize = size
                return this
            }

            /**
             * do compress image when return value was true, otherwise, do not compress the image file
             *
             * @param compressionPredicate A predicate callback that returns true or false for the given input path should be compressed.
             */
            fun filter(compressionPredicate: CompressionPredicate?): Builder {
                this.mCompressionPredicate = compressionPredicate
                return this
            }


            /**
             * begin compress image with asynchronous
             */
            fun launch() {
                build().launch(context)
            }

            @Throws(IOException::class)
            fun get(path: String): File {
                return build().get(object : InputStreamProvider {
                    @Throws(IOException::class)
                    override fun open(): InputStream {
                        return FileInputStream(path)
                    }

                    override fun getPath(): String {
                        return path
                    }
                }, context)
            }

            /**
             * begin compress image with synchronize
             *
             * @return the thumb image file list
             */
            @Throws(IOException::class)
            fun get(): List<File> {
                return build().get(context)
            }
        }

        fun with(context: Context): Builder {
            return Builder(context)
        }
    }

    private var mTargetDir: String?
    private val focusAlpha = false
    private var mLeastCompressSize = 0
    private var mRenameListener: OnRenameListener?
    private var mCompressListener: OnCompressListener?
    private var mCompressionPredicate: CompressionPredicate?
    private var mStreamProviders: MutableList<InputStreamProvider>?

    private var mHandler: Handler

    init {
        this.mTargetDir = builder.mTargetDir
        this.mRenameListener = builder.mRenameListener
        this.mStreamProviders = builder.mStreamProviders
        this.mCompressListener = builder.mCompressListener
        this.mLeastCompressSize = builder.mLeastCompressSize
        this.mCompressionPredicate = builder.mCompressionPredicate
        mHandler = Handler(Looper.getMainLooper(), this)
    }

//    private fun Luban(builder: Builder) {
//        this.mTargetDir = builder.mTargetDir
//        this.mRenameListener = builder.mRenameListener
//        this.mStreamProviders = builder.mStreamProviders
//        this.mCompressListener = builder.mCompressListener
//        this.mLeastCompressSize = builder.mLeastCompressSize
//        this.mCompressionPredicate = builder.mCompressionPredicate
//        mHandler = Handler(Looper.getMainLooper(), this)
//    }

    /**
     * Returns a file with a cache image name in the private cache directory.
     *
     * @param context A context.
     */
    private fun getImageCacheFile(context: Context, suffix: String): File {
        if (TextUtils.isEmpty(mTargetDir)) {
            mTargetDir = getImageCacheDir(context)!!.absolutePath
        }

        val cacheBuilder = mTargetDir + "/" +
                System.currentTimeMillis() + (Math.random() * 1000).toInt() +
                (if (TextUtils.isEmpty(suffix)) ".jpg" else suffix)

        return File(cacheBuilder)
    }

    private fun getImageCustomFile(context: Context, filename: String): File {
        if (TextUtils.isEmpty(mTargetDir)) {
            mTargetDir = getImageCacheDir(context)!!.absolutePath
        }

        val cacheBuilder = "$mTargetDir/$filename"

        return File(cacheBuilder)
    }

    /**
     * Returns a directory with a default name in the private cache directory of the application to
     * use to store retrieved audio.
     *
     * @param context A context.
     * @see .getImageCacheDir
     */
    private fun getImageCacheDir(context: Context): File? {
        return getImageCacheDir(context, DEFAULT_DISK_CACHE_DIR)
    }

    /**
     * Returns a directory with the given name in the private cache directory of the application to
     * use to store retrieved media and thumbnails.
     *
     * @param context   A context.
     * @param cacheName The name of the subdirectory in which to store the cache.
     * @see .getImageCacheDir
     */
    private fun getImageCacheDir(context: Context, cacheName: String): File? {
        val cacheDir = context.externalCacheDir
        if (cacheDir != null) {
            val result = File(cacheDir, cacheName)
            if (!result.mkdirs() && (!result.exists() || !result.isDirectory)) {
                // File wasn't able to create a directory, or the result exists but not a directory
                return null
            }
            return result
        }
        if (Log.isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, "default disk cache dir is null")
        }
        return null
    }

    /**
     * start asynchronous compress thread
     */
    private fun launch(context: Context) {
        if (mStreamProviders == null || mStreamProviders!!.size == 0 && mCompressListener != null) {
            mCompressListener!!.onError(NullPointerException("image file cannot be null"))
        }

        val iterator = mStreamProviders!!.iterator()

        while (iterator.hasNext()) {
            val path = iterator.next()

            AsyncTask.SERIAL_EXECUTOR.execute {
                try {
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_COMPRESS_START))

                    val result = compress(context, path)

                    mHandler.sendMessage(mHandler.obtainMessage(MSG_COMPRESS_SUCCESS, result))
                } catch (e: IOException) {
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_COMPRESS_ERROR, e))
                }
            }

            iterator.remove()
        }
    }

    /**
     * start compress and return the file
     */
    @Throws(IOException::class)
    private fun get(input: InputStreamProvider, context: Context): File {
        return Engine(
            input,
            getImageCacheFile(context, Checker.SINGLE.extSuffix(input)),
            focusAlpha
        ).compress()
    }

    @Throws(IOException::class)
    private fun get(context: Context): List<File> {
        val results: MutableList<File> = ArrayList()
        val iterator = mStreamProviders!!.iterator()

        while (iterator.hasNext()) {
            results.add(compress(context, iterator.next()))
            iterator.remove()
        }

        return results
    }

    @Throws(IOException::class)
    private fun compress(context: Context, path: InputStreamProvider): File {
        val result: File

        var outFile = getImageCacheFile(context, Checker.SINGLE.extSuffix(path))

        if (mRenameListener != null) {
            val filename = mRenameListener!!.rename(path.getPath())
            outFile = getImageCustomFile(context, filename)
        }

        result = if (mCompressionPredicate != null) {
            if (mCompressionPredicate!!.apply(path.getPath())
                && Checker.SINGLE.needCompress(mLeastCompressSize, path.getPath())
            ) {
                Engine(path, outFile, focusAlpha).compress()
            } else {
                File(path.getPath())
            }
        } else {
            if (Checker.SINGLE.needCompress(mLeastCompressSize, path.getPath())) Engine(
                path,
                outFile,
                focusAlpha
            ).compress() else File(path.getPath())
        }

        return result
    }

    override fun handleMessage(msg: Message): Boolean {
        if (mCompressListener == null) return false

        when (msg.what) {
            MSG_COMPRESS_START -> mCompressListener!!.onStart()
            MSG_COMPRESS_SUCCESS -> mCompressListener!!.onSuccess(msg.obj as File)
            MSG_COMPRESS_ERROR -> mCompressListener!!.onError(msg.obj as Throwable)
        }
        return false
    }



}