package org.finos.legend.pure.runtime.java.compiled.serialization.binary;

class DistributedMetadataHelper
{
    private static final String META_DATA_DIRNAME = "metadata/";
    private static final String DEFINITIONS_DIRNAME = META_DATA_DIRNAME + "definitions/";
    private static final String CLASSIFIERS_DIRNAME = META_DATA_DIRNAME + "classifiers/";
    private static final String STRINGS_DIRNAME = META_DATA_DIRNAME + "strings/";
    private static final String BINARIES_DIRNAME = META_DATA_DIRNAME + "bin/";

    private static final String BIN_FILE_EXTENSION = ".bin";
    private static final String INDEX_FILE_EXTENSION = ".idx";
    private static final String METADATA_DEFINITION_FILE_EXTENSION = ".json";

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
                string.codePoints().allMatch(DistributedMetadataHelper::isValidMetadataNameCodePoint);
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

    // Metadata definition path

    static String getMetadataDefinitionsDirectory()
    {
        return DEFINITIONS_DIRNAME;
    }

    static String getMetadataDefinitionFilePath(String metadataName)
    {
        return DEFINITIONS_DIRNAME + metadataName + METADATA_DEFINITION_FILE_EXTENSION;
    }

    // Metadata file paths

    static String getMetadataClassifierIndexFilePath(String metadataName, String classifierName)
    {
        return (metadataName == null) ?
                (CLASSIFIERS_DIRNAME + classifierName.replace("::", "/") + INDEX_FILE_EXTENSION) :
                (CLASSIFIERS_DIRNAME + metadataName + "/" + classifierName.replace("::", "/") + INDEX_FILE_EXTENSION);
    }

    static String getMetadataPartitionBinFilePath(String metadataName, int partitionId)
    {
        return (metadataName == null) ?
                (BINARIES_DIRNAME + partitionId + BIN_FILE_EXTENSION) :
                (BINARIES_DIRNAME + metadataName + "/" + partitionId + BIN_FILE_EXTENSION);
    }

    // Strings

    static String getClassifierIdStringsIndexFilePath(String metadataName)
    {
        return (metadataName == null) ?
                (STRINGS_DIRNAME + "classifiers" + INDEX_FILE_EXTENSION) :
                (STRINGS_DIRNAME + metadataName + "/classifiers" + INDEX_FILE_EXTENSION);
    }

    static String getOtherStringsIndexFilePath(String metadataName)
    {
        return (metadataName == null) ?
                (STRINGS_DIRNAME + "other" + INDEX_FILE_EXTENSION) :
                (STRINGS_DIRNAME + metadataName + "/other" + INDEX_FILE_EXTENSION);
    }

    static String getOtherStringsIndexPartitionFilePath(String metadataName, int partitionId)
    {
        return (metadataName == null) ?
                (STRINGS_DIRNAME + "other-" + partitionId + INDEX_FILE_EXTENSION) :
                (STRINGS_DIRNAME + metadataName + "/other-" + partitionId + INDEX_FILE_EXTENSION);
    }
}
