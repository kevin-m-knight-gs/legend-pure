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

package org.finos.legend.pure.runtime.java.compiled.generation.processors.support.coreinstance;

import org.finos.legend.pure.m3.serialization.compiler.element.AbstractPackageableElementBuilderTest;
import org.finos.legend.pure.m3.serialization.compiler.element.ElementBuilder;
import org.finos.legend.pure.runtime.java.compiled.generation.JavaPackageAndImportBuilder;

public class TestCompiledElementBuilder extends AbstractPackageableElementBuilderTest
{
    @Override
    protected ElementBuilder newElementBuilder()
    {
        return CompiledElementBuilder.newElementBuilder(Thread.currentThread().getContextClassLoader(), new CompiledPrimitiveValueResolver());
    }

    @Override
    protected String getExpectedVirtualPackageClassName()
    {
        return JavaPackageAndImportBuilder.buildLazyVirtualPackageClassReference();
    }

    @Override
    protected String getExpectedConcreteElementClassName(String classifierPath)
    {
        return JavaPackageAndImportBuilder.buildLazyConcreteElementClassReferenceFromUserPath(classifierPath);
    }

    @Override
    protected String getExpectedComponentInstanceClassName(String classifierPath)
    {
        return JavaPackageAndImportBuilder.buildLazyComponentInstanceClassReferenceFromUserPath(classifierPath);
    }
}
