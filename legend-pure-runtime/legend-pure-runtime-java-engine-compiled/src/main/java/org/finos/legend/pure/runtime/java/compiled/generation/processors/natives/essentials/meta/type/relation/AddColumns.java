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

package org.finos.legend.pure.runtime.java.compiled.generation.processors.natives.essentials.meta.type.relation;

import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relation.ColSpecArray;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relation.RelationType;
import org.finos.legend.pure.m3.execution.ExecutionSupport;
import org.finos.legend.pure.runtime.java.compiled.generation.processors.natives.AbstractNativeFunctionGeneric;

public class AddColumns extends AbstractNativeFunctionGeneric
{
    public AddColumns()
    {
        super("CoreGen.addColumns", new Class[]{RelationType.class, ColSpecArray.class, ExecutionSupport.class}, false, true, false, "addColumns_RelationType_1__ColSpecArray_1__RelationType_1_");
    }
}

