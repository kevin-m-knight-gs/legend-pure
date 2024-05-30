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

package org.finos.legend.pure.m3.serialization.compiler.metadata.v1;

import org.eclipse.collections.api.list.ImmutableList;
import org.finos.legend.pure.m3.navigation.graph.GraphPath;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ConcreteElementMetadata;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ExternalReference;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ModuleMetadata;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ModuleMetadataSerializerExtension;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;
import org.finos.legend.pure.m4.serialization.Reader;
import org.finos.legend.pure.m4.serialization.Writer;

public class ModuleMetadataSerializerV1 implements ModuleMetadataSerializerExtension
{
    private static final int GRAPH_PATH_EDGE_TYPE_MASK = 0b1100_0000;
    private static final int GRAPH_PATH_TO_ONE_EDGE = 0b0000_0000;
    private static final int GRAPH_PATH_TO_MANY_INDEX_EDGE = 0b1000_0000;
    private static final int GRAPH_PATH_TO_MANY_KEY_EDGE = 0b0100_0000;

    private static final int INT_TYPE_MASK = 0b0000_0011;
    private static final int BYTE_INT = 0b0000_0000;
    private static final int SHORT_INT = 0b0000_0001;
    private static final int INT_INT = 0b0000_0010;

    @Override
    public int version()
    {
        return 1;
    }

    @Override
    public void serialize(Writer writer, ModuleMetadata metadata)
    {
        writer.writeString(metadata.getName());

        writer.writeInt(metadata.getElementCount());
        metadata.forEachElement(e -> writeElement(writer, e));
    }

    @Override
    public ModuleMetadata deserialize(Reader reader)
    {
        String name = reader.readString();

        int elementCount = reader.readInt();
        ConcreteElementMetadata[] elements = new ConcreteElementMetadata[elementCount];
        for (int i = 0; i < elementCount; i++)
        {
            elements[i] = readElement(reader);
        }
        return new ModuleMetadata(name, elements);
    }

    private void writeElement(Writer writer, ConcreteElementMetadata element)
    {
        writer.writeString(element.getPath());
        writer.writeString(element.getClassifierPath());
        writeSourceInfo(writer, element.getSourceInformation());
        ImmutableList<ExternalReference> externalReferences = element.getExternalReferences();
        writer.writeInt(externalReferences.size());
        externalReferences.forEach(extRef -> writeExternalReference(writer, extRef));
    }

    private ConcreteElementMetadata readElement(Reader reader)
    {
        String path = reader.readString();
        String classifierPath = reader.readString();
        SourceInformation sourceInfo = readSourceInfo(reader);
        int extRefCount = reader.readInt();
        ConcreteElementMetadata.Builder builder = ConcreteElementMetadata.builder(extRefCount)
                .withPath(path)
                .withClassifierPath(classifierPath)
                .withSourceInformation(sourceInfo);

        for (int i = 0; i < extRefCount; i++)
        {
            builder.withExternalReference(readExternalReference(reader, path));
        }
        return builder.build();
    }

    private void writeSourceInfo(Writer writer, SourceInformation sourceInfo)
    {
        writer.writeString(sourceInfo.getSourceId());
        int intType = getIntType(sourceInfo.getStartLine(), sourceInfo.getStartColumn(), sourceInfo.getLine(), sourceInfo.getColumn(), sourceInfo.getEndLine(), sourceInfo.getEndColumn());
        writer.writeByte((byte) intType);
        switch (intType)
        {
            case BYTE_INT:
            {
                writer.writeByte((byte) sourceInfo.getStartLine());
                writer.writeByte((byte) sourceInfo.getStartColumn());
                writer.writeByte((byte) sourceInfo.getLine());
                writer.writeByte((byte) sourceInfo.getColumn());
                writer.writeByte((byte) sourceInfo.getEndLine());
                writer.writeByte((byte) sourceInfo.getEndColumn());
                break;
            }
            case SHORT_INT:
            {
                writer.writeShort((short) sourceInfo.getStartLine());
                writer.writeShort((short) sourceInfo.getStartColumn());
                writer.writeShort((short) sourceInfo.getLine());
                writer.writeShort((short) sourceInfo.getColumn());
                writer.writeShort((short) sourceInfo.getEndLine());
                writer.writeShort((short) sourceInfo.getEndColumn());
                break;
            }
            case INT_INT:
            {
                writer.writeInt(sourceInfo.getStartLine());
                writer.writeInt(sourceInfo.getStartColumn());
                writer.writeInt(sourceInfo.getLine());
                writer.writeInt(sourceInfo.getColumn());
                writer.writeInt(sourceInfo.getEndLine());
                writer.writeInt(sourceInfo.getEndColumn());
                break;
            }
            default:
            {
                throw new RuntimeException(String.format("Unknown int type code: %02x", intType));
            }
        }
    }

    private SourceInformation readSourceInfo(Reader reader)
    {
        String sourceId = reader.readString();
        int intType = reader.readByte();
        int startLine;
        int startCol;
        int line;
        int col;
        int endLine;
        int endCol;
        switch (intType & INT_TYPE_MASK)
        {
            case BYTE_INT:
            {
                startLine = reader.readByte();
                startCol = reader.readByte();
                line = reader.readByte();
                col = reader.readByte();
                endLine = reader.readByte();
                endCol = reader.readByte();
                break;
            }
            case SHORT_INT:
            {
                startLine = reader.readShort();
                startCol = reader.readShort();
                line = reader.readShort();
                col = reader.readShort();
                endLine = reader.readShort();
                endCol = reader.readShort();
                break;
            }
            case INT_INT:
            {
                startLine = reader.readInt();
                startCol = reader.readInt();
                line = reader.readInt();
                col = reader.readInt();
                endLine = reader.readInt();
                endCol = reader.readInt();
                break;
            }
            default:
            {
                throw new RuntimeException(String.format("Unknown int type code: %02x", intType & INT_TYPE_MASK));
            }
        }
        return new SourceInformation(sourceId, startLine, startCol, line, col, endLine, endCol);
    }

    private void writeExternalReference(Writer writer, ExternalReference externalReference)
    {
        GraphPath path = externalReference.getPath();
        writer.writeInt(path.getEdgeCount());
        path.forEachEdge(new GraphPath.EdgeConsumer()
        {
            @Override
            protected void accept(GraphPath.ToOnePropertyEdge edge)
            {
                writer.writeByte((byte) GRAPH_PATH_TO_ONE_EDGE);
                writer.writeString(edge.getProperty());
            }

            @Override
            protected void accept(GraphPath.ToManyPropertyAtIndexEdge edge)
            {
                int intType = getIntType(edge.getIndex());
                writer.writeByte((byte) (GRAPH_PATH_TO_MANY_INDEX_EDGE | intType));
                writer.writeString(edge.getProperty());
                switch (intType)
                {
                    case BYTE_INT:
                    {
                        writer.writeByte((byte) edge.getIndex());
                        break;
                    }
                    case SHORT_INT:
                    {
                        writer.writeShort((short) edge.getIndex());
                        break;
                    }
                    case INT_INT:
                    {
                        writer.writeInt(edge.getIndex());
                        break;
                    }
                    default:
                    {
                        throw new RuntimeException(String.format("Unknown int type code: %02x", intType));
                    }
                }
            }

            @Override
            protected void accept(GraphPath.ToManyPropertyWithStringKeyEdge edge)
            {
                writer.writeByte((byte) GRAPH_PATH_TO_MANY_KEY_EDGE);
                writer.writeString(edge.getProperty());
                writer.writeString(edge.getKeyProperty());
                writer.writeString(edge.getKey());
            }
        });
        writer.writeString(externalReference.getReferenceId());
    }

    private ExternalReference readExternalReference(Reader reader, String path)
    {
        int edgeCount = reader.readInt();
        GraphPath.Builder builder = GraphPath.builder(edgeCount).withStartNodePath(path);
        for (int i = 0; i < edgeCount; i++)
        {
            byte edgeType = reader.readByte();
            switch (edgeType & GRAPH_PATH_EDGE_TYPE_MASK)
            {
                case GRAPH_PATH_TO_ONE_EDGE:
                {
                    String property = reader.readString();
                    builder.addToOneProperty(property);
                    break;
                }
                case GRAPH_PATH_TO_MANY_INDEX_EDGE:
                {
                    String property = reader.readString();
                    int index;
                    switch (edgeType & INT_TYPE_MASK)
                    {
                        case BYTE_INT:
                        {
                            index = reader.readByte();
                            break;
                        }
                        case SHORT_INT:
                        {
                            index = reader.readShort();
                            break;
                        }
                        case INT_INT:
                        {
                            index = reader.readInt();
                            break;
                        }
                        default:
                        {
                            throw new RuntimeException(String.format("Unknown int type code: %02x", edgeType & INT_TYPE_MASK));
                        }
                    }
                    builder.addToManyPropertyValueAtIndex(property, index);
                    break;
                }
                case GRAPH_PATH_TO_MANY_KEY_EDGE:
                {
                    String property = reader.readString();
                    String keyProperty = reader.readString();
                    String key = reader.readString();
                    builder.addToManyPropertyValueWithKey(property, keyProperty, key);
                    break;
                }
                default:
                {
                    throw new RuntimeException(String.format("Unknown graph path edge type code: %02x", edgeType & GRAPH_PATH_EDGE_TYPE_MASK));
                }
            }
        }
        String referenceId = reader.readString();
        return new ExternalReference(builder.build(), referenceId);
    }

    private static int getIntType(int... ints)
    {
        int type = BYTE_INT;
        for (int i : ints)
        {
            switch (getIntType(i))
            {
                case INT_INT:
                {
                    return INT_INT;
                }
                case SHORT_INT:
                {
                    type = SHORT_INT;
                }
            }
        }
        return type;
    }

    private static int getIntType(int i)
    {
        return (i < 0) ?
               (i >= Byte.MIN_VALUE) ? BYTE_INT : ((i >= Short.MIN_VALUE) ? SHORT_INT : INT_INT) :
               (i <= Byte.MAX_VALUE) ? BYTE_INT : ((i <= Short.MAX_VALUE) ? SHORT_INT : INT_INT);
    }
}
