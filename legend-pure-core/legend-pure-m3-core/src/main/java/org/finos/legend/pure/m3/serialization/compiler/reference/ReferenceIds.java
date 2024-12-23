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

package org.finos.legend.pure.m3.serialization.compiler.reference;

import org.eclipse.collections.api.map.ConcurrentMutableMap;
import org.eclipse.collections.api.map.primitive.IntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;

import java.util.Objects;
import java.util.ServiceLoader;

public class ReferenceIds
{
    private final ProcessorSupport processorSupport;
    private final IntObjectMap<ReferenceIdExtension> extensions;
    private final int defaultVersion;
    private final ConcurrentMutableMap<Integer, ReferenceIdProvider> providersCache = ConcurrentHashMap.newMap();
    private final ConcurrentMutableMap<Integer, ReferenceIdResolver> resolversCache = ConcurrentHashMap.newMap();

    private ReferenceIds(ProcessorSupport processorSupport, IntObjectMap<ReferenceIdExtension> extensions, Integer defaultVersion)
    {
        this.processorSupport = processorSupport;
        this.extensions = extensions;
        if (defaultVersion == null)
        {
            this.defaultVersion = this.extensions.keySet().max();
        }
        else
        {
            this.defaultVersion = defaultVersion;
            if (!this.extensions.containsKey(this.defaultVersion))
            {
                throw new IllegalArgumentException("Default version " + this.defaultVersion + " is unknown");
            }
        }
    }

    public int getDefaultVersion()
    {
        return this.defaultVersion;
    }

    public boolean isVersionAvailable(int version)
    {
        return this.extensions.containsKey(version);
    }

    public ReferenceIdExtension getExtension(int version)
    {
        ReferenceIdExtension extension = this.extensions.get(version);
        if (extension == null)
        {
            throw new IllegalArgumentException("Unknown extension: " + version);
        }
        return extension;
    }

    public ReferenceIdExtension getExtension(Integer version)
    {
        return (version == null) ? getDefaultExtension() : getExtension(version.intValue());
    }

    public ReferenceIdExtension getDefaultExtension()
    {
        return getExtension(getDefaultVersion());
    }

    public ReferenceIdProvider provider(int version)
    {
        return this.providersCache.getIfAbsentPut(version, () -> getExtension(version).newProvider(processorSupport));
    }

    public ReferenceIdProvider provider(Integer version)
    {
        return provider((version == null) ? getDefaultVersion() : version);
    }

    public ReferenceIdProvider provider()
    {
        return provider(getDefaultVersion());
    }

    public ReferenceIdResolver resolver(int version)
    {
        return this.resolversCache.getIfAbsentPut(version, () -> getExtension(version).newResolver(processorSupport));
    }

    public ReferenceIdResolver resolver(Integer version)
    {
        return resolver((version == null) ? getDefaultVersion() : version);
    }

    public ReferenceIdResolver resolver()
    {
        return resolver(getDefaultVersion());
    }

    public static Builder builder(ProcessorSupport processorSupport)
    {
        return new Builder(processorSupport);
    }

    public static class Builder
    {
        private final ProcessorSupport processorSupport;
        private final MutableIntObjectMap<ReferenceIdExtension> extensions = IntObjectMaps.mutable.empty();
        private Integer defaultVersion;

        private Builder(ProcessorSupport processorSupport)
        {
            this.processorSupport = Objects.requireNonNull(processorSupport, "processor support is required");
        }

        public void addExtension(ReferenceIdExtension extension)
        {
            Objects.requireNonNull(extension, "extension may not be null");
            if (this.extensions.getIfAbsentPut(extension.version(), extension) != extension)
            {
                throw new IllegalArgumentException("There is already an extension for version " + extension.version());
            }
        }

        public Builder withExtension(ReferenceIdExtension extension)
        {
            addExtension(extension);
            return this;
        }

        public void addExtensions(Iterable<? extends ReferenceIdExtension> extensions)
        {
            extensions.forEach(this::addExtension);
        }

        public Builder withExtensions(Iterable<? extends ReferenceIdExtension> extensions)
        {
            addExtensions(extensions);
            return this;
        }

        public void loadExtensions(ClassLoader classLoader)
        {
            addExtensions(ServiceLoader.load(ReferenceIdExtension.class, classLoader));
        }

        public void loadExtensions()
        {
            addExtensions(ServiceLoader.load(ReferenceIdExtension.class));
        }

        public Builder withAvailableExtensions(ClassLoader classLoader)
        {
            loadExtensions(classLoader);
            return this;
        }

        public Builder withAvailableExtensions()
        {
            loadExtensions();
            return this;
        }

        public void setDefaultVersion(int defaultVersion)
        {
            this.defaultVersion = defaultVersion;
        }

        public void clearDefaultVersion()
        {
            this.defaultVersion = null;
        }

        public Builder withDefaultVersion(Integer defaultVersion)
        {
            if (defaultVersion == null)
            {
                clearDefaultVersion();
            }
            else
            {
                setDefaultVersion(defaultVersion);
            }
            return this;
        }

        public ReferenceIds build()
        {
            if (this.extensions.isEmpty())
            {
                throw new IllegalStateException("At least one extension is required");
            }
            return new ReferenceIds(this.processorSupport, this.extensions.toImmutable(), this.defaultVersion);
        }
    }
}
