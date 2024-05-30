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

package org.finos.legend.pure.m3.serialization.compiler.metadata;

import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.set.MutableSet;
import org.finos.legend.pure.m3.navigation.graph.GraphPath;
import org.finos.legend.pure.m3.serialization.compiler.ExtensibleSerializer;
import org.finos.legend.pure.m3.serialization.compiler.strings.StringIndexer;
import org.finos.legend.pure.m4.serialization.Reader;
import org.finos.legend.pure.m4.serialization.Writer;

import java.util.Arrays;

public class ModuleMetadataSerializer extends ExtensibleSerializer<ModuleMetadataSerializerExtension>
{
    private final StringIndexer stringIndexer;

    private ModuleMetadataSerializer(Iterable<? extends ModuleMetadataSerializerExtension> extensions, int defaultVersion, StringIndexer stringIndexer)
    {
        super(extensions, defaultVersion);
        this.stringIndexer = stringIndexer;
    }

    public void serialize(Writer writer, ModuleMetadata metadata)
    {
        serialize(writer, metadata, getDefaultExtension());
    }

    public void serialize(Writer writer, ModuleMetadata metadata, int version)
    {
        serialize(writer, metadata, getExtension(version));
    }

    private void serialize(Writer writer, ModuleMetadata metadata, ModuleMetadataSerializerExtension extension)
    {
        writer.writeInt(extension.version());
        Writer stringIndexedWriter = this.stringIndexer.writeStringIndex(writer, collectStrings(metadata));
        extension.serialize(stringIndexedWriter, metadata);
    }

    public ModuleMetadata deserialize(Reader reader)
    {
        int version = reader.readInt();
        ModuleMetadataSerializerExtension extension = getExtension(version);
        Reader stringIndexedReader = this.stringIndexer.readStringIndex(reader);
        return extension.deserialize(stringIndexedReader);
    }

    private static MutableSet<String> collectStrings(ModuleMetadata metadata)
    {
        MutableSet<String> stringSet = Sets.mutable.empty();
        stringSet.add(metadata.getName());
        metadata.forEachElement(element ->
        {
            stringSet.add(element.getPath());
            stringSet.add(element.getClassifierPath());
            stringSet.add(element.getSourceInformation().getSourceId());
            element.getExternalReferences().forEach(extRef ->
            {
                stringSet.add(extRef.getReferenceId());
                GraphPath graphPath = extRef.getPath();
                stringSet.add(graphPath.getStartNodePath());
                graphPath.forEachEdge(new GraphPath.EdgeConsumer()
                {
                    @Override
                    protected void accept(GraphPath.ToOnePropertyEdge edge)
                    {
                        stringSet.add(edge.getProperty());
                    }

                    @Override
                    protected void accept(GraphPath.ToManyPropertyAtIndexEdge edge)
                    {
                        stringSet.add(edge.getProperty());
                    }

                    @Override
                    protected void accept(GraphPath.ToManyPropertyWithStringKeyEdge edge)
                    {
                        stringSet.add(edge.getProperty());
                        stringSet.add(edge.getKeyProperty());
                        stringSet.add(edge.getKey());
                    }
                });
            });
        });
        return stringSet;
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<ModuleMetadataSerializerExtension, ModuleMetadataSerializer>
    {
        private StringIndexer stringIndexer;

        private Builder()
        {
        }

        public Builder withExtension(ModuleMetadataSerializerExtension extension)
        {
            addExtension(extension);
            return this;
        }

        public Builder withExtensions(Iterable<? extends ModuleMetadataSerializerExtension> extensions)
        {
            addExtensions(extensions);
            return this;
        }

        public Builder withExtensions(ModuleMetadataSerializerExtension... extensions)
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

        @Override
        protected ModuleMetadataSerializer build(Iterable<ModuleMetadataSerializerExtension> extensions, int defaultVersion)
        {
            // if no string indexer has been specified, use the default
            return new ModuleMetadataSerializer(extensions, defaultVersion, (this.stringIndexer == null) ? StringIndexer.defaultStringIndexer() : this.stringIndexer);
        }

        @Override
        protected Class<ModuleMetadataSerializerExtension> getExtensionClass()
        {
            return ModuleMetadataSerializerExtension.class;
        }
    }
}
