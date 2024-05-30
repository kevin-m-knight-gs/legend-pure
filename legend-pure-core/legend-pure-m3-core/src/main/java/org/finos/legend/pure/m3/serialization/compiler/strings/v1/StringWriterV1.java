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

package org.finos.legend.pure.m3.serialization.compiler.strings.v1;

import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.map.primitive.MutableObjectIntMap;
import org.eclipse.collections.api.map.primitive.ObjectIntMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.factory.primitive.ObjectIntMaps;
import org.eclipse.collections.impl.utility.ArrayIterate;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.pure.m3.serialization.compiler.strings.StringWriter;
import org.finos.legend.pure.m4.serialization.Writer;
import org.finos.legend.pure.m4.serialization.grammar.StringEscape;

import java.util.Arrays;
import java.util.Comparator;

abstract class StringWriterV1 extends BaseStringIndex implements StringWriter
{
    private final ObjectIntMap<String> stringIndex;

    private StringWriterV1(ObjectIntMap<String> stringIndex)
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

    private void serialize(Writer writer, String[] strings)
    {
        writer.writeStringArray(strings);
    }

    private static class ByteId extends StringWriterV1
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

    private static class ShortId extends StringWriterV1
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

    private static class IntId extends StringWriterV1
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
        MutableSet<String> stringSet = Iterate.reject(strings, BaseStringIndex::isSpecialString, Sets.mutable.empty());

        // Create sorted array of strings
        String[] stringArray = stringSet.toArray(new String[stringSet.size()]);
        Arrays.sort(stringArray, Comparator.comparing(String::length).thenComparing(Comparator.naturalOrder()));

        // Build string id index
        MutableObjectIntMap<String> stringIndex = ObjectIntMaps.mutable.ofInitialCapacity(stringArray.length);
        ArrayIterate.forEachWithIndex(stringArray, stringIndex::put);
        StringWriterV1 stringWriter = (stringArray.length <= Byte.MAX_VALUE) ?
                                      new ByteId(stringIndex) :
                                      ((stringArray.length <= Short.MAX_VALUE) ?
                                       new ShortId(stringIndex) :
                                       new IntId(stringIndex));

        // Serialize string index
        stringWriter.serialize(writer, stringArray);

        return stringWriter;
    }
}
