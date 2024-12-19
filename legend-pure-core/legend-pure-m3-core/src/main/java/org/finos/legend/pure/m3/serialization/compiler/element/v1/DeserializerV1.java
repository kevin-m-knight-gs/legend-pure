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

package org.finos.legend.pure.m3.serialization.compiler.element.v1;

import org.eclipse.collections.api.IntIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.list.primitive.IntInterval;
import org.finos.legend.pure.m3.navigation.M3Properties;
import org.finos.legend.pure.m3.navigation._package._Package;
import org.finos.legend.pure.m3.serialization.compiler.element.DeserializedConcreteElement;
import org.finos.legend.pure.m3.serialization.compiler.element.DeserializedElement;
import org.finos.legend.pure.m3.serialization.compiler.element.PropertyValues;
import org.finos.legend.pure.m3.serialization.compiler.element.Reference;
import org.finos.legend.pure.m3.serialization.compiler.element.SerializationContext;
import org.finos.legend.pure.m3.serialization.compiler.element.Value;
import org.finos.legend.pure.m3.serialization.compiler.element.ValueOrReference;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;
import org.finos.legend.pure.m4.coreinstance.primitive.date.PureDate;
import org.finos.legend.pure.m4.coreinstance.primitive.strictTime.PureStrictTime;
import org.finos.legend.pure.m4.serialization.Reader;

import java.math.BigInteger;

public class DeserializerV1 extends BaseV1
{
    private final SerializationContext serializationContext;

    DeserializerV1(SerializationContext serializationContext)
    {
        this.serializationContext = serializationContext;
    }

    DeserializedConcreteElement deserialize(Reader reader)
    {
        int referenceIdVersion = this.serializationContext.getReferenceIdProvider().version();
        Reader stringIndexedReader = this.serializationContext.getStringIndexer().readStringIndex(reader);
        String path = stringIndexedReader.readString();
        InternalNode[] nodes = deserializeNodes(stringIndexedReader);
        return new MainNode(path, nodes, referenceIdVersion);
    }

    private InternalNode[] deserializeNodes(Reader reader)
    {
        String sourceId = reader.readString();
        int nodeCount = reader.readInt();
        int internalIdWidth = getIntWidth(nodeCount);
        InternalNode[] nodes = new InternalNode[nodeCount];
        for (int i = 0; i < nodeCount; i++)
        {
            nodes[i] = deserializeNode(reader, sourceId, internalIdWidth);
        }
        return nodes;
    }

    private InternalNode deserializeNode(Reader reader, String sourceId, int internalIdWidth)
    {
        String name = readName(reader);
        String classifierId = readClassifier(reader);
        SourceInformation sourceInfo = readSourceInfo(reader, sourceId);
        int propertyCount = reader.readInt();
        MutableList<PropertyValues> propertiesWithValues = Lists.mutable.ofInitialCapacity(propertyCount);
        for (int i = 0; i < propertyCount; i++)
        {
            propertiesWithValues.add(readPropertyWithValues(reader, internalIdWidth));
        }
        return new InternalNode(name, classifierId, sourceInfo, propertiesWithValues.asUnmodifiable());
    }

    private String readName(Reader reader)
    {
        boolean hasName = reader.readBoolean();
        return hasName ? reader.readString() : null;
    }

    private String readClassifier(Reader reader)
    {
        return reader.readString();
    }

    private SourceInformation readSourceInfo(Reader reader, String sourceId)
    {
        int code = reader.readByte();
        switch (code & VALUE_PRESENT_MASK)
        {
            case VALUE_NOT_PRESENT:
            {
                return null;
            }
            case VALUE_PRESENT:
            {
                int startLine = readIntOfWidth(reader, code);
                int startCol = readIntOfWidth(reader, code);
                int line = readIntOfWidth(reader, code);
                int col = readIntOfWidth(reader, code);
                int endLine = readIntOfWidth(reader, code);
                int endCol = readIntOfWidth(reader, code);
                return new SourceInformation(sourceId, startLine, startCol, line, col, endLine, endCol);
            }
            default:
            {
                throw new RuntimeException(String.format("Unknown value present code: %02x", code & NODE_TYPE_MASK));
            }
        }
    }

    private PropertyValues readPropertyWithValues(Reader reader, int internalIdWidth)
    {
        String name = reader.readString();
        String sourceType = reader.readString();
        int valueCount = reader.readInt();
        MutableList<ValueOrReference> values = Lists.mutable.ofInitialCapacity(valueCount);
        for (int i = 0; i < valueCount; i++)
        {
            values.add(readPropertyValue(reader, internalIdWidth));
        }
        return new PropertyValues()
        {
            @Override
            public String getPropertyName()
            {
                return name;
            }

            @Override
            public ListIterable<String> getRealKey()
            {
                return _Package.convertM3PathToM4(sourceType).with(M3Properties.properties).with(name);
            }

            @Override
            public ListIterable<ValueOrReference> getValues()
            {
                return values.asUnmodifiable();
            }
        };
    }

    private ValueOrReference readPropertyValue(Reader reader, int internalIdWidth)
    {
        int code = reader.readByte();
        switch (code & NODE_TYPE_MASK)
        {
            case INTERNAL_REFERENCE:
            {
                int id = readIntOfWidth(reader, internalIdWidth);
                return Reference.newInternalReference(id);
            }
            case EXTERNAL_REFERENCE:
            {
                String id = reader.readString();
                return Reference.newExternalReference(id);
            }
            case BOOLEAN:
            {
                boolean value = deserializeBoolean(code);
                return Value.newBooleanValue(value);
            }
            case BYTE:
            {
                byte value = deserializeByte(reader);
                return Value.newByteValue(value);
            }
            case DATE:
            {
                switch (code & DATE_TYPE_MASK)
                {
                    case DATE_TYPE:
                    {
                        PureDate value = deserializeDate(reader, code);
                        return Value.newDateValue(value);
                    }
                    case DATE_TIME_TYPE:
                    {
                        PureDate value = deserializeDate(reader, code);
                        return Value.newDateTimeValue(value);
                    }
                    case STRICT_DATE_TYPE:
                    {
                        PureDate value = deserializeDate(reader, code);
                        return Value.newStrictDateValue(value);
                    }
                    case LATEST_DATE_TYPE:
                    {
                        return Value.newLatestDateValue();
                    }
                    default:
                    {
                        throw new RuntimeException(String.format("Unknown date type code: %02x", code & DATE_TYPE_MASK));
                    }
                }
            }
            case NUMBER:
            {
                switch (code & NUMBER_TYPE_MASK)
                {
                    case DECIMAL_TYPE:
                    {
                        String value = deserializeDecimal(reader, code);
                        return Value.newDecimalValue(value);
                    }
                    case FLOAT_TYPE:
                    {
                        String value = deserializeFloat(reader, code);
                        return Value.newFloatValue(value);
                    }
                    case INTEGER_TYPE:
                    {
                        if (((code & INTEGRAL_WIDTH_MASK) == LONG_WIDTH))
                        {
                            long value = deserializeOrdinaryIntegerAsLong(reader, code);
                            return Value.newIntegerValue(value);
                        }
                        else
                        {
                            int value = deserializeOrdinaryIntegerAsInt(reader, code);
                            return Value.newIntegerValue(value);
                        }
                    }
                    case BIG_INTEGER_TYPE:
                    {
                        BigInteger value = deserializeBigInteger(reader, code);
                        return Value.newIntegerValue(value);
                    }
                    default:
                    {
                        throw new RuntimeException(String.format("Unknown number type code: %02x", code & NUMBER_TYPE_MASK));
                    }
                }
            }
            case STRICT_TIME:
            {
                PureStrictTime value = deserializeStrictTime(reader, code);
                return Value.newStrictTimeValue(value);
            }
            case STRING:
            {
                String value = deserializeString(reader);
                return Value.newStringValue(value);
            }
            default:
            {
                throw new RuntimeException(String.format("Unknown node type code: %02x", code & NODE_TYPE_MASK));
            }
        }
    }

    private static class MainNode extends DeserializedConcreteElement
    {
        private final String path;
        private final InternalNode[] internalNodes;
        private final int referenceIdVersion;

        private MainNode(String path, InternalNode[] internalNodes, int referenceIdVersion)
        {
            this.path = path;
            this.internalNodes = internalNodes;
            this.referenceIdVersion = referenceIdVersion;
        }

        @Override
        public String getPath()
        {
            return this.path;
        }

        @Override
        public IntIterable getInternalElementIds()
        {
            return IntInterval.zeroTo(this.internalNodes.length - 1);
        }

        @Override
        public DeserializedElement getInternalElement(int id)
        {
            return this.internalNodes[id];
        }

        @Override
        public int getReferenceIdVersion()
        {
            return this.referenceIdVersion;
        }

        @Override
        public String getName()
        {
            return this.internalNodes[0].getName();
        }

        @Override
        public String getClassifierReferenceId()
        {
            return this.internalNodes[0].getClassifierReferenceId();
        }

        @Override
        public SourceInformation getSourceInformation()
        {
            return this.internalNodes[0].getSourceInformation();
        }

        @Override
        public ListIterable<? extends PropertyValues> getPropertyValues()
        {
            return this.internalNodes[0].getPropertyValues();
        }
    }

    private static class InternalNode extends DeserializedElement
    {
        private final String name;
        private final String classifierId;
        private final SourceInformation sourceInfo;
        private final ListIterable<PropertyValues> propertiesWithValues;

        private InternalNode(String name, String classifierId, SourceInformation sourceInfo, ListIterable<PropertyValues> propertiesWithValues)
        {
            this.name = name;
            this.classifierId = classifierId;
            this.sourceInfo = sourceInfo;
            this.propertiesWithValues = propertiesWithValues;
        }

        @Override
        public String getName()
        {
            return this.name;
        }

        @Override
        public String getClassifierReferenceId()
        {
            return this.classifierId;
        }

        @Override
        public SourceInformation getSourceInformation()
        {
            return this.sourceInfo;
        }

        @Override
        public ListIterable<? extends PropertyValues> getPropertyValues()
        {
            return this.propertiesWithValues;
        }
    }
}
