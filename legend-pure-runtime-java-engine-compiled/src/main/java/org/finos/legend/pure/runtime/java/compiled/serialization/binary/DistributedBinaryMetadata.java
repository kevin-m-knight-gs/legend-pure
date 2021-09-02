package org.finos.legend.pure.runtime.java.compiled.serialization.binary;

import java.util.Collection;
import java.util.Collections;

public interface DistributedBinaryMetadata
{
    /**
     * Get the name of this metadata.
     *
     * @return metadata name
     */
    String getName();

    /**
     * Get the names of the metadata that this depends on.
     *
     * @return metadata dependency names
     */
    default Collection<String> getDependencies()
    {
        return Collections.emptyList();
    }

    /**
     * Return whether string is a valid metadata name. This is true if it is a non-empty string consisting of ASCII
     * letters, numbers, and underscore (_a-zA-Z0-9).
     *
     * @param string string
     * @return whether string is a valid metadata name
     */
    static boolean isValidMetadataName(String string)
    {
        return (string != null) &&
                !string.isEmpty() &&
                string.codePoints().allMatch(c -> (c == '_') || ((c < 128) && Character.isLetterOrDigit(c)));
    }
}
