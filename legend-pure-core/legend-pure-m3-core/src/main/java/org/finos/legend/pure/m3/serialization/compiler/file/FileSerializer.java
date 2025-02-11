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

import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;
import org.finos.legend.pure.m3.serialization.compiler.element.ConcreteElementSerializer;
import org.finos.legend.pure.m3.serialization.compiler.element.DeserializedConcreteElement;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ModuleMetadata;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ModuleMetadataSerializer;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.serialization.Reader;
import org.finos.legend.pure.m4.serialization.Writer;
import org.finos.legend.pure.m4.serialization.binary.BinaryReaders;
import org.finos.legend.pure.m4.serialization.binary.BinaryWriters;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileSerializer
{
    private final FilePathProvider filePathProvider;
    private final ConcreteElementSerializer elementSerializer;
    private final ModuleMetadataSerializer moduleSerializer;

    private FileSerializer(FilePathProvider filePathProvider, ConcreteElementSerializer elementSerializer, ModuleMetadataSerializer moduleSerializer)
    {
        this.filePathProvider = filePathProvider;
        this.elementSerializer = elementSerializer;
        this.moduleSerializer = moduleSerializer;
    }

    // Deserialize element from directory

    public boolean elementExists(Path directory, String elementPath)
    {
        return elementExists(directory, elementPath, this.filePathProvider.getDefaultVersion());
    }

    public boolean elementExists(Path directory, String elementPath, int filePathVersion)
    {
        Objects.requireNonNull(directory, "directory is required");
        Objects.requireNonNull(elementPath, "element path is required");
        return Files.exists(this.filePathProvider.getElementFilePath(directory, elementPath, filePathVersion));
    }

    /**
     * Deserialize an element from a file in a directory. Throws an {@link ElementNotFoundException} if the element
     * cannot be found.
     *
     * @param directory   directory to search for the element file
     * @param elementPath element path
     * @return deserialized element
     * @throws ElementNotFoundException if the element cannot be found
     */
    public DeserializedConcreteElement deserializeElement(Path directory, String elementPath)
    {
        return deserializeElement(directory, elementPath, this.filePathProvider.getDefaultVersion());
    }

    /**
     * Deserialize an element from a file in a directory using the given file path version. Throws an
     * {@link ElementNotFoundException} if the element cannot be found.
     *
     * @param directory       directory to search for the element file
     * @param elementPath     element path
     * @param filePathVersion file path version
     * @return deserialized element
     * @throws ElementNotFoundException if the element cannot be found
     */
    public DeserializedConcreteElement deserializeElement(Path directory, String elementPath, int filePathVersion)
    {
        Objects.requireNonNull(directory, "directory is required");
        Objects.requireNonNull(elementPath, "element path is required");

        Path filePath = this.filePathProvider.getElementFilePath(directory, elementPath, filePathVersion);
        try (Reader reader = BinaryReaders.newBinaryReader(new BufferedInputStream(Files.newInputStream(filePath))))
        {
            return this.elementSerializer.deserialize(reader);
        }
        catch (NoSuchFileException | FileNotFoundException e)
        {
            throw new ElementNotFoundException(elementPath, "cannot find file " + filePath, e);
        }
        catch (Exception e)
        {
            if (Files.notExists(filePath))
            {
                throw new ElementNotFoundException(elementPath, "cannot find file " + filePath, e);
            }
            StringBuilder builder = new StringBuilder("Error deserializing element ").append(elementPath).append(" from ").append(filePath);
            String eMessage = e.getMessage();
            if (eMessage != null)
            {
                builder.append(": ").append(eMessage);
            }
            throw (e instanceof IOException) ? new UncheckedIOException(builder.toString(), (IOException) e) : new RuntimeException(builder.toString(), e);
        }
    }

    // Deserialize element from ClassLoader

    public boolean elementExists(ClassLoader classLoader, String elementPath)
    {
        return elementExists(classLoader, elementPath, this.filePathProvider.getDefaultVersion());
    }

    public boolean elementExists(ClassLoader classLoader, String elementPath, int filePathVersion)
    {
        Objects.requireNonNull(classLoader, "class loader is required");
        Objects.requireNonNull(elementPath, "element path is required");
        return classLoader.getResource(this.filePathProvider.getElementResourceName(elementPath, filePathVersion)) != null;
    }

    /**
     * Deserialize an element from a resource in a class loader. Throws an {@link ElementNotFoundException} if the
     * element cannot be found.
     *
     * @param classLoader class loader to search for the element resource
     * @param elementPath element path
     * @return deserialized element
     * @throws ElementNotFoundException if the element cannot be found
     */
    public DeserializedConcreteElement deserializeElement(ClassLoader classLoader, String elementPath)
    {
        return deserializeElement(classLoader, elementPath, this.filePathProvider.getDefaultVersion());
    }

    /**
     * Deserialize an element from a resource in a class loader using the given file path version. Throws an
     * {@link ElementNotFoundException} if the element cannot be found.
     *
     * @param classLoader     class loader to search for the element resource
     * @param elementPath     element path
     * @param filePathVersion file path version
     * @return deserialized element
     * @throws ElementNotFoundException if the element cannot be found
     */
    public DeserializedConcreteElement deserializeElement(ClassLoader classLoader, String elementPath, int filePathVersion)
    {
        Objects.requireNonNull(classLoader, "class loader is required");
        Objects.requireNonNull(elementPath, "element path is required");

        String resourceName = this.filePathProvider.getElementResourceName(elementPath, filePathVersion);
        URL url = classLoader.getResource(resourceName);
        if (url == null)
        {
            throw new ElementNotFoundException(elementPath, "cannot find resource " + resourceName);
        }
        try (Reader reader = BinaryReaders.newBinaryReader(url.openStream()))
        {
            return this.elementSerializer.deserialize(reader);
        }
        catch (Exception e)
        {
            StringBuilder builder = new StringBuilder("Error deserializing element ").append(elementPath)
                    .append(" from resource ").append(resourceName)
                    .append(" (").append(url).append(")");
            String eMessage = e.getMessage();
            if (eMessage != null)
            {
                builder.append(": ").append(eMessage);
            }
            throw (e instanceof IOException) ? new UncheckedIOException(builder.toString(), (IOException) e) : new RuntimeException(builder.toString(), e);
        }
    }

    // Serialize element to directory

    public Path serializeElement(Path directory, CoreInstance element)
    {
        return serializeElement(directory, element, this.filePathProvider.getDefaultVersion(), this.elementSerializer.getDefaultVersion(), this.elementSerializer.getReferenceIds().getDefaultVersion());
    }

    public Path serializeElement(Path directory, CoreInstance element, int filePathVersion, int serializerVersion, int referenceIdVersion)
    {
        Objects.requireNonNull(directory, "directory is required");
        Objects.requireNonNull(element, "element is required");

        String elementPath = PackageableElement.getUserPathForPackageableElement(element);
        Path filePath = this.filePathProvider.getElementFilePath(directory, elementPath, filePathVersion);
        try
        {
            Files.createDirectories(filePath.getParent());
            try (Writer writer = BinaryWriters.newBinaryWriter(new BufferedOutputStream(Files.newOutputStream(filePath))))
            {
                this.elementSerializer.serialize(writer, element, serializerVersion, referenceIdVersion);
            }
        }
        catch (Exception e)
        {
            StringBuilder builder = new StringBuilder("Error serializing element ").append(elementPath).append(" to ").append(filePath);
            String eMessage = e.getMessage();
            if (eMessage != null)
            {
                builder.append(": ").append(eMessage);
            }
            throw (e instanceof IOException) ? new UncheckedIOException(builder.toString(), (IOException) e) : new RuntimeException(builder.toString(), e);
        }
        return filePath;
    }

    // Serialize element to zip

    public String serializeElement(ZipOutputStream zipStream, CoreInstance element)
    {
        return serializeElement(zipStream, element, this.filePathProvider.getDefaultVersion(), this.elementSerializer.getDefaultVersion(), this.elementSerializer.getReferenceIds().getDefaultVersion());
    }

    public String serializeElement(ZipOutputStream zipStream, CoreInstance element, int filePathVersion, int serializerVersion, int referenceIdVersion)
    {
        Objects.requireNonNull(zipStream, "directory is required");
        Objects.requireNonNull(element, "element is required");

        String elementPath = PackageableElement.getUserPathForPackageableElement(element);
        String entryName = this.filePathProvider.getElementFilePath(elementPath, "/", filePathVersion);
        try
        {
            zipStream.putNextEntry(new ZipEntry(entryName));
            try (Writer writer = BinaryWriters.newBinaryWriter(zipStream, false))
            {
                this.elementSerializer.serialize(writer, element, serializerVersion, referenceIdVersion);
            }
            zipStream.closeEntry();
        }
        catch (Exception e)
        {
            StringBuilder builder = new StringBuilder("Error serializing element ").append(elementPath).append(" to ").append(entryName);
            String eMessage = e.getMessage();
            if (eMessage != null)
            {
                builder.append(": ").append(eMessage);
            }
            throw (e instanceof IOException) ? new UncheckedIOException(builder.toString(), (IOException) e) : new RuntimeException(builder.toString(), e);
        }
        return entryName;
    }

    // Deserialize module metadata from directory

    public boolean moduleMetadataExists(Path directory, String moduleName)
    {
        return moduleMetadataExists(directory, moduleName, this.filePathProvider.getDefaultVersion());
    }

    public boolean moduleMetadataExists(Path directory, String moduleName, int filePathVersion)
    {
        Objects.requireNonNull(directory, "directory is required");
        Objects.requireNonNull(moduleName, "module name is required");
        return Files.exists(this.filePathProvider.getModuleMetadataFilePath(directory, moduleName, filePathVersion));
    }

    /**
     * Deserialize module metadata from a file in a directory. Throws a {@link ModuleMetadataNotFoundException} if the
     * module metadata cannot be found.
     *
     * @param directory  directory to search for the module metadata file
     * @param moduleName module name
     * @return module metadata
     * @throws ModuleMetadataNotFoundException if the module metadata cannot be found
     */
    public ModuleMetadata deserializeModuleMetadata(Path directory, String moduleName)
    {
        return deserializeModuleMetadata(directory, moduleName, this.filePathProvider.getDefaultVersion());
    }

    /**
     * Deserialize module metadata from a file in a directory using the given file path version. Throws a
     * {@link ModuleMetadataNotFoundException} if the module metadata cannot be found.
     *
     * @param directory       directory to search for the module metadata file
     * @param moduleName      module name
     * @param filePathVersion file path version
     * @return module metadata
     * @throws ModuleMetadataNotFoundException if the module metadata cannot be found
     */
    public ModuleMetadata deserializeModuleMetadata(Path directory, String moduleName, int filePathVersion)
    {
        Objects.requireNonNull(directory, "directory is required");
        Objects.requireNonNull(moduleName, "module name is required");

        Path filePath = this.filePathProvider.getModuleMetadataFilePath(directory, moduleName, filePathVersion);
        try (Reader reader = BinaryReaders.newBinaryReader(new BufferedInputStream(Files.newInputStream(filePath))))
        {
            return this.moduleSerializer.deserialize(reader);
        }
        catch (NoSuchFileException | FileNotFoundException e)
        {
            throw new ModuleMetadataNotFoundException(moduleName, "cannot find file " + filePath, e);
        }
        catch (Exception e)
        {
            if (Files.notExists(filePath))
            {
                throw new ModuleMetadataNotFoundException(moduleName, "cannot find file " + filePath, e);
            }
            StringBuilder builder = new StringBuilder("Error deserializing metadata for module ").append(moduleName).append(" from ").append(filePath);
            String eMessage = e.getMessage();
            if (eMessage != null)
            {
                builder.append(": ").append(eMessage);
            }
            throw (e instanceof IOException) ? new UncheckedIOException(builder.toString(), (IOException) e) : new RuntimeException(builder.toString(), e);
        }
    }

    // Deserialize module metadata from ClassLoader

    public boolean moduleMetadataExists(ClassLoader classLoader, String moduleName)
    {
        return moduleMetadataExists(classLoader, moduleName, this.filePathProvider.getDefaultVersion());
    }

    public boolean moduleMetadataExists(ClassLoader classLoader, String moduleName, int filePathVersion)
    {
        Objects.requireNonNull(classLoader, "class loader is required");
        Objects.requireNonNull(moduleName, "module name is required");
        return classLoader.getResource(this.filePathProvider.getModuleMetadataResourceName(moduleName, filePathVersion)) != null;
    }

    /**
     * Deserialize module metadata from a resource in a class loader. Throws a {@link ModuleMetadataNotFoundException}
     * if the module metadata cannot be found.
     *
     * @param classLoader class loader to search for the module metadata resource
     * @param moduleName  module name
     * @return module metadata
     * @throws ModuleMetadataNotFoundException if the module metadata cannot be found
     */
    public ModuleMetadata deserializeModuleMetadata(ClassLoader classLoader, String moduleName)
    {
        return deserializeModuleMetadata(classLoader, moduleName, this.filePathProvider.getDefaultVersion());
    }

    /**
     * Deserialize module metadata from a resource in a class loader using the given file path version. Throws a
     * {@link ModuleMetadataNotFoundException} if the module metadata cannot be found.
     *
     * @param classLoader     class loader to search for the module metadata resource
     * @param moduleName      module name
     * @param filePathVersion file path version
     * @return module metadata
     * @throws ModuleMetadataNotFoundException if the module metadata cannot be found
     */
    public ModuleMetadata deserializeModuleMetadata(ClassLoader classLoader, String moduleName, int filePathVersion)
    {
        Objects.requireNonNull(classLoader, "class loader is required");
        Objects.requireNonNull(moduleName, "module name is required");

        String resourceName = this.filePathProvider.getModuleMetadataResourceName(moduleName, filePathVersion);
        URL url = classLoader.getResource(resourceName);
        if (url == null)
        {
            throw new ModuleMetadataNotFoundException(moduleName, "cannot find resource " + resourceName);
        }
        try (Reader reader = BinaryReaders.newBinaryReader(url.openStream()))
        {
            return this.moduleSerializer.deserialize(reader);
        }
        catch (Exception e)
        {
            StringBuilder builder = new StringBuilder("Error deserializing metadata for module ").append(moduleName)
                    .append(" from resource ").append(resourceName)
                    .append(" (").append(url).append(")");
            String eMessage = e.getMessage();
            if (eMessage != null)
            {
                builder.append(": ").append(eMessage);
            }
            throw (e instanceof IOException) ? new UncheckedIOException(builder.toString(), (IOException) e) : new RuntimeException(builder.toString(), e);
        }
    }

    // Serialize module metadata to directory

    public Path serializeModuleMetadata(Path directory, ModuleMetadata moduleMetadata)
    {
        return serializeModuleMetadata(directory, moduleMetadata, this.filePathProvider.getDefaultVersion(), this.moduleSerializer.getDefaultVersion());
    }

    public Path serializeModuleMetadata(Path directory, ModuleMetadata moduleMetadata, int filePathVersion, int serializerVersion)
    {
        Objects.requireNonNull(directory, "directory is required");
        Objects.requireNonNull(moduleMetadata, "module metadata is required");

        Path filePath = this.filePathProvider.getModuleMetadataFilePath(directory, moduleMetadata.getName(), filePathVersion);
        try
        {
            Files.createDirectories(filePath.getParent());
            try (Writer writer = BinaryWriters.newBinaryWriter(new BufferedOutputStream(Files.newOutputStream(filePath))))
            {
                this.moduleSerializer.serialize(writer, moduleMetadata, serializerVersion);
            }
        }
        catch (Exception e)
        {
            StringBuilder builder = new StringBuilder("Error serializing metadata for module ").append(moduleMetadata.getName()).append(" to ").append(filePath);
            String eMessage = e.getMessage();
            if (eMessage != null)
            {
                builder.append(": ").append(eMessage);
            }
            throw (e instanceof IOException) ? new UncheckedIOException(builder.toString(), (IOException) e) : new RuntimeException(builder.toString(), e);
        }
        return filePath;
    }

    // Serialize module metadata to zip

    public String serializeModuleMetadata(ZipOutputStream zipStream, ModuleMetadata moduleMetadata)
    {
        return serializeModuleMetadata(zipStream, moduleMetadata, this.filePathProvider.getDefaultVersion(), this.moduleSerializer.getDefaultVersion());
    }

    public String serializeModuleMetadata(ZipOutputStream zipStream, ModuleMetadata moduleMetadata, int filePathVersion, int serializerVersion)
    {
        Objects.requireNonNull(zipStream, "zip stream is required");
        Objects.requireNonNull(moduleMetadata, "module metadata is required");

        String entryName = this.filePathProvider.getModuleMetadataFilePath(moduleMetadata.getName(), "/", filePathVersion);
        try
        {
            zipStream.putNextEntry(new ZipEntry(entryName));
            try (Writer writer = BinaryWriters.newBinaryWriter(zipStream, false))
            {
                this.moduleSerializer.serialize(writer, moduleMetadata, serializerVersion);
            }
            zipStream.closeEntry();
        }
        catch (Exception e)
        {
            StringBuilder builder = new StringBuilder("Error serializing metadata for module ").append(moduleMetadata.getName()).append(" to ").append(entryName);
            String eMessage = e.getMessage();
            if (eMessage != null)
            {
                builder.append(": ").append(eMessage);
            }
            throw (e instanceof IOException) ? new UncheckedIOException(builder.toString(), (IOException) e) : new RuntimeException(builder.toString(), e);
        }
        return entryName;
    }

    // Builder

    public static Builder builder()
    {
        return new Builder();
    }

    public static class Builder
    {
        private FilePathProvider filePathProvider;
        private ConcreteElementSerializer elementSerializer;
        private ModuleMetadataSerializer moduleSerializer;

        private Builder()
        {
        }

        public Builder withFilePathProvider(FilePathProvider filePathProvider)
        {
            this.filePathProvider = filePathProvider;
            return this;
        }

        public Builder withConcreteElementSerializer(ConcreteElementSerializer elementSerializer)
        {
            this.elementSerializer = elementSerializer;
            return this;
        }

        public Builder withModuleMetadataSerializer(ModuleMetadataSerializer moduleSerializer)
        {
            this.moduleSerializer = moduleSerializer;
            return this;
        }

        public Builder withSerializers(ConcreteElementSerializer elementSerializer, ModuleMetadataSerializer moduleSerializer)
        {
            return withConcreteElementSerializer(elementSerializer)
                    .withModuleMetadataSerializer(moduleSerializer);
        }

        public FileSerializer build()
        {
            Objects.requireNonNull(this.filePathProvider, "file path provider is required");
            Objects.requireNonNull(this.elementSerializer, "concrete element serializer is required");
            Objects.requireNonNull(this.moduleSerializer, "module serializer is required");
            return new FileSerializer(this.filePathProvider, this.elementSerializer, this.moduleSerializer);
        }
    }
}
