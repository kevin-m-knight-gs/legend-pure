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
import org.finos.legend.pure.m3.tools.FilePathTools;

public class FilePathProviderExtensionV1 implements FilePathProviderExtension
{
    private static final String ELEMENT_FILE_EXTENSION = ".pelt";

    private static final ImmutableList<String> MODULE_FILE_DIR = Lists.immutable.with("org", "finos", "legend", "pure", "module");
    private static final String MODULE_FILE_EXTENSION = ".pmf";

    @Override
    public int version()
    {
        return 1;
    }

    @Override
    public String getElementFilePath(String elementPath, String fsSeparator)
    {
        return PackageableElement.DEFAULT_PATH_SEPARATOR.equals(elementPath) ?
               (M3Paths.Root + ELEMENT_FILE_EXTENSION) :
               FilePathTools.toFilePath(elementPath, PackageableElement.DEFAULT_PATH_SEPARATOR, fsSeparator, ELEMENT_FILE_EXTENSION);
    }

    @Override
    public String getModuleMetadataFilePath(String moduleName, String fsSeparator)
    {
        StringBuilder builder = new StringBuilder(moduleName.length() + MODULE_FILE_EXTENSION.length() + (MODULE_FILE_DIR.size() * fsSeparator.length()) + 24);
        MODULE_FILE_DIR.forEach(name -> builder.append(name).append(fsSeparator));
        return FilePathTools.appendFilePathName(builder, moduleName, MODULE_FILE_EXTENSION).toString();
    }
}
