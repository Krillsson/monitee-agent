package com.krillsson.sysapi.persistence

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.util.StdDateFormat
import com.krillsson.sysapi.util.FileSystem
import com.krillsson.sysapi.util.measureTimeMillis
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileReader
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.Paths

abstract class JsonFile<T>(
    fileName: String,
    private val typeToken: TypeReference<T>,
    objectMapperBase: ObjectMapper,
    directory: File = FileSystem.data
) : Store<T> {

    private val filePath = File(directory, fileName)
    private val tempPath = File(directory, "$fileName.tmp")

    private val objectMapper = objectMapperBase.copy()
        .apply {
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            dateFormat = StdDateFormat()
        }

    fun existsWithContent(): Boolean {
        return filePath.exists() && Files.size(Paths.get(filePath.path)) > 0
    }

    @Synchronized
    override fun update(action: (T?) -> T) {
        val previousValue = read()
        val newValue = action(previousValue)
        write(newValue)
    }

    @Synchronized
    override fun read(): T? {
        val file = getFile()
        val timedValue: Pair<Long, T?> = measureTimeMillis {
            try {
                FileReader(file).use { reader ->
                    var jsonObject: T? = null
                    if (file.length() > 0) {
                        jsonObject = objectMapper.readValue(reader, typeToken)
                    }
                    jsonObject
                }
            } catch (e: IOException) {
                LOGGER.error("Exception while reading {}", filePath, e)
                null
            }
        }
        val duration = timedValue.first
        LOGGER.debug(
            "Took {} to read {}",
            "${duration}ms",
            filePath
        )
        return timedValue.second
    }

    @Synchronized
    override fun write(content: T) {
        val duration = measureTimeMillis {
            getFile()
            try {
                LOGGER.debug("Writing {}", filePath)
                val bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(content)
                FileOutputStream(tempPath).use { out ->
                    out.write(bytes)
                    out.flush()
                    out.fd.sync()
                }
                moveIntoPlace()
            } catch (e: IOException) {
                LOGGER.error("Exception while writing {}", filePath, e)
                tempPath.delete()
            }
        }
        LOGGER.debug(
            "Took {} to write {}",
            "${duration.first.toInt()}ms",
            filePath
        )
    }

    @Synchronized
    override fun clear() {
        LOGGER.debug("Deleting {}", filePath)
        getFile().delete()
    }

    fun deleteOnExit() {
        getFile().deleteOnExit()
        LOGGER.info("Marked $filePath for deletion. Will delete when application quits")
    }

    private fun moveIntoPlace() {
        try {
            Files.move(
                tempPath.toPath(),
                filePath.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tempPath.toPath(), filePath.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun getFile(): File {
        try { //does not create a file if it already exists
            filePath.createNewFile()
        } catch (e: IOException) {
            LOGGER.error("Unable to create file {}", filePath, e)
        }
        return filePath
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(JsonFile::class.java)
    }
}