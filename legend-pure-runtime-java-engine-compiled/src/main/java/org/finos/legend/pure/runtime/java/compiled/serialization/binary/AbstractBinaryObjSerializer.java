// Copyright 2020 Goldman Sachs
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

package org.finos.legend.pure.runtime.java.compiled.serialization.binary;

import org.eclipse.collections.api.list.ListIterable;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;
import org.finos.legend.pure.m4.coreinstance.primitive.date.PureDate;
import org.finos.legend.pure.m4.serialization.Writer;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.Enum;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.EnumRef;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.Obj;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.ObjRef;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.ObjUpdate;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.Primitive;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.PropertyValue;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.PropertyValueConsumer;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.PropertyValueMany;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.PropertyValueOne;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.RValue;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.RValueConsumer;

import java.math.BigDecimal;
import java.util.function.Consumer;

abstract class AbstractBinaryObjSerializer implements BinaryObjSerializer
{
    @Override
    public void serializeObj(Writer writer, Obj obj)
    {
        byte objType = (obj instanceof Enum) ? BinaryGraphSerializationTypes.ENUM : BinaryGraphSerializationTypes.OBJ;
        writer.writeByte(objType);
        writeSourceInformation(writer, obj.getSourceInformation());
        writeIdentifier(writer, obj.getIdentifier());
        writeClassifier(writer, obj.getClassifier());
        writeName(writer, obj.getName());
        writePropertyValues(writer, obj.getPropertyValues());
    }

    @Override
    public void serializeObjUpdate(Writer writer, ObjUpdate objUpdate)
    {
        writer.writeByte(BinaryGraphSerializationTypes.OBJ_UPDATE);
        writeIdentifier(writer, objUpdate.getIdentifier());
        writeClassifier(writer, objUpdate.getClassifier());
        writePropertyValues(writer, objUpdate.getPropertyValues());
    }

    protected void writeSourceInformation(Writer writer, SourceInformation sourceInformation)
    {
        if (sourceInformation == null)
        {
            writer.writeBoolean(false);
        }
        else
        {
            writer.writeBoolean(true);
            writeString(writer, sourceInformation.getSourceId());
            writer.writeInt(sourceInformation.getStartLine());
            writer.writeInt(sourceInformation.getStartColumn());
            writer.writeInt(sourceInformation.getLine());
            writer.writeInt(sourceInformation.getColumn());
            writer.writeInt(sourceInformation.getEndLine());
            writer.writeInt(sourceInformation.getEndColumn());
        }
    }

    protected void writeIdentifier(Writer writer, String identifier)
    {
        writeString(writer, identifier);
    }

    protected void writeClassifier(Writer writer, String classifier)
    {
        writeString(writer, classifier);
    }

    protected void writeName(Writer writer, String name)
    {
        writeString(writer, name);
    }

    protected abstract void writeString(Writer writer, String string);

    private void writePropertyValues(Writer writer, ListIterable<? extends PropertyValue> propertyValues)
    {
        RValueConsumer rValueWriter = new RValueConsumer()
        {
            @Override
            protected void accept(Primitive primitive)
            {
                writePrimitive(writer, primitive);
            }

            @Override
            protected void accept(ObjRef objRef)
            {
                writeObjRef(writer, objRef);
            }

            @Override
            protected void accept(EnumRef enumRef)
            {
                writeEnumRef(writer, enumRef);
            }
        };
        PropertyValueConsumer propertyValueWriter = new PropertyValueConsumer()
        {
            @Override
            protected void accept(PropertyValueMany many)
            {
                writePropertyValueMany(writer, many, rValueWriter);
            }

            @Override
            protected void accept(PropertyValueOne one)
            {
                writePropertyValueOne(writer, one, rValueWriter);
            }
        };
        writer.writeInt(propertyValues.size());
        propertyValues.forEach(propertyValueWriter);
    }

    // PropertyValue writers

    private void writePropertyValueOne(Writer writer, PropertyValueOne propertyValue, Consumer<? super RValue> rValueWriter)
    {
        writer.writeBoolean(false);
        writeString(writer, propertyValue.getProperty());
        rValueWriter.accept(propertyValue.getValue());
    }

    private void writePropertyValueMany(Writer writer, PropertyValueMany propertyValue, Consumer<? super RValue> rValueWriter)
    {
        writer.writeBoolean(true);
        writeString(writer, propertyValue.getProperty());
        ListIterable<RValue> values = propertyValue.getValues();
        writer.writeInt(values.size());
        values.forEach(rValueWriter);
    }

    // RValue writers

    private void writeObjRef(Writer writer, ObjRef objRef)
    {
        writer.writeByte(BinaryGraphSerializationTypes.OBJ_REF);
        writeString(writer, objRef.getClassifierId());
        writeString(writer, objRef.getId());
    }

    private void writeEnumRef(Writer writer, EnumRef enumRef)
    {
        writer.writeByte(BinaryGraphSerializationTypes.ENUM_REF);
        writeString(writer, enumRef.getEnumerationId());
        writeString(writer, enumRef.getEnumName());
    }

    private void writePrimitive(Writer writer, Primitive primitive)
    {
        Object value = primitive.getValue();
        if (value instanceof Boolean)
        {
            writer.writeByte(BinaryGraphSerializationTypes.PRIMITIVE_BOOLEAN);
            writer.writeBoolean((Boolean) value);
        }
        else if (value instanceof Double)
        {
            writer.writeByte(BinaryGraphSerializationTypes.PRIMITIVE_DOUBLE);
            writer.writeDouble((Double) value);
        }
        else if (value instanceof Long)
        {
            writer.writeByte(BinaryGraphSerializationTypes.PRIMITIVE_LONG);
            writer.writeLong((Long) value);
        }
        else if (value instanceof String)
        {
            writer.writeByte(BinaryGraphSerializationTypes.PRIMITIVE_STRING);
            writeString(writer, (String) value);
        }
        else if (value instanceof PureDate)
        {
            writer.writeByte(BinaryGraphSerializationTypes.PRIMITIVE_DATE);
            writeString(writer, value.toString());
        }
        else if (value instanceof BigDecimal)
        {
            writer.writeByte(BinaryGraphSerializationTypes.PRIMITIVE_DECIMAL);
            writer.writeString(((BigDecimal) value).toPlainString());
        }
        else
        {
            throw new UnsupportedOperationException("Unsupported primitive type: " + value.getClass().getSimpleName());
        }
    }
}
