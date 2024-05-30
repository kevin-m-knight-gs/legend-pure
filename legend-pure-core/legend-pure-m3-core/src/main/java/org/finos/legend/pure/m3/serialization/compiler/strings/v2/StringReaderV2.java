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

import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;
import org.finos.legend.pure.m3.navigation.graph.GraphPath;
import org.finos.legend.pure.m3.serialization.compiler.strings.StringReader;
import org.finos.legend.pure.m3.serialization.filesystem.usercodestorage.RepositoryCodeStorage;
import org.finos.legend.pure.m4.serialization.Reader;

import java.nio.charset.StandardCharsets;

abstract class StringReaderV2 extends BaseStringIndex implements StringReader
{
    private final String[] strings;

    private StringReaderV2(String[] strings)
    {
        this.strings = strings;
    }

    private StringReaderV2(int length)
    {
        this(new String[length]);
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

    private void deserialize(Reader reader)
    {
        StringBuilder builder = new StringBuilder();
        for (int i = 0, len = this.strings.length; i < len; i++)
        {
            int code = reader.readByte();
            int length = readIntOfType(reader, code);
            switch (code & STRING_TYPE_MASK)
            {
                case SIMPLE_STRING:
                {
                    byte[] bytes = reader.readBytes(length);
                    this.strings[i] = new String(bytes, StandardCharsets.UTF_8);
                    break;
                }
                case PACKAGE_PATH_STRING:
                {
                    builder.setLength(0);
                    if (length > 0)
                    {
                        String string = readString(reader);
                        builder.append(string);
                        for (int j = 1; j < length; j++)
                        {
                            string = readString(reader);
                            builder.append(PackageableElement.DEFAULT_PATH_SEPARATOR).append(string);
                        }
                    }
                    this.strings[i] = builder.toString();
                    break;
                }
                case SOURCE_ID_STRING:
                {
                    builder.setLength(0);
                    for (int j = 0; j < length; j++)
                    {
                        String string = readString(reader);
                        builder.append(RepositoryCodeStorage.PATH_SEPARATOR).append(string);
                    }
                    this.strings[i] = builder.toString();
                    break;
                }
                case GRAPH_PATH_STRING:
                {
                    builder.setLength(0);
                    GraphPath.Builder pathBuilder = GraphPath.builder(length, false);
                    String startNodePath = readString(reader);
                    pathBuilder.withStartNodePath(startNodePath);
                    for (int j = 0; j < length; j++)
                    {
                        int edgeCode = reader.readByte();
                        switch (edgeCode & GRAPH_PATH_EDGE_TYPE_MASK)
                        {
                            case GRAPH_PATH_TO_ONE_EDGE:
                            {
                                String property = readString(reader);
                                pathBuilder.addToOneProperty(property);
                                break;
                            }
                            case GRAPH_PATH_TO_MANY_INDEX_EDGE:
                            {
                                String property = readString(reader);
                                int index = readIntOfType(reader, edgeCode);
                                pathBuilder.addToManyPropertyValueAtIndex(property, index);
                                break;
                            }
                            case GRAPH_PATH_TO_MANY_KEY_EDGE:
                            {
                                String property = readString(reader);
                                String keyProperty = readString(reader);
                                String key = readString(reader);
                                pathBuilder.addToManyPropertyValueWithKey(property, keyProperty, key);
                                break;
                            }
                            default:
                            {
                                throw new RuntimeException(String.format("Unknown graph path edge type code: %02x", code));
                            }
                        }
                    }
                    this.strings[i] = pathBuilder.writeDescription(builder).toString();
                    break;
                }
                default:
                {
                    throw new RuntimeException(String.format("Unknown string type code: %02x", code));
                }
            }
        }
    }

    private static class ByteId extends StringReaderV2
    {
        private ByteId(int length)
        {
            super(length);
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

    private static class ShortId extends StringReaderV2
    {
        private ShortId(int length)
        {
            super(length);
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

    private static class IntId extends StringReaderV2
    {
        private IntId(int length)
        {
            super(length);
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
        int count = reader.readInt();
        StringReaderV2 stringReader = (count <= Byte.MAX_VALUE) ?
                                      new StringReaderV2.ByteId(count) :
                                      ((count <= Short.MAX_VALUE) ?
                                       new StringReaderV2.ShortId(count) :
                                       new StringReaderV2.IntId(count));
        stringReader.deserialize(reader);
        return stringReader;
    }
}
