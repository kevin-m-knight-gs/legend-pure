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

package org.finos.legend.pure.m3.serialization.compiler.element;

import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.serialization.compiler.ExtensibleSerializer;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIdProvider;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIdProviders;
import org.finos.legend.pure.m3.serialization.compiler.strings.StringIndexer;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.serialization.Reader;
import org.finos.legend.pure.m4.serialization.Writer;

import java.util.Arrays;
import java.util.Objects;

public class ConcreteElementSerializer extends ExtensibleSerializer<ConcreteElementSerializerExtension>
{
    private static final long LEGEND_ENTITY_SIGNATURE = Long.parseLong("LegendEntity", 36);

    private final ReferenceIdProvider referenceIdProvider;
    private final StringIndexer stringIndexer;
    private final ProcessorSupport processorSupport;

    private ConcreteElementSerializer(Iterable<? extends ConcreteElementSerializerExtension> extensions, int defaultVersion, StringIndexer stringIndexer, ReferenceIdProvider referenceIdProvider, ProcessorSupport processorSupport)
    {
        super(extensions, defaultVersion);
        this.stringIndexer = stringIndexer;
        this.referenceIdProvider = referenceIdProvider;
        this.processorSupport = processorSupport;
    }

    public void serialize(Writer writer, CoreInstance element)
    {
        serialize(writer, element, getDefaultExtension());
    }

    public void serialize(Writer writer, CoreInstance element, int version)
    {
        serialize(writer, element, getExtension(version));
    }

    private void serialize(Writer writer, CoreInstance element, ConcreteElementSerializerExtension extension)
    {
        writer.writeLong(LEGEND_ENTITY_SIGNATURE);
        writer.writeInt(extension.version());
        extension.serialize(writer, element, new SerializationContext(this.stringIndexer, this.referenceIdProvider, this.processorSupport));
    }

    public DeserializedConcreteElement deserialize(Reader reader)
    {
        long signature = reader.readLong();
        if (signature != LEGEND_ENTITY_SIGNATURE)
        {
            throw new IllegalArgumentException("Invalid file format: not a Legend concrete element file");
        }
        int version = reader.readInt();
        ConcreteElementSerializerExtension extension = getExtension(version);
        return extension.deserialize(reader, new SerializationContext(this.stringIndexer, this.referenceIdProvider, this.processorSupport));
    }

    public static Builder builder(ProcessorSupport processorSupport)
    {
        return new Builder(processorSupport);
    }

    public static class Builder extends AbstractBuilder<ConcreteElementSerializerExtension, ConcreteElementSerializer>
    {
        private StringIndexer stringIndexer;
        private ReferenceIdProvider referenceIdProvider;
        private final ProcessorSupport processorSupport;

        private Builder(ProcessorSupport processorSupport)
        {
            this.processorSupport = Objects.requireNonNull(processorSupport);
        }

        public Builder withExtension(ConcreteElementSerializerExtension extension)
        {
            addExtension(extension);
            return this;
        }

        public Builder withExtensions(Iterable<? extends ConcreteElementSerializerExtension> extensions)
        {
            addExtensions(extensions);
            return this;
        }

        public Builder withExtensions(ConcreteElementSerializerExtension... extensions)
        {
            return withExtensions(Arrays.asList(extensions));
        }

        public Builder withLoadedExtensions(ClassLoader classLoader)
        {
            loadExtensions(classLoader);
            return this;
        }

        public Builder withLoadedExtensions()
        {
            loadExtensions();
            return this;
        }

        public Builder withDefaultVersion(int defaultVersion)
        {
            setDefaultVersion(defaultVersion);
            return this;
        }

        public Builder withStringIndexer(StringIndexer stringIndexer)
        {
            this.stringIndexer = stringIndexer;
            return this;
        }

        public Builder withReferenceIdProvider(ReferenceIdProvider referenceIdProvider)
        {
            this.referenceIdProvider = referenceIdProvider;
            return this;
        }

        @Override
        protected ConcreteElementSerializer build(Iterable<ConcreteElementSerializerExtension> extensions, int defaultVersion)
        {
            return new ConcreteElementSerializer(extensions, defaultVersion, resolveStringIndexer(), resolveReferenceIdProvider(), this.processorSupport);
        }

        private ReferenceIdProvider resolveReferenceIdProvider()
        {
            // If no reference id provider has been specified, create one
            return (this.referenceIdProvider == null) ?
                   ReferenceIdProviders.fromProcessorSupport(this.processorSupport, true) :
                   this.referenceIdProvider;
        }

        private StringIndexer resolveStringIndexer()
        {
            // if no string indexer has been specified, use the default
            return (this.stringIndexer == null) ? StringIndexer.defaultStringIndexer() : this.stringIndexer;
        }

        @Override
        protected Class<ConcreteElementSerializerExtension> getExtensionClass()
        {
            return ConcreteElementSerializerExtension.class;
        }
    }
}
