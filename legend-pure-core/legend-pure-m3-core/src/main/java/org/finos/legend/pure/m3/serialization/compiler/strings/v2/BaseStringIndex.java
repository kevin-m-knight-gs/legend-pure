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

import org.eclipse.collections.api.map.primitive.MutableObjectIntMap;
import org.eclipse.collections.api.map.primitive.ObjectIntMap;
import org.eclipse.collections.impl.factory.primitive.ObjectIntMaps;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.M3Properties;
import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;
import org.finos.legend.pure.m3.serialization.filesystem.usercodestorage.RepositoryCodeStorage;
import org.finos.legend.pure.m4.serialization.Reader;
import org.finos.legend.pure.m4.serialization.Writer;

import java.util.function.Consumer;

abstract class BaseStringIndex
{
    protected static final int STRING_TYPE_MASK = 0b1100_0000;
    protected static final int SIMPLE_STRING = 0b0000_0000;
    protected static final int SOURCE_ID_STRING = 0b1000_0000;
    protected static final int PACKAGE_PATH_STRING = 0b0100_0000;
    protected static final int GRAPH_PATH_STRING = 0b1100_0000;

    protected static final int INT_TYPE_MASK = 0b0000_0011;
    protected static final int INT_INT = 0b0000_0000;
    protected static final int SHORT_INT = 0b0000_0001;
    protected static final int BYTE_INT = 0b0000_0010;

    protected static final int GRAPH_PATH_EDGE_TYPE_MASK = 0b1100_0000;
    protected static final int GRAPH_PATH_TO_ONE_EDGE = 0b0000_0000;
    protected static final int GRAPH_PATH_TO_MANY_INDEX_EDGE = 0b1000_0000;
    protected static final int GRAPH_PATH_TO_MANY_KEY_EDGE = 0b0100_0000;

    private static final String[] SPECIAL_STRINGS = {
            // null and empty string
            null, "",
            // separators
            PackageableElement.DEFAULT_PATH_SEPARATOR, RepositoryCodeStorage.PATH_SEPARATOR, ".",
            // classifiers
            M3Paths.Association, M3Paths.Class, M3Paths.ConcreteFunctionDefinition, M3Paths.Enumeration,
            M3Paths.NativeFunction, M3Paths.Package, M3Paths.PrimitiveType, M3Paths.Profile,
            // package names
            M3Paths.Root, "function", "meta", "metamodel", "property", "pure", "relationship", "type",
            // properties
            M3Properties.children, M3Properties.classifierGenericType, M3Properties.constraints,
            M3Properties.expressionSequence, M3Properties.func, M3Properties.genericType, M3Properties.multiplicity,
            M3Properties.multiplicityArguments, M3Properties.multiplicityParameters, M3Properties.owner,
            M3Properties.postConstraints, M3Properties.preConstraints, M3Properties.properties,
            M3Properties.propertiesFromAssociations, M3Properties.qualifiedProperties,
            M3Properties.qualifiedPropertiesFromAssociations, M3Properties.rawType, M3Properties.resolvedEnum,
            M3Properties.resolvedNode, M3Properties.resolvedProperty, M3Properties.returnMultiplicity,
            M3Properties.returnType, M3Properties.stereotypes, M3Properties.tag, M3Properties.taggedValues,
            M3Properties.typeArguments, M3Properties.typeParameters, M3Properties.values,
            // M3 Pure parser name
            "Pure"
    };
    private static final ObjectIntMap<String> SPECIAL_STRING_IDS = buildSpecialStringsToIdMap();

    private static final int MIN_SPECIAL_STRING_ID = Byte.MIN_VALUE;
    private static final int MAX_SPECIAL_STRING_ID = MIN_SPECIAL_STRING_ID + SPECIAL_STRINGS.length;

    // Special strings

    /**
     * Return whether the given id is a special string id. Special string ids are always negative.
     *
     * @param id id
     * @return whether id is a special string id
     */
    static boolean isSpecialStringId(int id)
    {
        return (MIN_SPECIAL_STRING_ID <= id) && (id < MAX_SPECIAL_STRING_ID);
    }

    /**
     * Return whether the given string is a special string.
     *
     * @param string string
     * @return whether string is a special string
     */
    static boolean isSpecialString(String string)
    {
        return SPECIAL_STRING_IDS.containsKey(string);
    }

    /**
     * Get the special string for the given id. Should call {@link #isSpecialStringId} first to ensure the id is a
     * special string id.
     *
     * @param id special string id
     * @return special string
     */
    static String getSpecialString(int id)
    {
        return SPECIAL_STRINGS[specialStringIdToIndex(id)];
    }

    /**
     * Get the id of the given special string. Special string ids are always negative. Returns 0 if the string is not a
     * special string.
     *
     * @param string special string
     * @return special string id or 0 if not found
     */
    static int getSpecialStringId(String string)
    {
        return SPECIAL_STRING_IDS.getIfAbsent(string, 0);
    }

    static void forEachSpecialString(Consumer<? super String> consumer)
    {
        for (String string : SPECIAL_STRINGS)
        {
            consumer.accept(string);
        }
    }

    private static int specialStringIdToIndex(int id)
    {
        return id - MIN_SPECIAL_STRING_ID;
    }

    private static int specialStringIndexToId(int index)
    {
        return MIN_SPECIAL_STRING_ID + index;
    }

    private static ObjectIntMap<String> buildSpecialStringsToIdMap()
    {
        int size = SPECIAL_STRINGS.length;
        MutableObjectIntMap<String> map = ObjectIntMaps.mutable.ofInitialCapacity(size);
        for (int i = 0; i < size; i++)
        {
            map.put(SPECIAL_STRINGS[i], specialStringIndexToId(i));
        }
        return map;
    }

    // int types

    static int getIntType(int i)
    {
        return (i < 0) ?
               (i >= Byte.MIN_VALUE) ? BYTE_INT : ((i >= Short.MIN_VALUE) ? SHORT_INT : INT_INT) :
               (i <= Byte.MAX_VALUE) ? BYTE_INT : ((i <= Short.MAX_VALUE) ? SHORT_INT : INT_INT);
    }

    static void writeIntOfType(Writer writer, int i, int intType)
    {
        switch (intType & INT_TYPE_MASK)
        {
            case BYTE_INT:
            {
                writer.writeByte((byte) i);
                return;
            }
            case SHORT_INT:
            {
                writer.writeShort((short) i);
                return;
            }
            case INT_INT:
            {
                writer.writeInt(i);
                return;
            }
            default:
            {
                throw new RuntimeException(String.format("Unknown int type code: %02x", intType & INT_TYPE_MASK));
            }
        }
    }

    static int readIntOfType(Reader reader, int intType)
    {
        switch (intType & INT_TYPE_MASK)
        {
            case BYTE_INT:
            {
                return reader.readByte();
            }
            case SHORT_INT:
            {
                return reader.readShort();
            }
            case INT_INT:
            {
                return reader.readInt();
            }
            default:
            {
                throw new RuntimeException(String.format("Unknown int type code: %02x", intType & INT_TYPE_MASK));
            }
        }
    }
}
