package org.finos.legend.pure.runtime.java.compiled.serialization.binary;

import org.eclipse.collections.impl.utility.StringIterate;

class DistributedMetadataHelper
{
    private static final String META_DATA_DIRNAME = "metadata/";
    private static final String BIN_FILE_EXTENSION = ".bin";
    private static final String INDEX_FILE_EXTENSION = ".idx";

    // Metadata name

    static String validateMetadataName(String string)
    {
        if (!isValidMetadataName(string))
        {
            throw new IllegalArgumentException("Invalid metadata name: " + ((string == null) ? null : ('"' + string + '"')));
        }
        return string;
    }

    static String validateMetadataNameIfPresent(String string)
    {
        return (string == null) ? null : validateMetadataName(string);
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
                StringIterate.allSatisfyCodePoint(string, DistributedMetadataHelper::isValidMetadataNameCodePoint);
    }

    /**
     * Only ASCII letters, numbers, and underscore are valid (_a-zA-Z0-9).
     *
     * @param codePoint code point
     * @return whether it is a valid metadata name code point
     */
    private static boolean isValidMetadataNameCodePoint(int codePoint)
    {
        return (codePoint == '_') || ((codePoint < 128) && Character.isLetterOrDigit(codePoint));
    }

    static String getMetadataIdPrefix(String metadataName)
    {
        return (metadataName == null) ? null : ('$' + metadataName + '$');
    }

    // Metadata file paths

    static String getMetadataClassifierIndexFilePath(String metadataName, String classifierName)
    {
        return (metadataName == null) ?
                (META_DATA_DIRNAME + "classifiers/" + classifierName.replace("::", "/") + INDEX_FILE_EXTENSION) :
                (META_DATA_DIRNAME + metadataName + "/classifiers/" + classifierName.replace("::", "/") + INDEX_FILE_EXTENSION);
    }

    static String getMetadataPartitionBinFilePath(String metadataName, int partitionId)
    {
        return (metadataName == null) ?
                (META_DATA_DIRNAME + partitionId + BIN_FILE_EXTENSION) :
                (META_DATA_DIRNAME + metadataName + "/" + partitionId + BIN_FILE_EXTENSION);
    }

    // Strings

    static String getClassifierIdStringsIndexFilePath(String metadataName)
    {
        return (metadataName == null) ?
                (META_DATA_DIRNAME + "strings/classifiers" + INDEX_FILE_EXTENSION) :
                (META_DATA_DIRNAME + metadataName + "/strings/classifiers" + INDEX_FILE_EXTENSION);
    }

    static String getOtherStringsIndexFilePath(String metadataName)
    {
        return (metadataName == null) ?
                (META_DATA_DIRNAME + "strings/other" + INDEX_FILE_EXTENSION) :
                (META_DATA_DIRNAME + metadataName + "/strings/other" + INDEX_FILE_EXTENSION);
    }

    static String getOtherStringsIndexPartitionFilePath(String metadataName, int partitionId)
    {
        return (metadataName == null) ?
                (META_DATA_DIRNAME + "strings/other-" + partitionId + INDEX_FILE_EXTENSION) :
                (META_DATA_DIRNAME + metadataName + "/strings/other-" + partitionId + INDEX_FILE_EXTENSION);
    }
}
