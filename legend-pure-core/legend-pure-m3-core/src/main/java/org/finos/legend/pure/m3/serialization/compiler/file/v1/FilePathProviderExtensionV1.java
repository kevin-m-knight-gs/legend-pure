// Copyright 2024 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.pure.m3.serialization.compiler.file.v1;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;
import org.finos.legend.pure.m3.serialization.compiler.file.FilePathProviderExtension;

public class FilePathProviderExtensionV1 implements FilePathProviderExtension
{
    // UTF-16 uses 2 bytes per Java char, possibly plus a byte order marker of 2 bytes
    // Since the name must be <= 255 bytes in UTF-16, 126 is the length limit for names (126 * 2 + 2 = 254)
    private static final int NAME_LEN_LIMIT = 126;
    private static final int SUFFIX_LEN = 16;

    private static final String ELEMENT_FILE_EXTENSION = ".pelt";

    private static final ImmutableList<String> MODULE_FILE_DIR = Lists.immutable.with("legend", "pure", "module");
    private static final String MODULE_FILE_EXTENSION = ".pmf";

    @Override
    public int version()
    {
        return 1;
    }

    @Override
    public String getElementFilePath(String elementPath, String fsSeparator)
    {
        if (PackageableElement.DEFAULT_PATH_SEPARATOR.equals(elementPath))
        {
            return M3Paths.Root + ELEMENT_FILE_EXTENSION;
        }

        StringBuilder builder = new StringBuilder(elementPath.length() + ELEMENT_FILE_EXTENSION.length());
        int start = 0;
        int sepLen = PackageableElement.DEFAULT_PATH_SEPARATOR.length();
        int end;
        while ((end = elementPath.indexOf(PackageableElement.DEFAULT_PATH_SEPARATOR, start)) != -1)
        {
            appendName(builder, elementPath, start, end, null).append(fsSeparator);
            start = end + sepLen;
        }

        return appendName(builder, elementPath, start, elementPath.length(), ELEMENT_FILE_EXTENSION).toString();
    }

    @Override
    public String getModuleMetadataFilePath(String moduleName, String fsSeparator)
    {
        StringBuilder builder = new StringBuilder(moduleName.length() + MODULE_FILE_EXTENSION.length() + (MODULE_FILE_DIR.size() * fsSeparator.length()) + 16);
        MODULE_FILE_DIR.forEach(name -> builder.append(name).append(fsSeparator));
        return appendName(builder, moduleName, 0, moduleName.length(), MODULE_FILE_EXTENSION).toString();
    }

    private static StringBuilder appendName(StringBuilder builder, String string, int start, int end, String extension)
    {
        int extLen = (extension == null) ? 0 : extension.length();
        int len = (end - start) + extLen;
        if (len < NAME_LEN_LIMIT)
        {
            // append the name, possibly plus extension, as it's below the limit
            builder.append(string, start, end);
            return (extension == null) ? builder : builder.append(extension);
        }

        // if the name is too long, append an initial segment plus a fixed length suffix computed from the overage
        int overageStart = start + NAME_LEN_LIMIT - SUFFIX_LEN - extLen;
        if (Character.isLowSurrogate(string.charAt(overageStart)) && Character.isHighSurrogate(string.charAt(overageStart - 1)))
        {
            // avoid splitting the string in the middle of a supplementary pair
            overageStart--;
        }
        builder.ensureCapacity(builder.length() + (overageStart - start) + SUFFIX_LEN + extLen);
        builder.append(string, start, overageStart);
        String suffix = getOverageSuffix(string, overageStart, end);
        for (int i = suffix.length(); i < SUFFIX_LEN; i++)
        {
            builder.append('0');
        }
        builder.append(suffix);
        return (extension == null) ? builder : builder.append(extension);
    }

    private static String getOverageSuffix(String string, int start, int end)
    {
        long value = 0;
        for (int i = start, cp; i < end; i += Character.charCount(cp))
        {
            value = (31 * value) + (cp = string.codePointAt(i));
        }
        return Long.toHexString(value);
    }
}
