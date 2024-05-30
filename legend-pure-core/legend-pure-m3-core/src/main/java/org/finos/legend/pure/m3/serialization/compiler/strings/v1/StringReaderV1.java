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

package org.finos.legend.pure.m3.serialization.compiler.strings.v1;

import org.finos.legend.pure.m3.serialization.compiler.strings.StringReader;
import org.finos.legend.pure.m4.serialization.Reader;

abstract class StringReaderV1 extends BaseStringIndex implements StringReader
{
    private final String[] strings;

    private StringReaderV1(String[] strings)
    {
        this.strings = strings;
    }

    @Override
    public String readString(Reader reader)
    {
        int id = readStringId(reader);
        return getString(id);
    }

    protected abstract int readStringId(Reader reader);

    protected String getString(int id)
    {
        if (isSpecialStringId(id))
        {
            return getSpecialString(id);
        }
        if ((id < 0) || (id >= this.strings.length))
        {
            throw new IllegalArgumentException("Unknown string: " + id);
        }
        return this.strings[id];
    }

    private static class ByteId extends StringReaderV1
    {
        private ByteId(String[] strings)
        {
            super(strings);
        }

        @Override
        public void skipString(Reader reader)
        {
            reader.skipBytes(Byte.BYTES);
        }

        @Override
        public String[] readStringArray(Reader reader)
        {
            byte[] ids = reader.readByteArray();
            int length = ids.length;
            String[] strings = new String[length];
            for (int i = 0; i < length; i++)
            {
                strings[i] = getString(ids[i]);
            }
            return strings;
        }

        @Override
        public void skipStringArray(Reader reader)
        {
            reader.skipByteArray();
        }

        @Override
        protected int readStringId(Reader reader)
        {
            return reader.readByte();
        }
    }

    private static class ShortId extends StringReaderV1
    {
        private ShortId(String[] strings)
        {
            super(strings);
        }

        @Override
        public void skipString(Reader reader)
        {
            reader.skipShort();
        }

        @Override
        public String[] readStringArray(Reader reader)
        {
            short[] ids = reader.readShortArray();
            int length = ids.length;
            String[] strings = new String[length];
            for (int i = 0; i < length; i++)
            {
                strings[i] = getString(ids[i]);
            }
            return strings;
        }

        @Override
        public void skipStringArray(Reader reader)
        {
            reader.skipShortArray();
        }

        @Override
        protected int readStringId(Reader reader)
        {
            return reader.readShort();
        }
    }

    private static class IntId extends StringReaderV1
    {
        private IntId(String[] strings)
        {
            super(strings);
        }

        @Override
        public void skipString(Reader reader)
        {
            reader.skipInt();
        }

        @Override
        public String[] readStringArray(Reader reader)
        {
            int[] ids = reader.readIntArray();
            int length = ids.length;
            String[] strings = new String[length];
            for (int i = 0; i < length; i++)
            {
                strings[i] = getString(ids[i]);
            }
            return strings;
        }

        @Override
        public void skipStringArray(Reader reader)
        {
            reader.skipIntArray();
        }

        @Override
        protected int readStringId(Reader reader)
        {
            return reader.readInt();
        }
    }

    static StringReader readStringIndex(Reader reader)
    {
        String[] strings = reader.readStringArray();
        return (strings.length <= Byte.MAX_VALUE) ?
               new ByteId(strings) :
               ((strings.length <= Short.MAX_VALUE) ?
                new ShortId(strings) :
                new IntId(strings));
    }
}
