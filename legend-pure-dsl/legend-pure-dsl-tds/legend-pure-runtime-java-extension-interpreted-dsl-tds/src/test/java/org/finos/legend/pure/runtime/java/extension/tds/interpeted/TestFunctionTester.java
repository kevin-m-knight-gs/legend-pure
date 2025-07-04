// Copyright 2023 Goldman Sachs
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

package org.finos.legend.pure.runtime.java.extension.tds.interpeted;

import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relation.TDS;
import org.finos.legend.pure.m3.execution.FunctionExecution;
import org.finos.legend.pure.m3.navigation.M3Properties;
import org.finos.legend.pure.m3.navigation.generictype.GenericType;
import org.finos.legend.pure.m3.tests.function.base.PureExpressionTest;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.runtime.java.interpreted.FunctionExecutionInterpreted;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;

public class TestFunctionTester extends PureExpressionTest
{
    @BeforeClass
    public static void setUp()
    {
        setUpRuntime(getFunctionExecution());
    }

    @After
    public void cleanRuntime()
    {
        runtime.delete("fromString.pure");
    }

    protected static FunctionExecution getFunctionExecution()
    {
        return new FunctionExecutionInterpreted();
    }

    @org.junit.Test
    public void testFunction()
    {
        compileTestSource("fromString.pure",
                                "function test():Any[*]\n" +
                                        "{" +
                                        " meta::pure::metamodel::relation::stringToTDS('a\\n1');" +
                                        "}");
        this.execute("test():Any[*]");
    }

    @org.junit.Test
    public void testStringToTDSWithEmpty()
    {
        compileTestSource("fromString.pure",
                "function test():Any[*]\n" +
                        "{" +
                        " meta::pure::metamodel::relation::stringToTDS('a,b,c\\n2,c,true\\n,,\\nnull,null,null\\n');" +
                        "}");
        CoreInstance tdsCoreInstance = this.execute("test():Any[*]");
        TDS<?> tds = (TDS<?>) tdsCoreInstance.getValueForMetaPropertyToOne(M3Properties.values);
        Assert.assertEquals("TDS<(a:Integer, b:String, c:Boolean)>", GenericType.print(tds._classifierGenericType(), processorSupport));
    }
}
