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

package org.finos.legend.pure.m3.coreinstance.helper;

import org.eclipse.collections.api.block.function.Function;
import org.eclipse.collections.api.block.function.Function2;
import org.finos.legend.pure.m4.ModelRepository;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.primitive.FloatCoreInstance;
import org.finos.legend.pure.m4.coreinstance.primitive.PrimitiveCoreInstance;
import org.finos.legend.pure.m4.coreinstance.primitive.date.PureDate;
import org.finos.legend.pure.m4.coreinstance.primitive.strictTime.PureStrictTime;

import java.math.BigDecimal;
import java.math.BigInteger;

public class AnyHelper
{
    public static final Function<CoreInstance, ? extends Object> UNWRAP_PRIMITIVES = AnyHelper::unwrapPrimitives;
    public static final Function2<Object, ModelRepository, ? extends CoreInstance> WRAP_PRIMITIVES = AnyHelper::wrapPrimitives;

    public static Object unwrapPrimitives(CoreInstance instance)
    {
        if (!(instance instanceof PrimitiveCoreInstance))
        {
            return instance;
        }

        Object result = ((PrimitiveCoreInstance<?>) instance).getValue();
        if (result instanceof Integer)
        {
            return ((Integer) result).longValue();
        }
        if (result instanceof BigDecimal && instance instanceof FloatCoreInstance)
        {
            return ((BigDecimal) result).doubleValue();
        }
        return result;
    }

    public static CoreInstance wrapPrimitives(Object object, ModelRepository modelRepository)
    {
        if (object instanceof CoreInstance)
        {
            return (CoreInstance) object;
        }
        if (object instanceof Integer || object instanceof Long || object instanceof BigInteger)
        {
            return PrimitiveHelper.integerToCoreInstance((Number) object, modelRepository);
        }
        if (object instanceof String)
        {
            return PrimitiveHelper.stringToCoreInstance((String) object, modelRepository);
        }
        if (object instanceof Boolean)
        {
            return PrimitiveHelper.booleanToCoreInstance((Boolean) object, modelRepository);
        }
        if (object instanceof PureDate)
        {
            return PrimitiveHelper.dateToCoreInstance((PureDate) object, modelRepository);
        }
        if ((object instanceof Float) || (object instanceof Double))
        {
            return PrimitiveHelper.floatToCoreInstance(BigDecimal.valueOf(((Number) object).doubleValue()), modelRepository);
        }
        if (object instanceof BigDecimal)
        {
            return PrimitiveHelper.decimalToCoreInstance((BigDecimal) object, modelRepository);
        }
        if (object instanceof PureStrictTime)
        {
            return PrimitiveHelper.strictTimeToCoreInstance((PureStrictTime) object, modelRepository);
        }
        throw new IllegalArgumentException("Unhandled type: " + object.getClass().getName() + " (value=" + object + ")");
    }
}