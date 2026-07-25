package xyz.malefic.koven.util

/**
 * Sanitizes a filename to prevent directory traversal attacks.
 *
 * This function:
 * 1. Extracts the last segment of a path (the actual filename).
 * 2. Removes any remaining path separators (/ or \).
 * 3. Removes sequences that could be used for traversal (e.g., ..).
 *
 * @return The sanitized filename.
 */
fun String.sanitizeFilename(): String {
    val filename = this.substringAfterLast('/').substringAfterLast('\\')

    return filename
        .replace("..", "")
        .replace("/", "")
        .replace("\\", "")
        .trim()
}
