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

package org.finos.legend.pure.m3.serialization.compiler.file;

import org.finos.legend.pure.m3.serialization.compiler.SerializerExtension;

public interface FilePathProviderExtension extends SerializerExtension
{
    /**
     * Get the relative file path for the binary file for the given element. This should be a relative file path, and
     * must not start with the path separator. It should never be null or empty. Each name in the path should be no
     * longer than 255 bytes when encoded in UTF-16.
     *
     * @param elementPath concrete element path
     * @param fsSeparator filesystem path separator
     * @return relative file path
     */
    String getElementFilePath(String elementPath, String fsSeparator);

    /**
     * Get the relative file path for the metadata file for the given module. This should be a relative file path, and
     * must not start with the path separator. Tt should never be null or empty. Each name in the path should be no
     * longer than 255 bytes when encoded in UTF-16.
     *
     * @param moduleName  module name
     * @param fsSeparator filesystem path separator
     * @return relative file path
     */
    String getModuleMetadataFilePath(String moduleName, String fsSeparator);
}
