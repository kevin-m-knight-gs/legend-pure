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

package org.finos.legend.pure.m3.serialization.compiler.metadata.v1;

import org.eclipse.collections.api.list.ImmutableList;
import org.finos.legend.pure.m3.serialization.compiler.metadata.BackReference;
import org.finos.legend.pure.m3.serialization.compiler.metadata.BackReferenceConsumer;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ConcreteElementMetadata;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ExternalReference;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ModuleMetadata;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ModuleMetadataSerializerExtension;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;
import org.finos.legend.pure.m4.serialization.Reader;
import org.finos.legend.pure.m4.serialization.Writer;

public class ModuleMetadataSerializerV1 implements ModuleMetadataSerializerExtension
{
    private static final int BACK_REF_TYPE_MASK = 0b1110_0000;
    private static final int BACK_REF_APPLICATION = 0b0000_0000;
    private static final int BACK_REF_MODEL_ELEMENT = 0b1000_0000;
    private static final int BACK_REF_PROP_FROM_ASSOC = 0b0100_0000;
    private static final int BACK_REF_QUAL_PROP_FROM_ASSOC = 0b0010_0000;
    private static final int BACK_REF_REF_USAGE = 0b1100_0000;
    private static final int BACK_REF_SPEC = 0b1010_0000;

    private static final int INT_WIDTH_MASK = 0b0000_0011;
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
        ModuleMetadata.Builder builder = ModuleMetadata.builder(elementCount).withName(name);
        for (int i = 0; i < elementCount; i++)
        {
            builder.addElement(readElement(reader));
        }
        return builder.build();
    }

    private void writeElement(Writer writer, ConcreteElementMetadata element)
    {
        writer.writeString(element.getPath());
        writer.writeString(element.getClassifierPath());
        writeSourceInfo(writer, element.getSourceInformation());
        writer.writeInt(element.getReferenceIdVersion());
        ImmutableList<ExternalReference> externalReferences = element.getExternalReferences();
        writer.writeInt(externalReferences.size());
        externalReferences.forEach(extRef -> writeExternalReference(writer, extRef));
    }

    private ConcreteElementMetadata readElement(Reader reader)
    {
        String path = reader.readString();
        String classifierPath = reader.readString();
        SourceInformation sourceInfo = readSourceInfo(reader);
        int referenceIdVersion = reader.readInt();
        int extRefCount = reader.readInt();
        ConcreteElementMetadata.Builder builder = ConcreteElementMetadata.builder(extRefCount)
                .withPath(path)
                .withClassifierPath(classifierPath)
                .withSourceInformation(sourceInfo)
                .withReferenceIdVersion(referenceIdVersion);

        for (int i = 0; i < extRefCount; i++)
        {
            ExternalReference extRef = readExternalReference(reader);
            builder.withExternalReference(extRef);
        }
        return builder.build();
    }

    private void writeSourceInfo(Writer writer, SourceInformation sourceInfo)
    {
        writer.writeString(sourceInfo.getSourceId());
        int intType = getIntWidth(sourceInfo.getStartLine(), sourceInfo.getStartColumn(), sourceInfo.getLine(), sourceInfo.getColumn(), sourceInfo.getEndLine(), sourceInfo.getEndColumn());
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
        switch (intType & INT_WIDTH_MASK)
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
                throw new RuntimeException(String.format("Unknown int type code: %02x", intType & INT_WIDTH_MASK));
            }
        }
        return new SourceInformation(sourceId, startLine, startCol, line, col, endLine, endCol);
    }

    private void writeExternalReference(Writer writer, ExternalReference extRef)
    {
        writer.writeString(extRef.getReferenceId());
        ImmutableList<BackReference> backReferences = extRef.getBackReferences();
        writer.writeInt(backReferences.size());
        backReferences.forEach(new BackReferenceConsumer()
        {
            @Override
            protected void accept(BackReference.Application application)
            {
                writer.writeByte((byte) BACK_REF_APPLICATION);
                writer.writeString(application.getFunctionExpression());
            }

            @Override
            protected void accept(BackReference.ModelElement modelElement)
            {
                writer.writeByte((byte) BACK_REF_MODEL_ELEMENT);
                writer.writeString(modelElement.getElement());
            }

            @Override
            protected void accept(BackReference.PropertyFromAssociation propertyFromAssociation)
            {
                writer.writeByte((byte) BACK_REF_PROP_FROM_ASSOC);
                writer.writeString(propertyFromAssociation.getProperty());
            }

            @Override
            protected void accept(BackReference.QualifiedPropertyFromAssociation qualifiedPropertyFromAssociation)
            {
                writer.writeByte((byte) BACK_REF_QUAL_PROP_FROM_ASSOC);
                writer.writeString(qualifiedPropertyFromAssociation.getQualifiedProperty());
            }

            @Override
            protected void accept(BackReference.ReferenceUsage referenceUsage)
            {
                int offset = referenceUsage.getOffset();
                int offsetIntWidth = getIntWidth(offset);
                writer.writeByte((byte) (BACK_REF_REF_USAGE | offsetIntWidth));
                writer.writeString(referenceUsage.getOwner());
                writer.writeString(referenceUsage.getProperty());
                switch (offsetIntWidth)
                {
                    case BYTE_INT:
                    {
                        writer.writeByte((byte) offset);
                        break;
                    }
                    case SHORT_INT:
                    {
                        writer.writeShort((short) offset);
                        break;
                    }
                    case INT_INT:
                    {
                        writer.writeInt(offset);
                        break;
                    }
                    default:
                    {
                        throw new RuntimeException(String.format("Unknown int width code: %02x", offsetIntWidth));
                    }
                }
            }

            @Override
            protected void accept(BackReference.Specialization specialization)
            {
                writer.writeByte((byte) BACK_REF_SPEC);
                writer.writeString(specialization.getGeneralization());
            }
        });
    }

    private ExternalReference readExternalReference(Reader reader)
    {
        String referenceId = reader.readString();
        int backRefCount = reader.readInt();
        ExternalReference.Builder builder = ExternalReference.builder(backRefCount)
                .withReferenceId(referenceId);
        for (int i = 0; i < backRefCount; i++)
        {
            int code = reader.readByte();
            switch (code & BACK_REF_TYPE_MASK)
            {
                case BACK_REF_APPLICATION:
                {
                    String functionExpression = reader.readString();
                    builder.withApplication(functionExpression);
                    break;
                }
                case BACK_REF_MODEL_ELEMENT:
                {
                    String element = reader.readString();
                    builder.withModelElement(element);
                    break;
                }
                case BACK_REF_PROP_FROM_ASSOC:
                {
                    String property = reader.readString();
                    builder.withPropertyFromAssociation(property);
                    break;
                }
                case BACK_REF_QUAL_PROP_FROM_ASSOC:
                {
                    String qualifiedProperty = reader.readString();
                    builder.withQualifiedPropertyFromAssociation(qualifiedProperty);
                    break;
                }
                case BACK_REF_REF_USAGE:
                {
                    String owner = reader.readString();
                    String property = reader.readString();
                    int offset;
                    switch (code & INT_WIDTH_MASK)
                    {
                        case BYTE_INT:
                        {
                            offset = reader.readByte();
                            break;
                        }
                        case SHORT_INT:
                        {
                            offset = reader.readShort();
                            break;
                        }
                        case INT_INT:
                        {
                            offset = reader.readInt();
                            break;
                        }
                        default:
                        {
                            throw new RuntimeException(String.format("Unknown int width code: %02x", code & INT_WIDTH_MASK));
                        }
                    }
                    builder.withReferenceUsage(owner, property, offset);
                    break;
                }
                case BACK_REF_SPEC:
                {
                    String specialization = reader.readString();
                    builder.withSpecialization(specialization);
                    break;
                }
                default:
                {
                    throw new RuntimeException(String.format("Unknown back reference type code: %02x", code & BACK_REF_TYPE_MASK));
                }
            }
        }
        return builder.build();
    }

    private static int getIntWidth(int... ints)
    {
        int type = BYTE_INT;
        for (int i : ints)
        {
            switch (getIntWidth(i))
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

    private static int getIntWidth(int i)
    {
        return (i < 0) ?
               (i >= Byte.MIN_VALUE) ? BYTE_INT : ((i >= Short.MIN_VALUE) ? SHORT_INT : INT_INT) :
               (i <= Byte.MAX_VALUE) ? BYTE_INT : ((i <= Short.MAX_VALUE) ? SHORT_INT : INT_INT);
    }
}
