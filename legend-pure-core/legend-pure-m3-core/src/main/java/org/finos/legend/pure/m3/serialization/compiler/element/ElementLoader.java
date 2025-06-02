// Copyright 2025 Goldman Sachs
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

package org.finos.legend.pure.m3.serialization.compiler.element;

import org.eclipse.collections.api.map.ConcurrentMutableMap;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.finos.legend.pure.m3.serialization.compiler.file.FileSerializer;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ConcreteElementMetadata;
import org.finos.legend.pure.m3.serialization.compiler.metadata.MetadataIndex;
import org.finos.legend.pure.m3.serialization.compiler.metadata.PackageableElementMetadata;
import org.finos.legend.pure.m3.serialization.compiler.metadata.VirtualPackageMetadata;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIds;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public abstract class ElementLoader
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ElementLoader.class);

    private final MetadataIndex index;
    private final ElementBuilder builder;
    private final ReferenceIds referenceIds;
    private final ConcurrentMutableMap<String, AtomicReference<CoreInstance>> cache = ConcurrentHashMap.newMap();

    private ElementLoader(MetadataIndex index, ElementBuilder builder, ReferenceIds referenceIds)
    {
        this.index = Objects.requireNonNull(index);
        this.builder = Objects.requireNonNull(builder);
        this.referenceIds = Objects.requireNonNull(referenceIds);
    }

    /**
     * Check if an element exists. This will not load the element.
     *
     * @param path package path of the element
     * @return whether the element exists
     */
    public boolean elementExists(String path)
    {
        return elementPresentInMetadata(path);
    }

    /**
     * Load an element. Returns null if the element does not exist. This method is thread safe, and a given element will
     * be loaded at most once.
     *
     * @param path package path of the element
     * @return the loaded element, or null if it does not exist
     */
    public CoreInstance loadElement(String path)
    {
        if (path == null)
        {
            return null;
        }

        AtomicReference<CoreInstance> ref = this.cache.get(path);
        if (ref == null)
        {
            if (!elementPresentInMetadata(path))
            {
                return null;
            }
            ref = this.cache.getIfAbsentPut(path, new AtomicReference<>());
        }

        CoreInstance value = ref.get();
        if (value == null)
        {
            synchronized (ref)
            {
                if ((value = ref.get()) == null)
                {
                    ref.set(value = load(path));
                }
            }
        }
        return value;
    }

    private boolean elementPresentInMetadata(String path)
    {
        return this.index.hasElement(path) || this.index.hasPackage(path);
    }

    private CoreInstance load(String path)
    {
        long start = System.nanoTime();
        try
        {
            ConcreteElementMetadata elementMetadata = this.index.getElement(path);
            if (elementMetadata != null)
            {
                LOGGER.debug("Loading concrete element {}", path);
                return this.builder.buildConcreteElement(elementMetadata, this.index, this.referenceIds, () -> deserialize(path));
            }

            PackageableElementMetadata packageMetadata = this.index.getPackageMetadata(path);
            if (packageMetadata instanceof VirtualPackageMetadata)
            {
                LOGGER.debug("Loading virtual package {}", path);
                return this.builder.buildVirtualPackage((VirtualPackageMetadata) packageMetadata, this.index, this.referenceIds);
            }

            // This should not be possible, but just in case ...
            throw new IllegalStateException(path + " is neither a concrete element nor a virtual package");
        }
        catch (Throwable t)
        {
            LOGGER.error("Error loading {}", path, t);
            throw t;
        }
        finally
        {
            long end = System.nanoTime();
            LOGGER.debug("Finished loading {} in {}s", path, (end - start) / 1_000_000_000.0);
        }
    }

    private DeserializedConcreteElement deserialize(String path)
    {
        long start = System.nanoTime();
        LOGGER.debug("Deserializing {}", path);
        try
        {
            return deserializeConcreteElement(path);
        }
        catch (Throwable t)
        {
            LOGGER.error("Error deserializing {}", path, t);
            throw t;
        }
        finally
        {
            long end = System.nanoTime();
            LOGGER.debug("Finished deserializing {} in {}s", path, (end - start) / 1_000_000_000.0);
        }
    }

    abstract DeserializedConcreteElement deserializeConcreteElement(String path);

    private static class ClassLoaderElementLoader extends ElementLoader
    {
        private final FileSerializer fileSerializer;
        private final ClassLoader classLoader;

        private ClassLoaderElementLoader(MetadataIndex index, ElementBuilder builder, ReferenceIds referenceIds, FileSerializer fileSerializer, ClassLoader classLoader)
        {
            super(index, builder, referenceIds);
            this.fileSerializer = Objects.requireNonNull(fileSerializer);
            this.classLoader = Objects.requireNonNull(classLoader);
        }

        @Override
        DeserializedConcreteElement deserializeConcreteElement(String path)
        {
            return this.fileSerializer.deserializeElement(this.classLoader, path);
        }
    }

    private static class DirectoryElementLoader extends ElementLoader
    {
        private final FileSerializer fileSerializer;
        private final Path directory;

        private DirectoryElementLoader(MetadataIndex index, ElementBuilder builder, ReferenceIds referenceIds, FileSerializer fileSerializer, Path directory)
        {
            super(index, builder, referenceIds);
            this.fileSerializer = Objects.requireNonNull(fileSerializer);
            this.directory = Objects.requireNonNull(directory);
        }

        @Override
        DeserializedConcreteElement deserializeConcreteElement(String path)
        {
            return this.fileSerializer.deserializeElement(this.directory, path);
        }
    }

    public static ElementLoader fromClassLoader(MetadataIndex index, ElementBuilder builder, ReferenceIds referenceIds, FileSerializer fileSerializer, ClassLoader classLoader)
    {
        return new ClassLoaderElementLoader(index, builder, referenceIds, fileSerializer, classLoader);
    }

    public static ElementLoader fromDirectory(MetadataIndex index, ElementBuilder builder, ReferenceIds referenceIds, FileSerializer fileSerializer, Path directory)
    {
        return new DirectoryElementLoader(index, builder, referenceIds, fileSerializer, directory);
    }
}
