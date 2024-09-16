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

package org.finos.legend.pure.runtime.java.compiled.generation.processors.natives.essentials.lang.unit;

import org.eclipse.collections.api.list.ListIterable;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Unit;
import org.finos.legend.pure.m3.navigation.Instance;
import org.finos.legend.pure.m3.navigation.M3Properties;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.navigation.measure.Measure;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.runtime.java.compiled.generation.JavaPackageAndImportBuilder;
import org.finos.legend.pure.runtime.java.compiled.generation.ProcessorContext;
import org.finos.legend.pure.runtime.java.compiled.generation.processors.natives.AbstractNative;
import org.finos.legend.pure.runtime.java.compiled.generation.processors.support.coreinstance.QuantityCoreInstance;

public class NewUnit extends AbstractNative
{
    public NewUnit()
    {
        super("newUnit_Unit_1__Number_1__U_1_");
    }

    @Override
    public String build(CoreInstance topLevelElement, CoreInstance functionExpression, ListIterable<String> transformedParams, ProcessorContext processorContext)
    {
        ProcessorSupport processorSupport = processorContext.getSupport();
        CoreInstance unit = Instance.getValueForMetaPropertyToOneResolved(functionExpression.getValueForMetaPropertyToMany(M3Properties.parametersValues).getFirst(), M3Properties.values, processorSupport);
        if (Measure.isUnit(unit, processorContext.getSupport()))
        {
            // concretely specified unit: we can generate the Java instantiation directly
            return "new " + JavaPackageAndImportBuilder.buildImplClassReferenceFromType(unit, processorSupport) + "(" + transformedParams.get(1) + ", es)";
        }

        // unit comes from a variable or function expression or something like that: we have to instantiate reflectively
        StringBuilder builder = new StringBuilder("CompiledSupport.");
        CoreInstance rawType = Instance.getValueForMetaPropertyToOneResolved(functionExpression, M3Properties.genericType, M3Properties.rawType, processorSupport);
        if (rawType != null)
        {
            JavaPackageAndImportBuilder.buildInterfaceReferenceFromType(builder.append('<'), rawType, processorSupport).append('>');
        }
        return builder.append("newUnitInstance(").append(transformedParams.get(0)).append(", ").append(transformedParams.get(1)).append(", es)").toString();
    }

    @Override
    public String buildBody()
    {
        String quantityCoreInstance = QuantityCoreInstance.class.getSimpleName();
        String unitType = Unit.class.getName() + "<? extends " + quantityCoreInstance + ">";
        String numberClass = Number.class.getName();
        return "new DefendedPureFunction2<" + unitType + ", " + numberClass + ", " + quantityCoreInstance + ">()\n" +
                "        {\n" +
                "            @Override\n" +
                "            public " + quantityCoreInstance + " value(" + unitType + " unit, " + numberClass + " value, ExecutionSupport es)\n" +
                "            {\n" +
                "                return CompiledSupport.newUnitInstance(unit, value, es);\n" +
                "            }\n" +
                "        }";
    }
}
