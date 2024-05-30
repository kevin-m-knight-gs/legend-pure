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

package org.finos.legend.pure.m3.serialization.compiler.strings.v2;

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.map.primitive.MutableObjectIntMap;
import org.eclipse.collections.api.map.primitive.ObjectIntMap;
import org.eclipse.collections.impl.factory.primitive.ObjectIntMaps;
import org.eclipse.collections.impl.utility.ArrayIterate;
import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;
import org.finos.legend.pure.m3.navigation.graph.GraphPath;
import org.finos.legend.pure.m3.serialization.compiler.strings.StringWriter;
import org.finos.legend.pure.m3.serialization.filesystem.usercodestorage.CodeStorageTools;
import org.finos.legend.pure.m3.serialization.filesystem.usercodestorage.RepositoryCodeStorage;
import org.finos.legend.pure.m4.serialization.Writer;
import org.finos.legend.pure.m4.serialization.grammar.StringEscape;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

abstract class StringWriterV2 extends BaseStringIndex implements StringWriter
{
    private final ObjectIntMap<String> stringIndex;

    private StringWriterV2(ObjectIntMap<String> stringIndex)
    {
        this.stringIndex = stringIndex;
    }

    @Override
    public void writeString(Writer writer, String string)
    {
        int id = getStringId(string);
        writeStringId(writer, id);
    }

    protected int getStringId(String string)
    {
        int id = getSpecialStringId(string);
        if (id == 0)
        {
            id = this.stringIndex.getIfAbsent(string, -1);
            if (id == -1)
            {
                throw new IllegalArgumentException("Unknown string: '" + StringEscape.escape(string) + "'");
            }
        }
        return id;
    }

    protected abstract void writeStringId(Writer writer, int id);

    private void serialize(Writer writer, String[] strings, MapIterable<String, StringInfo> stringInfo)
    {
        int len = strings.length;
        writer.writeInt(len);
        StringInfoConsumer consumer = new StringInfoConsumer()
        {
            @Override
            public void accept(SimpleStringInfo info)
            {
                serializeSimpleString(writer, info);
            }

            @Override
            public void accept(SourceIdStringInfo info)
            {
                serializeSourceId(writer, info);
            }

            @Override
            public void accept(PackagePathStringInfo info)
            {
                serializePackagePath(writer, info);
            }

            @Override
            public void accept(GraphPathStringInfo info)
            {
                serializeGraphPath(writer, info);
            }
        };
        ArrayIterate.forEach(strings, s -> stringInfo.get(s).accept(consumer));
    }

    private void serializeSimpleString(Writer writer, SimpleStringInfo stringInfo)
    {
        byte[] bytes = stringInfo.string.getBytes(StandardCharsets.UTF_8);
        int length = bytes.length;
        int lenType = getIntType(length);
        writer.writeByte((byte) (SIMPLE_STRING | lenType));
        writeIntOfType(writer, length, lenType);
        writer.writeBytes(bytes);
    }

    private void serializeSourceId(Writer writer, SourceIdStringInfo stringInfo)
    {
        int length = stringInfo.elements.size();
        int lenType = getIntType(length);
        writer.writeByte((byte) (SOURCE_ID_STRING | lenType));
        writeIntOfType(writer, length, lenType);
        stringInfo.elements.forEach(s -> writeString(writer, s));
    }

    private void serializePackagePath(Writer writer, PackagePathStringInfo stringInfo)
    {
        int length = stringInfo.elements.size();
        int lenType = getIntType(length);
        writer.writeByte((byte) (PACKAGE_PATH_STRING | lenType));
        writeIntOfType(writer, length, lenType);
        stringInfo.elements.forEach(s -> writeString(writer, s));
    }

    private void serializeGraphPath(Writer writer, GraphPathStringInfo stringInfo)
    {
        int length = stringInfo.graphPath.getEdgeCount();
        int lenType = getIntType(length);
        writer.writeByte((byte) (GRAPH_PATH_STRING | lenType));
        writeIntOfType(writer, length, lenType);
        writeString(writer, stringInfo.graphPath.getStartNodePath());
        stringInfo.graphPath.forEachEdge(new GraphPath.EdgeConsumer()
        {
            @Override
            protected void accept(GraphPath.ToOnePropertyEdge edge)
            {
                writer.writeByte((byte) GRAPH_PATH_TO_ONE_EDGE);
                writeString(writer, edge.getProperty());
            }

            @Override
            protected void accept(GraphPath.ToManyPropertyAtIndexEdge edge)
            {
                int index = edge.getIndex();
                int intType = getIntType(index);
                writer.writeByte((byte) (GRAPH_PATH_TO_MANY_INDEX_EDGE | intType));
                writeString(writer, edge.getProperty());
                writeIntOfType(writer, index, intType);
            }

            @Override
            protected void accept(GraphPath.ToManyPropertyWithStringKeyEdge edge)
            {
                writer.writeByte((byte) GRAPH_PATH_TO_MANY_KEY_EDGE);
                writeString(writer, edge.getProperty());
                writeString(writer, edge.getKeyProperty());
                writeString(writer, edge.getKey());
            }
        });
    }

    private static class ByteId extends StringWriterV2
    {
        private ByteId(ObjectIntMap<String> stringIndex)
        {
            super(stringIndex);
        }

        @Override
        public void writeStringArray(Writer writer, String[] strings)
        {
            int length = strings.length;
            byte[] ids = new byte[length];
            for (int i = 0; i < length; i++)
            {
                ids[i] = (byte) getStringId(strings[i]);
            }
            writer.writeByteArray(ids);
        }

        @Override
        protected void writeStringId(Writer writer, int id)
        {
            writer.writeByte((byte) id);
        }
    }

    private static class ShortId extends StringWriterV2
    {
        private ShortId(ObjectIntMap<String> stringIndex)
        {
            super(stringIndex);
        }

        @Override
        public void writeStringArray(Writer writer, String[] strings)
        {
            int length = strings.length;
            short[] ids = new short[length];
            for (int i = 0; i < length; i++)
            {
                ids[i] = (short) getStringId(strings[i]);
            }
            writer.writeShortArray(ids);
        }

        @Override
        protected void writeStringId(Writer writer, int id)
        {
            writer.writeShort((short) id);
        }
    }

    private static class IntId extends StringWriterV2
    {
        private IntId(ObjectIntMap<String> stringIndex)
        {
            super(stringIndex);
        }

        @Override
        public void writeStringArray(Writer writer, String[] strings)
        {
            int length = strings.length;
            int[] ids = new int[length];
            for (int i = 0; i < length; i++)
            {
                ids[i] = getStringId(strings[i]);
            }
            writer.writeIntArray(ids);
        }

        @Override
        protected void writeStringId(Writer writer, int id)
        {
            writer.writeInt(id);
        }
    }

    static StringWriter writeStringIndex(Writer writer, Iterable<String> strings)
    {
        // Prepare string set
        MutableMap<String, StringInfo> stringInfo = Maps.mutable.empty();
        strings.forEach(string -> processString(stringInfo, string));

        // Create sorted array of strings
        String[] stringArray = stringInfo.keySet().toArray(new String[stringInfo.size()]);
        Arrays.sort(stringArray, Comparator.comparing(String::length).thenComparing(Comparator.naturalOrder()));

        // Build string id index
        MutableObjectIntMap<String> stringIndex = ObjectIntMaps.mutable.ofInitialCapacity(stringArray.length);
        ArrayIterate.forEachWithIndex(stringArray, stringIndex::put);
        StringWriterV2 stringWriter = (stringArray.length <= Byte.MAX_VALUE) ?
                                      new ByteId(stringIndex) :
                                      ((stringArray.length <= Short.MAX_VALUE) ?
                                       new ShortId(stringIndex) :
                                       new IntId(stringIndex));

        // Serialize string index
        stringWriter.serialize(writer, stringArray, stringInfo);

        return stringWriter;
    }

    private static void processString(MutableMap<String, StringInfo> stringInfo, String string)
    {
        if (isSpecialString(string) || stringInfo.containsKey(string))
        {
            // either no need to process or already processed
            return;
        }

        if (string.startsWith(RepositoryCodeStorage.PATH_SEPARATOR))
        {
            ListIterable<String> elements = CodeStorageTools.splitPath(string);
            stringInfo.put(string, new SourceIdStringInfo(elements));
            elements.forEach(s -> processString(stringInfo, s));
            return;
        }

        GraphPath path = tryParseGraphPath(string);
        if (path != null)
        {
            stringInfo.put(string, new GraphPathStringInfo(path));
            processString(stringInfo, path.getStartNodePath());
            path.forEachEdge(new GraphPath.EdgeConsumer()
            {
                @Override
                protected void accept(GraphPath.ToOnePropertyEdge edge)
                {
                    processString(stringInfo, edge.getProperty());
                }

                @Override
                protected void accept(GraphPath.ToManyPropertyAtIndexEdge edge)
                {
                    processString(stringInfo, edge.getProperty());
                }

                @Override
                protected void accept(GraphPath.ToManyPropertyWithStringKeyEdge edge)
                {
                    processString(stringInfo, edge.getProperty());
                    processString(stringInfo, edge.getKeyProperty());
                    processString(stringInfo, edge.getKey());
                }
            });
            return;
        }

        if (string.contains(PackageableElement.DEFAULT_PATH_SEPARATOR))
        {
            ListIterable<String> elements = PackageableElement.splitUserPath(string);
            stringInfo.put(string, new PackagePathStringInfo(elements));
            elements.forEach(s -> processString(stringInfo, s));
            return;
        }

        stringInfo.put(string, new SimpleStringInfo(string));
    }

    private static GraphPath tryParseGraphPath(String string)
    {
        // We're only interested in graph paths with edges
        if (string.indexOf('.') > 0)
        {
            try
            {
                GraphPath.Builder builder = GraphPath.builder().fromDescription(string);
                if (builder.getEdges().notEmpty() && string.equals(builder.getDescription()))
                {
                    return builder.build();
                }
            }
            catch (Exception ignore)
            {
                // could not parse
            }
        }
        return null;
    }

    private abstract static class StringInfo
    {
        protected abstract void accept(StringInfoConsumer consumer);
    }

    private static class SimpleStringInfo extends StringInfo
    {
        private final String string;

        private SimpleStringInfo(String string)
        {
            this.string = string;
        }

        @Override
        protected void accept(StringInfoConsumer consumer)
        {
            consumer.accept(this);
        }
    }

    private static class SourceIdStringInfo extends StringInfo
    {
        private final ListIterable<String> elements;

        private SourceIdStringInfo(ListIterable<String> elements)
        {
            this.elements = elements;
        }

        @Override
        protected void accept(StringInfoConsumer consumer)
        {
            consumer.accept(this);
        }
    }

    private static class PackagePathStringInfo extends StringInfo
    {
        private final ListIterable<String> elements;

        private PackagePathStringInfo(ListIterable<String> elements)
        {
            this.elements = elements;
        }

        @Override
        protected void accept(StringInfoConsumer consumer)
        {
            consumer.accept(this);
        }
    }

    private static class GraphPathStringInfo extends StringInfo
    {
        private final GraphPath graphPath;

        private GraphPathStringInfo(GraphPath graphPath)
        {
            this.graphPath = graphPath;
        }

        @Override
        protected void accept(StringInfoConsumer consumer)
        {
            consumer.accept(this);
        }
    }

    private interface StringInfoConsumer
    {
        void accept(SimpleStringInfo info);

        void accept(SourceIdStringInfo info);

        void accept(PackagePathStringInfo info);

        void accept(GraphPathStringInfo info);
    }
}
