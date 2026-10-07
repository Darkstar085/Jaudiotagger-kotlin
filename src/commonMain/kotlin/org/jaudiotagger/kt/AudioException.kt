package org.jaudiotagger.kt

/** Base class for all errors raised by the library. */
open class AudioException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

/** The file is not a valid or supported audio file, or is too corrupt to parse. */
class CannotReadException(message: String? = null, cause: Throwable? = null) : AudioException(message, cause)

/** The file could not be modified (I/O error, no permission, corrupt structure). */
class CannotWriteException(message: String? = null, cause: Throwable? = null) : AudioException(message, cause)

/** A single tag structure (frame, block, field) inside the file is malformed. */
class InvalidTagDataException(message: String? = null, cause: Throwable? = null) : AudioException(message, cause)
