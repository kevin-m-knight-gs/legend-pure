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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.ConcurrentMutableMap;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.PackageableElement;
import org.finos.legend.pure.m3.serialization.compiler.file.FileDeserializer;
import org.finos.legend.pure.m3.serialization.compiler.metadata.BackReference;
import org.finos.legend.pure.m3.serialization.compiler.metadata.BackReferenceProvider;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ConcreteElementMetadata;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ElementBackReferenceMetadata;
import org.finos.legend.pure.m3.serialization.compiler.metadata.MetadataIndex;
import org.finos.legend.pure.m3.serialization.compiler.metadata.PackageableElementMetadata;
import org.finos.legend.pure.m3.serialization.compiler.metadata.VirtualPackageMetadata;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIdExtension;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIdResolvers;
import org.finos.legend.pure.m3.tools.ListHelper;
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
    private final ReferenceIdResolvers referenceIds;
    private final ConcurrentMutableMap<String, AtomicReference<PackageableElement>> cache = ConcurrentHashMap.newMap();

    private ElementLoader(MetadataIndex index, ElementBuilder builder, ReferenceIdResolvers.Builder referenceIdsBuilder)
    {
        this.index = Objects.requireNonNull(index);
        this.builder = Objects.requireNonNull(builder);
        this.referenceIds = referenceIdsBuilder.withPackagePathResolver(this::loadElement).build();
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
    public PackageableElement loadElement(String path)
    {
        if (path == null)
        {
            return null;
        }

        AtomicReference<PackageableElement> ref = this.cache.get(path);
        if (ref == null)
        {
            if (!elementPresentInMetadata(path))
            {
                return null;
            }
            ref = this.cache.getIfAbsentPut(path, new AtomicReference<>());
        }

        PackageableElement value = ref.get();
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

    public ReferenceIdResolvers getReferenceIdResolvers()
    {
        return this.referenceIds;
    }

    private boolean elementPresentInMetadata(String path)
    {
        return this.index.hasElement(path) || this.index.hasPackage(path);
    }

    private PackageableElement load(String path)
    {
        long start = System.nanoTime();
        try
        {
            ConcreteElementMetadata elementMetadata = this.index.getElement(path);
            if (elementMetadata != null)
            {
                LOGGER.debug("Loading concrete element {}", path);
                return this.builder.buildConcreteElement(elementMetadata, this.index, this.referenceIds, () -> deserialize(path), () -> deserializeBackReferences(path));
            }

            PackageableElementMetadata packageMetadata = this.index.getPackageMetadata(path);
            if (packageMetadata instanceof VirtualPackageMetadata)
            {
                LOGGER.debug("Loading virtual package {}", path);
                BackReferenceProvider backRefProvider = deserializeBackReferences(path);
                return this.builder.buildVirtualPackage(
                        (VirtualPackageMetadata) packageMetadata,
                        this.index,
                        backRefProvider.getBackReferences(path),
                        this.referenceIds);
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

    private BackReferenceProvider deserializeBackReferences(String path)
    {
        MutableMap<String, MutableList<BackReference>> map = Maps.mutable.empty();
        this.index.getAllModuleNames().forEach(moduleName ->
        {
            if (hasBackReferences(moduleName, path))
            {
                ElementBackReferenceMetadata backReferenceMetadata = deserializeBackReferences(moduleName, path);
                backReferenceMetadata.getInstanceBackReferenceMetadata().forEach(ibr -> map.getIfAbsentPut(ibr.getInstanceReferenceId(), Lists.mutable::empty).addAll(ibr.getBackReferences().castToList()));
            }
        });

        if (map.isEmpty())
        {
            return id -> Lists.fixedSize.empty();
        }

        map.forEachValue(ListHelper::sortAndRemoveDuplicates);
        return id -> map.getIfAbsentValue(id, Lists.fixedSize.empty());
    }

    abstract DeserializedConcreteElement deserializeConcreteElement(String path);

    abstract boolean hasBackReferences(String moduleName, String path);

    abstract ElementBackReferenceMetadata deserializeBackReferences(String moduleName, String path);

    private static class ClassLoaderElementLoader extends ElementLoader
    {
        private final FileDeserializer fileDeserializer;
        private final ClassLoader classLoader;

        private ClassLoaderElementLoader(MetadataIndex index, ElementBuilder builder, ReferenceIdResolvers.Builder referenceIdsBuilder, FileDeserializer fileDeserializer, ClassLoader classLoader)
        {
            super(index, builder, referenceIdsBuilder);
            this.fileDeserializer = Objects.requireNonNull(fileDeserializer);
            this.classLoader = Objects.requireNonNull(classLoader);
        }

        @Override
        DeserializedConcreteElement deserializeConcreteElement(String path)
        {
            return this.fileDeserializer.deserializeElement(this.classLoader, path);
        }

        @Override
        boolean hasBackReferences(String moduleName, String path)
        {
            return this.fileDeserializer.moduleElementBackReferenceMetadataExists(this.classLoader, moduleName, path);
        }

        @Override
        ElementBackReferenceMetadata deserializeBackReferences(String moduleName, String path)
        {
            return this.fileDeserializer.deserializeModuleElementBackReferenceMetadata(this.classLoader, moduleName, path);
        }
    }

    private static class DirectoryElementLoader extends ElementLoader
    {
        private final FileDeserializer fileDeserializer;
        private final Path directory;

        private DirectoryElementLoader(MetadataIndex index, ElementBuilder builder, ReferenceIdResolvers.Builder referenceIdsBuilder, FileDeserializer fileDeserializer, Path directory)
        {
            super(index, builder, referenceIdsBuilder);
            this.fileDeserializer = Objects.requireNonNull(fileDeserializer);
            this.directory = Objects.requireNonNull(directory);
        }

        @Override
        DeserializedConcreteElement deserializeConcreteElement(String path)
        {
            return this.fileDeserializer.deserializeElement(this.directory, path);
        }

        @Override
        boolean hasBackReferences(String moduleName, String path)
        {
            return this.fileDeserializer.moduleElementBackReferenceMetadataExists(this.directory, moduleName, path);
        }

        @Override
        ElementBackReferenceMetadata deserializeBackReferences(String moduleName, String path)
        {
            return this.fileDeserializer.deserializeModuleElementBackReferenceMetadata(this.directory, moduleName, path);
        }
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static class Builder
    {
        private MetadataIndex index;
        private ElementBuilder builder;
        private final ReferenceIdResolvers.Builder referenceIdsBuilder = ReferenceIdResolvers.builder();
        private FileDeserializer fileDeserializer;
        private ClassLoader classLoader;
        private Path directory;

        private Builder()
        {
        }

        public Builder withMetadataIndex(MetadataIndex index)
        {
            this.index = index;
            return this;
        }

        public Builder withElementBuilder(ElementBuilder builder)
        {
            this.builder = builder;
            return this;
        }

        public Builder withFileDeserializer(FileDeserializer fileDeserializer)
        {
            this.fileDeserializer = fileDeserializer;
            return this;
        }

        public Builder withClassLoader(ClassLoader classLoader)
        {
            this.classLoader = classLoader;
            return this;
        }

        public Builder withDirectory(Path directory)
        {
            this.directory = directory;
            return this;
        }

        public Builder withReferenceIdExtension(ReferenceIdExtension extension)
        {
            this.referenceIdsBuilder.addExtension(extension);
            return this;
        }

        public Builder withReferenceIdExtensions(Iterable<? extends ReferenceIdExtension> extensions)
        {
            this.referenceIdsBuilder.addExtensions(extensions);
            return this;
        }

        public Builder withAvailableReferenceIdExtensions(ClassLoader classLoader)
        {
            this.referenceIdsBuilder.loadExtensions(classLoader);
            return this;
        }

        public Builder withDefaultReferenceIdVersion(Integer version)
        {
            this.referenceIdsBuilder.setDefaultVersion(version);
            return this;
        }

        public Builder withAvailableReferenceIdExtensions()
        {
            this.referenceIdsBuilder.loadExtensions();
            return this;
        }

        public ElementLoader build()
        {
            if ((this.classLoader == null) && (this.directory == null))
            {
                throw new IllegalStateException("Either class loader or directory must be provided");
            }
            if ((this.classLoader != null) && (this.directory != null))
            {
                throw new IllegalStateException("Only one of class loader or directory may be provided");
            }
            return (this.classLoader != null) ?
                   new ClassLoaderElementLoader(this.index, this.builder, this.referenceIdsBuilder, this.fileDeserializer, this.classLoader) :
                   new DirectoryElementLoader(this.index, this.builder, this.referenceIdsBuilder, this.fileDeserializer, this.directory);
        }
    }
}
