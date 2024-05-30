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

package org.finos.legend.pure.m3.serialization.compiler.reference;

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.Counter;
import org.eclipse.collections.impl.tuple.Tuples;
import org.finos.legend.pure.m3.coreinstance.Package;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.PropertyOwner;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.extension.Profile;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.extension.Stereotype;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.extension.Tag;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.ConcreteFunctionDefinition;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.LambdaFunction;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.NativeFunction;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.QualifiedProperty;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relationship.Association;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relationship.Generalization;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Enum;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Enumeration;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.FunctionType;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.InstanceValue;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.SimpleFunctionExpression;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.ValueSpecification;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.VariableExpression;
import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;
import org.finos.legend.pure.m3.navigation.generictype.GenericType;
import org.finos.legend.pure.m3.tests.AbstractPureTestWithCoreCompiled;
import org.finos.legend.pure.m3.tools.ListHelper;
import org.finos.legend.pure.m3.tools.PackageTreeIterable;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestReferenceIdGenerator extends AbstractPureTestWithCoreCompiled
{
    private static ReferenceIdGenerator idGenerator;

    @BeforeClass
    public static void setUp()
    {
        setUpRuntime(getFunctionExecution(), getExtra());
        idGenerator = new ReferenceIdGenerator(processorSupport);
    }

    public static Pair<String, String> getExtra()
    {
        return Tuples.pair(
                "test.pure",
                "import test::*;\n" +
                        "\n" +
                        "Class test::SimpleClass\n" +
                        "{\n" +
                        "  name : String[1];\n" +
                        "  id : Integer[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class test::Left\n" +
                        "{\n" +
                        "}\n" +
                        "\n" +
                        "Class test::Right\n" +
                        "{\n" +
                        "}\n" +
                        "\n" +
                        "Association test::LeftRight\n" +
                        "{\n" +
                        "  toLeft : Left[1];\n" +
                        "  toRight : Right[1];\n" +
                        "}\n" +
                        "\n" +
                        "Profile test::SimpleProfile\n" +
                        "{\n" +
                        "  stereotypes : [st1, st2];\n" +
                        "  tags : [t1, t2, t3];\n" +
                        "}\n" +
                        "\n" +
                        "Enum test::SimpleEnumeration\n" +
                        "{\n" +
                        "  VAL1, VAL2\n" +
                        "}\n" +
                        "\n" +
                        "Class test::BothSides extends Left, Right\n" +
                        "{\n" +
                        "  leftCount : Integer[1];\n" +
                        "  rightCount : Integer[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class <<doc.deprecated>> {doc.doc = 'Deprecated class with annotations'} test::ClassWithAnnotations\n" +
                        "{\n" +
                        "  <<doc.deprecated>> deprecated : String[0..1];\n" +
                        "  <<doc.deprecated>> {doc.doc = 'Deprecated: don\\'t use this'} alsoDeprecated : String[0..1];\n" +
                        "  {doc.doc = 'Time must be specified', doc.todo = 'Change this to DateTime'} date : Date[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class test::ClassWithTypeAndMultParams<T,V|m,n>\n" +
                        "{\n" +
                        "  propT : T[m];\n" +
                        "  propV : V[n];\n" +
                        "}\n" +
                        "\n" +
                        "Class test::ClassWithQualifiedProperties\n" +
                        "{\n" +
                        "  names : String[*];\n" +
                        "  title : String[0..1];\n" +
                        "  firstName()\n" +
                        "  {\n" +
                        "    if($this.names->isEmpty(), |'', |$this.names->at(0))\n" +
                        "  }:String[1];\n" +
                        "  fullName()\n" +
                        "  {\n" +
                        "    $this.fullName(false)\n" +
                        "  }:String[1];\n" +
                        "  fullName(withTitle:Boolean[1])\n" +
                        "  {\n" +
                        "    let titleString = if($withTitle && !$this.title->isEmpty(), |$this.title->toOne() + ' ', |'');\n" +
                        "    $this.names->joinStrings($titleString, ' ', '');\n" +
                        "  }:String[1];\n" +
                        "}\n" +
                        "\n" +
                        "function test::testFunc<T|m>(col:T[m], func:Function<{T[1]->String[1]}>[0..1]):String[m]\n" +
                        "{\n" +
                        "  let toStringFunc = if($func->isEmpty(), |{x:T[1] | $x->toString()}, |$func->toOne());\n" +
                        "  $col->map(x | $toStringFunc->eval($x));\n" +
                        "}\n"
        );
    }

    @Test
    public void testAllElements()
    {
        PackageTreeIterable.newRootPackageTreeIterable(processorSupport)
                .forEach(pkg ->
                {
                    if (pkg.getSourceInformation() != null)
                    {
                        assertIds(pkg);
                    }
                    pkg._children().forEach(c ->
                    {
                        if (!(c instanceof Package))
                        {
                            assertIds(c);
                        }
                    });
                });
    }

    @Test
    public void testSimpleClass()
    {
        String path = "test::SimpleClass";
        Class<?> simpleClass = getCoreInstance(path);
        Property<?, ?> name = findProperty(simpleClass, "name");
        Property<?, ?> id = findProperty(simpleClass, "id");
        MutableMap<String, CoreInstance> expected = Maps.mutable.<String, CoreInstance>with(path, simpleClass)
                .withKeyValue(path + ".properties['name']", name)
                .withKeyValue(path + ".properties['name'].classifierGenericType", name._classifierGenericType())
//                .withKeyValue(path + ".properties['name'].classifierGenericType.typeArguments[0]", name._classifierGenericType()._typeArguments().getFirst())
                .withKeyValue(path + ".properties['name'].classifierGenericType.typeArguments[1]", name._classifierGenericType()._typeArguments().getLast())
                .withKeyValue(path + ".properties['name'].genericType", name._genericType())
                .withKeyValue(path + ".properties['id']", id)
                .withKeyValue(path + ".properties['id'].classifierGenericType", id._classifierGenericType())
//                .withKeyValue(path + ".properties['id'].classifierGenericType.typeArguments[0]", id._classifierGenericType()._typeArguments().getFirst())
                .withKeyValue(path + ".properties['id'].classifierGenericType.typeArguments[1]", id._classifierGenericType()._typeArguments().getLast())
                .withKeyValue(path + ".properties['id'].genericType", id._genericType());

        assertIds(path, expected);
    }

    @Test
    public void testSimpleEnumeration()
    {
        String path = "test::SimpleEnumeration";
        Enumeration<? extends Enum> testEnumeration = getCoreInstance(path);
        ListIterable<? extends Enum> enums = ListHelper.wrapListIterable(testEnumeration._values());
        MutableMap<String, CoreInstance> expected = Maps.mutable.<String, CoreInstance>with(path, testEnumeration)
                .withKeyValue(path + ".values['VAL1']", enums.get(0))
                .withKeyValue(path + ".values['VAL2']", enums.get(1));

        assertIds(path, expected);
    }

    @Test
    public void testSimpleAssociation()
    {
        String path = "test::LeftRight";
        Association leftRight = getCoreInstance(path);
        Property<?, ?> toLeft = findProperty(leftRight, "toLeft");
        Property<?, ?> toRight = findProperty(leftRight, "toRight");

        MutableMap<String, CoreInstance> expected = Maps.mutable.<String, CoreInstance>with(path, leftRight)
                .withKeyValue(path + ".properties['toLeft']", toLeft)
                .withKeyValue(path + ".properties['toLeft'].classifierGenericType", toLeft._classifierGenericType())
                .withKeyValue(path + ".properties['toLeft'].classifierGenericType.typeArguments[0]", toLeft._classifierGenericType()._typeArguments().getFirst())
                .withKeyValue(path + ".properties['toLeft'].classifierGenericType.typeArguments[1]", toLeft._classifierGenericType()._typeArguments().getLast())
                .withKeyValue(path + ".properties['toLeft'].genericType", toLeft._genericType())
                .withKeyValue(path + ".properties['toRight']", toRight)
                .withKeyValue(path + ".properties['toRight'].classifierGenericType", toRight._classifierGenericType())
                .withKeyValue(path + ".properties['toRight'].classifierGenericType.typeArguments[0]", toRight._classifierGenericType()._typeArguments().getFirst())
                .withKeyValue(path + ".properties['toRight'].classifierGenericType.typeArguments[1]", toRight._classifierGenericType()._typeArguments().getLast())
                .withKeyValue(path + ".properties['toRight'].genericType", toRight._genericType());

        assertIds(path, expected);

        String leftPath = "test::Left";
        assertIds(leftPath, Maps.mutable.with(leftPath, getCoreInstance(leftPath)));

        String rightPath = "test::Right";
        assertIds(rightPath, Maps.mutable.with(rightPath, getCoreInstance(rightPath)));
    }

    @Test
    public void testSimpleProfile()
    {
        String path = "test::SimpleProfile";
        Profile testProfile = getCoreInstance(path);
        ListIterable<? extends Stereotype> stereotypes = ListHelper.wrapListIterable(testProfile._p_stereotypes());
        ListIterable<? extends Tag> tags = ListHelper.wrapListIterable(testProfile._p_tags());
        MutableMap<String, CoreInstance> expected = Maps.mutable.<String, CoreInstance>with(path, testProfile)
                .withKeyValue(path + ".p_stereotypes[value='st1']", stereotypes.get(0))
                .withKeyValue(path + ".p_stereotypes[value='st2']", stereotypes.get(1))
                .withKeyValue(path + ".p_tags[value='t1']", tags.get(0))
                .withKeyValue(path + ".p_tags[value='t2']", tags.get(1))
                .withKeyValue(path + ".p_tags[value='t3']", tags.get(2));

        assertIds(path, expected);
    }

    @Test
    public void testClassWithGeneralizations()
    {
        String path = "test::BothSides";
        Class<?> bothSides = getCoreInstance(path);
        ListIterable<? extends Generalization> generalizations = ListHelper.wrapListIterable(bothSides._generalizations());
        Property<?, ?> leftCount = findProperty(bothSides, "leftCount");
        Property<?, ?> rightCount = findProperty(bothSides, "rightCount");
        MutableMap<String, CoreInstance> expected = Maps.mutable.<String, CoreInstance>with(path, bothSides)
                .withKeyValue(path + ".generalizations[0].general", generalizations.get(0)._general())
                .withKeyValue(path + ".generalizations[1].general", generalizations.get(1)._general())
                .withKeyValue(path + ".properties['leftCount']", leftCount)
                .withKeyValue(path + ".properties['leftCount'].classifierGenericType", leftCount._classifierGenericType())
//                .withKeyValue(path + ".properties['leftCount'].classifierGenericType.typeArguments[0]", leftCount._classifierGenericType()._typeArguments().getFirst())
                .withKeyValue(path + ".properties['leftCount'].classifierGenericType.typeArguments[1]", leftCount._classifierGenericType()._typeArguments().getLast())
                .withKeyValue(path + ".properties['leftCount'].genericType", leftCount._genericType())
                .withKeyValue(path + ".properties['rightCount']", rightCount)
                .withKeyValue(path + ".properties['rightCount'].classifierGenericType", rightCount._classifierGenericType())
//                .withKeyValue(path + ".properties['rightCount'].classifierGenericType.typeArguments[0]", rightCount._classifierGenericType()._typeArguments().getFirst())
                .withKeyValue(path + ".properties['rightCount'].classifierGenericType.typeArguments[1]", rightCount._classifierGenericType()._typeArguments().getLast())
                .withKeyValue(path + ".properties['rightCount'].genericType", rightCount._genericType());

        assertIds(path, expected);
    }

    @Test
    public void testClassWithAnnotations()
    {
        String path = "test::ClassWithAnnotations";
        Class<?> classWithAnnotations = getCoreInstance(path);
        ListIterable<? extends Property<?, ?>> properties = ListHelper.wrapListIterable(classWithAnnotations._properties());
        MutableMap<String, CoreInstance> expected = Maps.mutable.<String, CoreInstance>with(path, classWithAnnotations)
                .withKeyValue(path + ".taggedValues[0]", classWithAnnotations._taggedValues().getOnly())
                .withKeyValue(path + ".properties['deprecated']", properties.get(0))
                .withKeyValue(path + ".properties['deprecated'].classifierGenericType", properties.get(0)._classifierGenericType())
//                .withKeyValue(path + ".properties['deprecated'].classifierGenericType.typeArguments[0]", properties.get(0)._classifierGenericType()._typeArguments().getFirst())
                .withKeyValue(path + ".properties['deprecated'].classifierGenericType.typeArguments[1]", properties.get(0)._classifierGenericType()._typeArguments().getLast())
                .withKeyValue(path + ".properties['deprecated'].genericType", properties.get(0)._genericType())
                .withKeyValue(path + ".properties['alsoDeprecated']", properties.get(1))
                .withKeyValue(path + ".properties['alsoDeprecated'].classifierGenericType", properties.get(1)._classifierGenericType())
//                .withKeyValue(path + ".properties['alsoDeprecated'].classifierGenericType.typeArguments[0]", properties.get(1)._classifierGenericType()._typeArguments().getFirst())
                .withKeyValue(path + ".properties['alsoDeprecated'].classifierGenericType.typeArguments[1]", properties.get(1)._classifierGenericType()._typeArguments().getLast())
                .withKeyValue(path + ".properties['alsoDeprecated'].genericType", properties.get(1)._genericType())
                .withKeyValue(path + ".properties['alsoDeprecated'].taggedValues[0]", properties.get(1)._taggedValues().getOnly())
                .withKeyValue(path + ".properties['date']", properties.get(2))
                .withKeyValue(path + ".properties['date'].classifierGenericType", properties.get(2)._classifierGenericType())
//                .withKeyValue(path + ".properties['date'].classifierGenericType.typeArguments[0]", properties.get(2)._classifierGenericType()._typeArguments().getFirst())
                .withKeyValue(path + ".properties['date'].classifierGenericType.typeArguments[1]", properties.get(2)._classifierGenericType()._typeArguments().getLast())
                .withKeyValue(path + ".properties['date'].genericType", properties.get(2)._genericType())
                .withKeyValue(path + ".properties['date'].taggedValues[0]", properties.get(2)._taggedValues().getFirst())
                .withKeyValue(path + ".properties['date'].taggedValues[1]", properties.get(2)._taggedValues().getLast());

        assertIds(path, expected);
    }

    @Test
    public void testClassWithTypeAndMultiplicityParameters()
    {
        String path = "test::ClassWithTypeAndMultParams";
        Class<?> classWithTypeMultParams = getCoreInstance(path);
        ListIterable<? extends Property<?, ?>> properties = ListHelper.wrapListIterable(classWithTypeMultParams._properties());
        MutableMap<String, CoreInstance> expected = Maps.mutable.<String, CoreInstance>with(path, classWithTypeMultParams)
//                .withKeyValue(path + ".multiplicityParameters[0]", classWithTypeMultParams._multiplicityParameters().getFirst())
//                .withKeyValue(path + ".multiplicityParameters[1]", classWithTypeMultParams._multiplicityParameters().getLast())
//                .withKeyValue(path + ".typeParameters[0]", classWithTypeMultParams._typeParameters().getFirst())
//                .withKeyValue(path + ".typeParameters[1]", classWithTypeMultParams._typeParameters().getLast())
                .withKeyValue(path + ".properties['propT']", properties.get(0))
                .withKeyValue(path + ".properties['propT'].classifierGenericType", properties.get(0)._classifierGenericType())
//                .withKeyValue(path + ".properties['propT'].classifierGenericType.typeArguments[0]", properties.get(0)._classifierGenericType()._typeArguments().getFirst())
                .withKeyValue(path + ".properties['propT'].classifierGenericType.typeArguments[1]", properties.get(0)._classifierGenericType()._typeArguments().getLast())
                .withKeyValue(path + ".properties['propT'].genericType", properties.get(0)._genericType())
                .withKeyValue(path + ".properties['propT'].multiplicity", properties.get(0)._multiplicity())
                .withKeyValue(path + ".properties['propV']", properties.get(1))
                .withKeyValue(path + ".properties['propV'].classifierGenericType", properties.get(1)._classifierGenericType())
//                .withKeyValue(path + ".properties['propV'].classifierGenericType.typeArguments[0]", properties.get(1)._classifierGenericType()._typeArguments().getFirst())
                .withKeyValue(path + ".properties['propV'].classifierGenericType.typeArguments[1]", properties.get(1)._classifierGenericType()._typeArguments().getLast())
                .withKeyValue(path + ".properties['propV'].genericType", properties.get(1)._genericType())
                .withKeyValue(path + ".properties['propV'].multiplicity", properties.get(1)._multiplicity());

        assertIds(path, expected);
    }

    @Test
    public void testClassWithQualifiedProperties()
    {
        String path = "test::ClassWithQualifiedProperties";
        Class<?> classWithQualifiedProps = getCoreInstance(path);
        MutableMap<String, CoreInstance> expected = Maps.mutable.with(path, classWithQualifiedProps);

        Property<?, ?> names = classWithQualifiedProps._properties().getFirst();
        expected.put(path + ".properties['names']", names);
        expected.put(path + ".properties['names'].classifierGenericType", names._classifierGenericType());
        expected.put(path + ".properties['names'].classifierGenericType.typeArguments[1]", names._classifierGenericType()._typeArguments().getLast());
        expected.put(path + ".properties['names'].genericType", names._genericType());

        Property<?, ?> title = classWithQualifiedProps._properties().getLast();
        expected.put(path + ".properties['title']", title);
        expected.put(path + ".properties['title'].classifierGenericType", title._classifierGenericType());
        expected.put(path + ".properties['title'].classifierGenericType.typeArguments[1]", title._classifierGenericType()._typeArguments().getLast());
        expected.put(path + ".properties['title'].genericType", title._genericType());


        ListIterable<? extends QualifiedProperty<?>> qualifiedProperties = ListHelper.wrapListIterable(classWithQualifiedProps._qualifiedProperties());

        QualifiedProperty<?> firstName = qualifiedProperties.get(0);
        SimpleFunctionExpression firstNameIfExp = (SimpleFunctionExpression) firstName._expressionSequence().getOnly();
        ListIterable<? extends ValueSpecification> firstNameIfParams = ListHelper.wrapListIterable(firstNameIfExp._parametersValues());
        SimpleFunctionExpression firstNameIfCond = (SimpleFunctionExpression) firstNameIfParams.get(0);
        InstanceValue firstNameIfTrue = (InstanceValue) firstNameIfParams.get(1);
        LambdaFunction<?> firstNameIfTrueLambda = (LambdaFunction<?>) firstNameIfTrue._values().getOnly();
        InstanceValue firstNameIfFalse = (InstanceValue) firstNameIfParams.get(2);
        LambdaFunction<?> firstNameIfFalseLambda = (LambdaFunction<?>) firstNameIfFalse._values().getOnly();
        expected.put(path + ".qualifiedProperties[id='firstName()']", firstName);
//        expected.put(path + ".qualifiedProperties[id='firstName()'].classifierGenericType", firstName._classifierGenericType());
//        expected.put(path + ".qualifiedProperties[id='firstName()'].classifierGenericType.typeArguments[0]", firstName._classifierGenericType()._typeArguments().getOnly());
        expected.put(path + ".qualifiedProperties[id='firstName()'].classifierGenericType.typeArguments[0].rawType", firstName._classifierGenericType()._typeArguments().getOnly()._rawType());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0]", firstName._expressionSequence().getOnly());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].genericType", firstName._expressionSequence().getOnly()._genericType());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[0]", firstNameIfParams.get(0));
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[0].genericType", firstNameIfParams.get(0)._genericType());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[0].parametersValues[0]", firstNameIfCond._parametersValues().getOnly());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[0].parametersValues[0].genericType", firstNameIfCond._parametersValues().getOnly()._genericType());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[0].parametersValues[0].parametersValues[0]", ((SimpleFunctionExpression) firstNameIfCond._parametersValues().getOnly())._parametersValues().getOnly());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[0].parametersValues[0].parametersValues[0].genericType", ((SimpleFunctionExpression) firstNameIfCond._parametersValues().getOnly())._parametersValues().getOnly()._genericType());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[0].parametersValues[0].propertyName", ((SimpleFunctionExpression) firstNameIfCond._parametersValues().getOnly())._propertyName());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[1]", firstNameIfParams.get(1));
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[1].genericType", firstNameIfParams.get(1)._genericType());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[1].genericType.typeArguments[0]", firstNameIfParams.get(1)._genericType()._typeArguments().getOnly());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[1].genericType.typeArguments[0].rawType", firstNameIfParams.get(1)._genericType()._typeArguments().getOnly()._rawType());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[1].genericType.typeArguments[0].rawType.returnType", ((FunctionType) firstNameIfParams.get(1)._genericType()._typeArguments().getOnly()._rawType())._returnType());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[1].values[0]", firstNameIfTrueLambda);
//        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType", firstNameIfTrueLambda._classifierGenericType());
//        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0]", firstNameIfTrueLambda._classifierGenericType()._typeArguments().getOnly());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType", firstNameIfTrueLambda._classifierGenericType()._typeArguments().getOnly()._rawType());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.returnType", ((FunctionType) firstNameIfTrueLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0]", firstNameIfTrueLambda._expressionSequence().getOnly());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].genericType", firstNameIfTrueLambda._expressionSequence().getOnly()._genericType());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2]", firstNameIfFalse);
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].genericType", firstNameIfFalse._genericType());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].genericType.typeArguments[0]", firstNameIfFalse._genericType()._typeArguments().getOnly());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].genericType.typeArguments[0].rawType", firstNameIfFalse._genericType()._typeArguments().getOnly()._rawType());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].genericType.typeArguments[0].rawType.returnType", ((FunctionType) firstNameIfFalse._genericType()._typeArguments().getOnly()._rawType())._returnType());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0]", firstNameIfFalseLambda);
//        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].classifierGenericType", firstNameIfFalseLambda._classifierGenericType());
//        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].classifierGenericType.typeArguments[0]", firstNameIfFalseLambda._classifierGenericType()._typeArguments().getOnly());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType", firstNameIfFalseLambda._classifierGenericType()._typeArguments().getOnly()._rawType());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType.returnType", ((FunctionType) firstNameIfFalseLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].expressionSequence[0]", firstNameIfFalseLambda._expressionSequence().getOnly());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].expressionSequence[0].genericType", firstNameIfFalseLambda._expressionSequence().getOnly()._genericType());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].expressionSequence[0].parametersValues[0]", ((SimpleFunctionExpression) firstNameIfFalseLambda._expressionSequence().getOnly())._parametersValues().getFirst());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].expressionSequence[0].parametersValues[0].genericType", ((SimpleFunctionExpression) firstNameIfFalseLambda._expressionSequence().getOnly())._parametersValues().getFirst()._genericType());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].expressionSequence[0].parametersValues[0].parametersValues[0]", ((SimpleFunctionExpression) ((SimpleFunctionExpression) firstNameIfFalseLambda._expressionSequence().getOnly())._parametersValues().getFirst())._parametersValues().getOnly());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].expressionSequence[0].parametersValues[0].parametersValues[0].genericType", ((SimpleFunctionExpression) ((SimpleFunctionExpression) firstNameIfFalseLambda._expressionSequence().getOnly())._parametersValues().getFirst())._parametersValues().getOnly()._genericType());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].expressionSequence[0].parametersValues[0].propertyName", ((SimpleFunctionExpression) ((SimpleFunctionExpression) firstNameIfFalseLambda._expressionSequence().getOnly())._parametersValues().getFirst())._propertyName());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].expressionSequence[0].parametersValues[1]", ((SimpleFunctionExpression) firstNameIfFalseLambda._expressionSequence().getOnly())._parametersValues().getLast());
        expected.put(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].expressionSequence[0].parametersValues[1].genericType", ((SimpleFunctionExpression) firstNameIfFalseLambda._expressionSequence().getOnly())._parametersValues().getLast()._genericType());
        expected.put(path + ".qualifiedProperties[id='firstName()'].genericType", firstName._genericType());

        QualifiedProperty<?> fullNameNoTitle = qualifiedProperties.get(1);
        SimpleFunctionExpression fullNameExpression = (SimpleFunctionExpression) fullNameNoTitle._expressionSequence().getOnly();
        expected.put(path + ".qualifiedProperties[id='fullName()']", fullNameNoTitle);
//        expected.put(path + ".qualifiedProperties[id='fullName()'].classifierGenericType", fullName._classifierGenericType());
//        expected.put(path + ".qualifiedProperties[id='fullName()'].classifierGenericType.typeArguments[0]", fullName._classifierGenericType()._typeArguments().getOnly());
        expected.put(path + ".qualifiedProperties[id='fullName()'].classifierGenericType.typeArguments[0].rawType", fullNameNoTitle._classifierGenericType()._typeArguments().getOnly()._rawType());
        expected.put(path + ".qualifiedProperties[id='fullName()'].expressionSequence[0]", fullNameNoTitle._expressionSequence().getOnly());
        expected.put(path + ".qualifiedProperties[id='fullName()'].expressionSequence[0].genericType", fullNameNoTitle._expressionSequence().getOnly()._genericType());
        expected.put(path + ".qualifiedProperties[id='fullName()'].expressionSequence[0].parametersValues[0]", fullNameExpression._parametersValues().getFirst());
        expected.put(path + ".qualifiedProperties[id='fullName()'].expressionSequence[0].parametersValues[0].genericType", fullNameExpression._parametersValues().getFirst()._genericType());
        expected.put(path + ".qualifiedProperties[id='fullName()'].expressionSequence[0].parametersValues[1]", fullNameExpression._parametersValues().getLast());
        expected.put(path + ".qualifiedProperties[id='fullName()'].expressionSequence[0].parametersValues[1].genericType", fullNameExpression._parametersValues().getLast()._genericType());
        expected.put(path + ".qualifiedProperties[id='fullName()'].expressionSequence[0].qualifiedPropertyName", fullNameExpression._qualifiedPropertyName());
        expected.put(path + ".qualifiedProperties[id='fullName()'].genericType", fullNameNoTitle._genericType());

        QualifiedProperty<?> fullNameWithTitle = qualifiedProperties.get(2);
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])']", fullNameWithTitle);
//        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].classifierGenericType", fullNameWithTitle._classifierGenericType());
//        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].classifierGenericType.typeArguments[0]", fullNameWithTitle._classifierGenericType()._typeArguments().getOnly());

        FunctionType fullNameWithTitleFunctionType = (FunctionType) fullNameWithTitle._classifierGenericType()._typeArguments().getOnly()._rawType();
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].classifierGenericType.typeArguments[0].rawType", fullNameWithTitleFunctionType);
//        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].classifierGenericType.typeArguments[0].rawType.parameters['this']", fullNameWithTitleFunctionType._parameters().getFirst());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].classifierGenericType.typeArguments[0].rawType.parameters['withTitle']", fullNameWithTitleFunctionType._parameters().getLast());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].classifierGenericType.typeArguments[0].rawType.parameters['withTitle'].genericType", fullNameWithTitleFunctionType._parameters().getLast()._genericType());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].genericType", fullNameWithTitle._genericType());

        SimpleFunctionExpression fullNameWithTitleLetExp = (SimpleFunctionExpression) fullNameWithTitle._expressionSequence().getFirst();
        InstanceValue fullNameWithTitleLetVarExp = (InstanceValue) fullNameWithTitleLetExp._parametersValues().getFirst();
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0]", fullNameWithTitleLetExp);
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].genericType", fullNameWithTitleLetExp._genericType());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[0]", fullNameWithTitleLetVarExp);
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[0].genericType", fullNameWithTitleLetVarExp._genericType());

        SimpleFunctionExpression fullNameWithTitleLetValExp = (SimpleFunctionExpression) fullNameWithTitleLetExp._parametersValues().getLast();
        ListIterable<? extends ValueSpecification> fullNameWithTitleLetValIfParams = ListHelper.wrapListIterable(fullNameWithTitleLetValExp._parametersValues());
        SimpleFunctionExpression fullNameWithTitleLetValIfCond = (SimpleFunctionExpression) fullNameWithTitleLetValIfParams.get(0);
        InstanceValue fullNameWithTitleLetValIfTrue = (InstanceValue) fullNameWithTitleLetValIfParams.get(1);
        LambdaFunction<?> fullNameWithTitleLetValIfTrueLambda = (LambdaFunction<?>) fullNameWithTitleLetValIfTrue._values().getOnly();
        InstanceValue fullNameWithTitleLetValIfFalse = (InstanceValue) fullNameWithTitleLetValIfParams.get(2);
        LambdaFunction<?> fullNameWithTitleLetValIfFalseLambda = (LambdaFunction<?>) fullNameWithTitleLetValIfFalse._values().getOnly();
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1]", fullNameWithTitleLetValExp);
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].genericType", fullNameWithTitleLetValExp._genericType());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0]", fullNameWithTitleLetValIfCond);
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].genericType", fullNameWithTitleLetValIfCond._genericType());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[0]", fullNameWithTitleLetValIfCond._parametersValues().getFirst());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[0].genericType", fullNameWithTitleLetValIfCond._parametersValues().getFirst()._genericType());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[1]", fullNameWithTitleLetValIfCond._parametersValues().getLast());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[1].genericType", fullNameWithTitleLetValIfCond._parametersValues().getLast()._genericType());
        expected.put(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[1].parametersValues[0]",
                ((SimpleFunctionExpression) fullNameWithTitleLetValIfCond._parametersValues().getLast())._parametersValues().getOnly());
        expected.put(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[1].parametersValues[0].genericType",
                ((SimpleFunctionExpression) fullNameWithTitleLetValIfCond._parametersValues().getLast())._parametersValues().getOnly()._genericType());
        expected.put(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[1].parametersValues[0].parametersValues[0]",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) fullNameWithTitleLetValIfCond._parametersValues().getLast())._parametersValues().getOnly())._parametersValues().getOnly());
        expected.put(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[1].parametersValues[0].parametersValues[0].genericType",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) fullNameWithTitleLetValIfCond._parametersValues().getLast())._parametersValues().getOnly())._parametersValues().getOnly()._genericType());
        expected.put(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[1].parametersValues[0].parametersValues[0].parametersValues[0]",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) ((SimpleFunctionExpression) fullNameWithTitleLetValIfCond._parametersValues().getLast())._parametersValues().getOnly())._parametersValues().getOnly())._parametersValues().getOnly());
        expected.put(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[1].parametersValues[0].parametersValues[0].parametersValues[0].genericType",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) ((SimpleFunctionExpression) fullNameWithTitleLetValIfCond._parametersValues().getLast())._parametersValues().getOnly())._parametersValues().getOnly())._parametersValues().getOnly()._genericType());
        expected.put(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[1].parametersValues[0].parametersValues[0].propertyName",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) ((SimpleFunctionExpression) fullNameWithTitleLetValIfCond._parametersValues().getLast())._parametersValues().getOnly())._parametersValues().getOnly())._propertyName());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1]", fullNameWithTitleLetValIfTrue);
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].genericType", fullNameWithTitleLetValIfTrue._genericType());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].genericType.typeArguments[0]", fullNameWithTitleLetValIfTrue._genericType()._typeArguments().getOnly());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].genericType.typeArguments[0].rawType", fullNameWithTitleLetValIfTrue._genericType()._typeArguments().getOnly()._rawType());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].genericType.typeArguments[0].rawType.returnType", ((FunctionType) fullNameWithTitleLetValIfTrue._genericType()._typeArguments().getOnly()._rawType())._returnType());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0]", fullNameWithTitleLetValIfTrueLambda);
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType", fullNameWithTitleLetValIfTrueLambda._classifierGenericType()._typeArguments().getOnly()._rawType());
        expected.put(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.returnType",
                ((FunctionType) fullNameWithTitleLetValIfTrueLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0]", fullNameWithTitleLetValIfTrueLambda._expressionSequence().getOnly());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].genericType", fullNameWithTitleLetValIfTrueLambda._expressionSequence().getOnly()._genericType());
        expected.put(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].genericType",
                ((SimpleFunctionExpression) fullNameWithTitleLetValIfTrueLambda._expressionSequence().getOnly())._parametersValues().getOnly()._genericType());
        expected.put(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].values[0]",
                (SimpleFunctionExpression) ((InstanceValue) ((SimpleFunctionExpression) fullNameWithTitleLetValIfTrueLambda._expressionSequence().getOnly())._parametersValues().getOnly())._values().getFirst());
        expected.put(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].values[0].genericType",
                ((SimpleFunctionExpression) ((InstanceValue) ((SimpleFunctionExpression) fullNameWithTitleLetValIfTrueLambda._expressionSequence().getOnly())._parametersValues().getOnly())._values().getFirst())._genericType());
        expected.put(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].values[0].parametersValues[0]",
                ((SimpleFunctionExpression) ((InstanceValue) ((SimpleFunctionExpression) fullNameWithTitleLetValIfTrueLambda._expressionSequence().getOnly())._parametersValues().getOnly())._values().getFirst())._parametersValues().getOnly());
        expected.put(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].values[0].parametersValues[0].genericType",
                ((SimpleFunctionExpression) ((InstanceValue) ((SimpleFunctionExpression) fullNameWithTitleLetValIfTrueLambda._expressionSequence().getOnly())._parametersValues().getOnly())._values().getFirst())._parametersValues().getOnly()._genericType());
        expected.put(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].values[0].parametersValues[0].parametersValues[0]",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) ((InstanceValue) ((SimpleFunctionExpression) fullNameWithTitleLetValIfTrueLambda._expressionSequence().getOnly())._parametersValues().getOnly())._values().getFirst())._parametersValues().getOnly())._parametersValues().getOnly());
        expected.put(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].values[0].parametersValues[0].parametersValues[0].genericType",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) ((InstanceValue) ((SimpleFunctionExpression) fullNameWithTitleLetValIfTrueLambda._expressionSequence().getOnly())._parametersValues().getOnly())._values().getFirst())._parametersValues().getOnly())._parametersValues().getOnly()._genericType());
        expected.put(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].values[0].parametersValues[0].propertyName",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) ((InstanceValue) ((SimpleFunctionExpression) fullNameWithTitleLetValIfTrueLambda._expressionSequence().getOnly())._parametersValues().getOnly())._values().getFirst())._parametersValues().getOnly())._propertyName());
        expected.put(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].values[1]",
                (InstanceValue) ((InstanceValue) ((SimpleFunctionExpression) fullNameWithTitleLetValIfTrueLambda._expressionSequence().getOnly())._parametersValues().getOnly())._values().getLast());
        expected.put(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].values[1].genericType",
                ((InstanceValue) ((InstanceValue) ((SimpleFunctionExpression) fullNameWithTitleLetValIfTrueLambda._expressionSequence().getOnly())._parametersValues().getOnly())._values().getLast())._genericType());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[2]", fullNameWithTitleLetValIfFalse);
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[2].genericType", fullNameWithTitleLetValIfFalse._genericType());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[2].genericType.typeArguments[0]", fullNameWithTitleLetValIfFalse._genericType()._typeArguments().getOnly());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[2].genericType.typeArguments[0].rawType", fullNameWithTitleLetValIfFalse._genericType()._typeArguments().getOnly()._rawType());
        expected.put(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[2].genericType.typeArguments[0].rawType.returnType",
                ((FunctionType) fullNameWithTitleLetValIfFalse._genericType()._typeArguments().getOnly()._rawType())._returnType());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[2].values[0]", fullNameWithTitleLetValIfFalseLambda);
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType", fullNameWithTitleLetValIfFalseLambda._classifierGenericType()._typeArguments().getOnly()._rawType());
        expected.put(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType.returnType",
                ((FunctionType) fullNameWithTitleLetValIfFalseLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0]", fullNameWithTitleLetValIfFalseLambda._expressionSequence().getOnly());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].genericType", fullNameWithTitleLetValIfFalseLambda._expressionSequence().getOnly()._genericType());

        SimpleFunctionExpression fullNameWithTitleJoinStrExp = (SimpleFunctionExpression) fullNameWithTitle._expressionSequence().getLast();
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1]", fullNameWithTitleJoinStrExp);
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1].genericType", fullNameWithTitleJoinStrExp._genericType());
        ListIterable<? extends ValueSpecification> fullNameWithTitleJoinStrExpParams = ListHelper.wrapListIterable(fullNameWithTitleJoinStrExp._parametersValues());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1].parametersValues[0]", fullNameWithTitleJoinStrExpParams.get(0));
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1].parametersValues[0].genericType", fullNameWithTitleJoinStrExpParams.get(0)._genericType());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1].parametersValues[0].parametersValues[0]", ((SimpleFunctionExpression) fullNameWithTitleJoinStrExpParams.get(0))._parametersValues().getOnly());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1].parametersValues[0].parametersValues[0].genericType", ((SimpleFunctionExpression) fullNameWithTitleJoinStrExpParams.get(0))._parametersValues().getOnly()._genericType());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1].parametersValues[0].propertyName", ((SimpleFunctionExpression) fullNameWithTitleJoinStrExpParams.get(0))._propertyName());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1].parametersValues[1]", fullNameWithTitleJoinStrExpParams.get(1));
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1].parametersValues[1].genericType", fullNameWithTitleJoinStrExpParams.get(1)._genericType());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1].parametersValues[2]", fullNameWithTitleJoinStrExpParams.get(2));
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1].parametersValues[2].genericType", fullNameWithTitleJoinStrExpParams.get(2)._genericType());
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1].parametersValues[3]", fullNameWithTitleJoinStrExpParams.get(3));
        expected.put(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1].parametersValues[3].genericType", fullNameWithTitleJoinStrExpParams.get(3)._genericType());

        assertIds(path, expected);
    }

    @Test
    public void testNativeFunction()
    {
        String path = "meta::pure::functions::lang::compare_T_1__T_1__Integer_1_";
        NativeFunction<?> compare = getCoreInstance(path);

        MutableMap<String, CoreInstance> expected = Maps.mutable.<String, CoreInstance>with(path, compare)
//                .withKeyValue(path + ".classifierGenericType", compare._classifierGenericType())
//                .withKeyValue(path + ".classifierGenericType.typeArguments[0]", compare._classifierGenericType()._typeArguments().getOnly())
                .withKeyValue(path + ".classifierGenericType.typeArguments[0].rawType", compare._classifierGenericType()._typeArguments().getOnly()._rawType())
                .withKeyValue(path + ".classifierGenericType.typeArguments[0].rawType.parameters['a']", ((FunctionType) compare._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getFirst())
                .withKeyValue(path + ".classifierGenericType.typeArguments[0].rawType.parameters['a'].genericType", ((FunctionType) compare._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getFirst()._genericType())
                .withKeyValue(path + ".classifierGenericType.typeArguments[0].rawType.parameters['b']", ((FunctionType) compare._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getLast())
                .withKeyValue(path + ".classifierGenericType.typeArguments[0].rawType.parameters['b'].genericType", ((FunctionType) compare._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getLast()._genericType())
                .withKeyValue(path + ".classifierGenericType.typeArguments[0].rawType.returnType", ((FunctionType) compare._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType())
                .withKeyValue(path + ".taggedValues[0]", compare._taggedValues().getFirst())
                .withKeyValue(path + ".taggedValues[1]", compare._taggedValues().getLast());

        assertIds(path, expected);
    }

    @Test
    public void testFunction()
    {
        String path = "test::testFunc_T_m__Function_$0_1$__String_m_";
        ConcreteFunctionDefinition<?> testFunction = getCoreInstance(path);
        MutableMap<String, CoreInstance> expected = Maps.mutable.with(path, testFunction);

        FunctionType functionType = (FunctionType) testFunction._classifierGenericType()._typeArguments().getOnly()._rawType();
//        expected.put(path + ".classifierGenericType", testFunction._classifierGenericType());
//        expected.put(path + ".classifierGenericType.typeArguments[0]", testFunction._classifierGenericType()._typeArguments().getOnly());
        expected.put(path + ".classifierGenericType.typeArguments[0].rawType", functionType);
        expected.put(path + ".classifierGenericType.typeArguments[0].rawType.parameters['col']", functionType._parameters().getFirst());
        expected.put(path + ".classifierGenericType.typeArguments[0].rawType.parameters['col'].genericType", functionType._parameters().getFirst()._genericType());
        expected.put(path + ".classifierGenericType.typeArguments[0].rawType.parameters['col'].multiplicity", functionType._parameters().getFirst()._multiplicity());
        expected.put(path + ".classifierGenericType.typeArguments[0].rawType.parameters['func']", functionType._parameters().getLast());
        expected.put(path + ".classifierGenericType.typeArguments[0].rawType.parameters['func'].genericType", functionType._parameters().getLast()._genericType());
//        expected.put(path + ".classifierGenericType.typeArguments[0].rawType.parameters['func'].genericType.typeArguments[0]", functionType._parameters().getLast()._genericType()._typeArguments().getOnly());
        expected.put(path + ".classifierGenericType.typeArguments[0].rawType.parameters['func'].genericType.typeArguments[0].rawType", functionType._parameters().getLast()._genericType()._typeArguments().getOnly()._rawType());
//        expected.put(
//                path + ".classifierGenericType.typeArguments[0].rawType.parameters['func'].genericType.typeArguments[0].rawType.parameters['']",
//                ((FunctionType) functionType._parameters().getLast()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        expected.put(
                path + ".classifierGenericType.typeArguments[0].rawType.parameters['func'].genericType.typeArguments[0].rawType.parameters[''].genericType",
                ((FunctionType) functionType._parameters().getLast()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        expected.put(
                path + ".classifierGenericType.typeArguments[0].rawType.parameters['func'].genericType.typeArguments[0].rawType.returnType",
                ((FunctionType) functionType._parameters().getLast()._genericType()._typeArguments().getOnly()._rawType())._returnType());
        expected.put(path + ".classifierGenericType.typeArguments[0].rawType.returnMultiplicity", functionType._returnMultiplicity());
        expected.put(path + ".classifierGenericType.typeArguments[0].rawType.returnType", functionType._returnType());

        SimpleFunctionExpression letExp = (SimpleFunctionExpression) testFunction._expressionSequence().getFirst();
        expected.put(path + ".expressionSequence[0]", letExp);
        expected.put(path + ".expressionSequence[0].genericType", letExp._genericType());
        expected.put(path + ".expressionSequence[0].genericType.typeArguments[0]", letExp._genericType()._typeArguments().getOnly());
        expected.put(path + ".expressionSequence[0].genericType.typeArguments[0].rawType", letExp._genericType()._typeArguments().getOnly()._rawType());
        expected.put(path + ".expressionSequence[0].genericType.typeArguments[0].rawType.parameters['']", ((FunctionType) letExp._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        expected.put(path + ".expressionSequence[0].genericType.typeArguments[0].rawType.parameters[''].genericType", ((FunctionType) letExp._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        expected.put(path + ".expressionSequence[0].genericType.typeArguments[0].rawType.parameters[''].genericType.typeParameter", ((FunctionType) letExp._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        expected.put(path + ".expressionSequence[0].genericType.typeArguments[0].rawType.returnType", ((FunctionType) letExp._genericType()._typeArguments().getOnly()._rawType())._returnType());

        InstanceValue letVar = (InstanceValue) letExp._parametersValues().getFirst();
        expected.put(path + ".expressionSequence[0].parametersValues[0]", letVar);
        expected.put(path + ".expressionSequence[0].parametersValues[0].genericType", letVar._genericType());

        SimpleFunctionExpression letValIfExp = (SimpleFunctionExpression) letExp._parametersValues().getLast();
        expected.put(path + ".expressionSequence[0].parametersValues[1]", letValIfExp);
        expected.put(path + ".expressionSequence[0].parametersValues[1].genericType", letValIfExp._genericType());
        expected.put(path + ".expressionSequence[0].parametersValues[1].genericType.typeArguments[0]", letValIfExp._genericType()._typeArguments().getOnly());
        expected.put(path + ".expressionSequence[0].parametersValues[1].genericType.typeArguments[0].rawType", letValIfExp._genericType()._typeArguments().getOnly()._rawType());
        expected.put(path + ".expressionSequence[0].parametersValues[1].genericType.typeArguments[0].rawType.parameters['']", ((FunctionType) letValIfExp._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        expected.put(path + ".expressionSequence[0].parametersValues[1].genericType.typeArguments[0].rawType.parameters[''].genericType", ((FunctionType) letValIfExp._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        expected.put(path + ".expressionSequence[0].parametersValues[1].genericType.typeArguments[0].rawType.parameters[''].genericType.typeParameter", ((FunctionType) letValIfExp._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        expected.put(path + ".expressionSequence[0].parametersValues[1].genericType.typeArguments[0].rawType.returnType", ((FunctionType) letValIfExp._genericType()._typeArguments().getOnly()._rawType())._returnType());

        ListIterable<? extends ValueSpecification> letValIfParams = ListHelper.wrapListIterable(letValIfExp._parametersValues());
        SimpleFunctionExpression letValIfCond = (SimpleFunctionExpression) letValIfParams.get(0);
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[0]", letValIfCond);
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[0].genericType", letValIfCond._genericType());
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[0]", letValIfCond._parametersValues().getOnly());
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[0].genericType", letValIfCond._parametersValues().getOnly()._genericType());
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[0].genericType.typeArguments[0]", letValIfCond._parametersValues().getOnly()._genericType()._typeArguments().getOnly());
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[0].genericType.typeArguments[0].rawType", letValIfCond._parametersValues().getOnly()._genericType()._typeArguments().getOnly()._rawType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[0].genericType.typeArguments[0].rawType.parameters['']",
                ((FunctionType) letValIfCond._parametersValues().getOnly()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[0].genericType.typeArguments[0].rawType.parameters[''].genericType",
                ((FunctionType) letValIfCond._parametersValues().getOnly()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[0].genericType.typeArguments[0].rawType.parameters[''].genericType.typeParameter",
                ((FunctionType) letValIfCond._parametersValues().getOnly()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[0].genericType.typeArguments[0].rawType.returnType",
                ((FunctionType) letValIfCond._parametersValues().getOnly()._genericType()._typeArguments().getOnly()._rawType())._returnType());

        InstanceValue letValIfTrue = (InstanceValue) letValIfParams.get(1);
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[1]", letValIfTrue);
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].genericType", letValIfTrue._genericType());
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].genericType.typeArguments[0]", letValIfTrue._genericType()._typeArguments().getOnly());
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].genericType.typeArguments[0].rawType", letValIfTrue._genericType()._typeArguments().getOnly()._rawType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].genericType.typeArguments[0].rawType.returnType",
                ((FunctionType) letValIfTrue._genericType()._typeArguments().getOnly()._rawType())._returnType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].genericType.typeArguments[0].rawType.returnType.typeArguments[0]",
                ((FunctionType) letValIfTrue._genericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].genericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType",
                ((FunctionType) letValIfTrue._genericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].genericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.parameters['x']",
                ((FunctionType) ((FunctionType) letValIfTrue._genericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].genericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.parameters['x'].genericType",
                ((FunctionType) ((FunctionType) letValIfTrue._genericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].genericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.parameters['x'].genericType.typeParameter",
                ((FunctionType) ((FunctionType) letValIfTrue._genericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].genericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.returnType",
                ((FunctionType) ((FunctionType) letValIfTrue._genericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._returnType());

        LambdaFunction<?> letValIfTrueLambda = (LambdaFunction<?>) letValIfTrue._values().getOnly();
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0]", letValIfTrueLambda);
//        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].classifierGenericType", letValIfTrueLambda._classifierGenericType());
//        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0]", letValIfTrueLambda._classifierGenericType()._typeArguments().getOnly());
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType", letValIfTrueLambda._classifierGenericType()._typeArguments().getOnly()._rawType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.returnType",
                ((FunctionType) letValIfTrueLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.returnType.typeArguments[0]",
                ((FunctionType) letValIfTrueLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType",
                ((FunctionType) letValIfTrueLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.parameters['x']",
                ((FunctionType) ((FunctionType) letValIfTrueLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.parameters['x'].genericType",
                ((FunctionType) ((FunctionType) letValIfTrueLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.parameters['x'].genericType.typeParameter",
                ((FunctionType) ((FunctionType) letValIfTrueLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.returnType",
                ((FunctionType) ((FunctionType) letValIfTrueLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._returnType());
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0]", letValIfTrueLambda._expressionSequence().getOnly());
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].genericType", letValIfTrueLambda._expressionSequence().getOnly()._genericType());
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].genericType.typeArguments[0]", letValIfTrueLambda._expressionSequence().getOnly()._genericType()._typeArguments().getOnly());
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].genericType.typeArguments[0].rawType", letValIfTrueLambda._expressionSequence().getOnly()._genericType()._typeArguments().getOnly()._rawType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].genericType.typeArguments[0].rawType.parameters['x']",
                ((FunctionType) letValIfTrueLambda._expressionSequence().getOnly()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].genericType.typeArguments[0].rawType.parameters['x'].genericType",
                ((FunctionType) letValIfTrueLambda._expressionSequence().getOnly()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].genericType.typeArguments[0].rawType.parameters['x'].genericType.typeParameter",
                ((FunctionType) letValIfTrueLambda._expressionSequence().getOnly()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].genericType.typeArguments[0].rawType.returnType",
                ((FunctionType) letValIfTrueLambda._expressionSequence().getOnly()._genericType()._typeArguments().getOnly()._rawType())._returnType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].values[0]",
                (LambdaFunction<?>) ((InstanceValue) letValIfTrueLambda._expressionSequence().getOnly())._values().getOnly());
//        expected.put(
//                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].values[0].classifierGenericType",
//                ((LambdaFunction<?>) ((InstanceValue) letValIfTrueLambda._expressionSequence().getOnly())._values().getOnly())._classifierGenericType());
//        expected.put(
//                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].values[0].classifierGenericType.typeArguments[0]",
//                ((LambdaFunction<?>) ((InstanceValue) letValIfTrueLambda._expressionSequence().getOnly())._values().getOnly())._classifierGenericType()._typeArguments().getOnly());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].values[0].classifierGenericType.typeArguments[0].rawType",
                ((LambdaFunction<?>) ((InstanceValue) letValIfTrueLambda._expressionSequence().getOnly())._values().getOnly())._classifierGenericType()._typeArguments().getOnly()._rawType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].values[0].classifierGenericType.typeArguments[0].rawType.parameters['x']",
                ((FunctionType) ((LambdaFunction<?>) ((InstanceValue) letValIfTrueLambda._expressionSequence().getOnly())._values().getOnly())._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].values[0].classifierGenericType.typeArguments[0].rawType.parameters['x'].genericType",
                ((FunctionType) ((LambdaFunction<?>) ((InstanceValue) letValIfTrueLambda._expressionSequence().getOnly())._values().getOnly())._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].values[0].classifierGenericType.typeArguments[0].rawType.returnType",
                ((FunctionType) ((LambdaFunction<?>) ((InstanceValue) letValIfTrueLambda._expressionSequence().getOnly())._values().getOnly())._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].values[0].expressionSequence[0]",
                ((LambdaFunction<?>) ((InstanceValue) letValIfTrueLambda._expressionSequence().getOnly())._values().getOnly())._expressionSequence().getOnly());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].values[0].expressionSequence[0].genericType",
                ((LambdaFunction<?>) ((InstanceValue) letValIfTrueLambda._expressionSequence().getOnly())._values().getOnly())._expressionSequence().getOnly()._genericType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].values[0].expressionSequence[0].parametersValues[0]",
                ((SimpleFunctionExpression) ((LambdaFunction<?>) ((InstanceValue) letValIfTrueLambda._expressionSequence().getOnly())._values().getOnly())._expressionSequence().getOnly())._parametersValues().getOnly());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].values[0].expressionSequence[0].parametersValues[0].genericType",
                ((SimpleFunctionExpression) ((LambdaFunction<?>) ((InstanceValue) letValIfTrueLambda._expressionSequence().getOnly())._values().getOnly())._expressionSequence().getOnly())._parametersValues().getOnly()._genericType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].values[0].expressionSequence[0].parametersValues[0].genericType.typeParameter",
                ((SimpleFunctionExpression) ((LambdaFunction<?>) ((InstanceValue) letValIfTrueLambda._expressionSequence().getOnly())._values().getOnly())._expressionSequence().getOnly())._parametersValues().getOnly()._genericType()._typeParameter());

        InstanceValue letValIfFalse = (InstanceValue) letValIfParams.get(2);
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[2]", letValIfFalse);
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[2].genericType", letValIfFalse._genericType());
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[2].genericType.typeArguments[0]", letValIfFalse._genericType()._typeArguments().getOnly());
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[2].genericType.typeArguments[0].rawType", letValIfFalse._genericType()._typeArguments().getOnly()._rawType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].genericType.typeArguments[0].rawType.returnType",
                ((FunctionType) letValIfFalse._genericType()._typeArguments().getOnly()._rawType())._returnType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].genericType.typeArguments[0].rawType.returnType.typeArguments[0]",
                ((FunctionType) letValIfFalse._genericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].genericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType",
                ((FunctionType) letValIfFalse._genericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].genericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.parameters['']",
                ((FunctionType) ((FunctionType) letValIfFalse._genericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].genericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.parameters[''].genericType",
                ((FunctionType) ((FunctionType) letValIfFalse._genericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].genericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.parameters[''].genericType.typeParameter",
                ((FunctionType) ((FunctionType) letValIfFalse._genericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].genericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.returnType",
                ((FunctionType) ((FunctionType) letValIfFalse._genericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._returnType());

        LambdaFunction<?> letValIfFalseLambda = (LambdaFunction<?>) letValIfFalse._values().getOnly();
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0]", letValIfFalseLambda);
//        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].classifierGenericType", letValIfFalseLambda._classifierGenericType());
//        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].classifierGenericType.typeArguments[0]", letValIfFalseLambda._classifierGenericType()._typeArguments().getOnly());
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType", letValIfFalseLambda._classifierGenericType()._typeArguments().getOnly()._rawType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType.returnType",
                ((FunctionType) letValIfFalseLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType.returnType.typeArguments[0]",
                ((FunctionType) letValIfFalseLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType",
                ((FunctionType) letValIfFalseLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.parameters['']",
                ((FunctionType) ((FunctionType) letValIfFalseLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.parameters[''].genericType",
                ((FunctionType) ((FunctionType) letValIfFalseLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.parameters[''].genericType.typeParameter",
                ((FunctionType) ((FunctionType) letValIfFalseLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.returnType",
                ((FunctionType) ((FunctionType) letValIfFalseLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._returnType());
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0]", letValIfFalseLambda._expressionSequence().getOnly());
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].genericType", letValIfFalseLambda._expressionSequence().getOnly()._genericType());
        expected.put(path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].genericType.typeArguments[0]", letValIfFalseLambda._expressionSequence().getOnly()._genericType()._typeArguments().getOnly());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].genericType.typeArguments[0].rawType",
                letValIfFalseLambda._expressionSequence().getOnly()._genericType()._typeArguments().getOnly()._rawType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].genericType.typeArguments[0].rawType.parameters['']",
                ((FunctionType) letValIfFalseLambda._expressionSequence().getOnly()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].genericType.typeArguments[0].rawType.parameters[''].genericType",
                ((FunctionType) letValIfFalseLambda._expressionSequence().getOnly()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].genericType.typeArguments[0].rawType.parameters[''].genericType.typeParameter",
                ((FunctionType) letValIfFalseLambda._expressionSequence().getOnly()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].genericType.typeArguments[0].rawType.returnType",
                ((FunctionType) letValIfFalseLambda._expressionSequence().getOnly()._genericType()._typeArguments().getOnly()._rawType())._returnType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].parametersValues[0]",
                ((SimpleFunctionExpression) letValIfFalseLambda._expressionSequence().getOnly())._parametersValues().getOnly());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].parametersValues[0].genericType",
                ((SimpleFunctionExpression) letValIfFalseLambda._expressionSequence().getOnly())._parametersValues().getOnly()._genericType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].parametersValues[0].genericType.typeArguments[0]",
                ((SimpleFunctionExpression) letValIfFalseLambda._expressionSequence().getOnly())._parametersValues().getOnly()._genericType()._typeArguments().getOnly());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].parametersValues[0].genericType.typeArguments[0].rawType",
                ((SimpleFunctionExpression) letValIfFalseLambda._expressionSequence().getOnly())._parametersValues().getOnly()._genericType()._typeArguments().getOnly()._rawType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].parametersValues[0].genericType.typeArguments[0].rawType.parameters['']",
                ((FunctionType) ((SimpleFunctionExpression) letValIfFalseLambda._expressionSequence().getOnly())._parametersValues().getOnly()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].parametersValues[0].genericType.typeArguments[0].rawType.parameters[''].genericType",
                ((FunctionType) ((SimpleFunctionExpression) letValIfFalseLambda._expressionSequence().getOnly())._parametersValues().getOnly()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].parametersValues[0].genericType.typeArguments[0].rawType.parameters[''].genericType.typeParameter",
                ((FunctionType) ((SimpleFunctionExpression) letValIfFalseLambda._expressionSequence().getOnly())._parametersValues().getOnly()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        expected.put(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].parametersValues[0].genericType.typeArguments[0].rawType.returnType",
                ((FunctionType) ((SimpleFunctionExpression) letValIfFalseLambda._expressionSequence().getOnly())._parametersValues().getOnly()._genericType()._typeArguments().getOnly()._rawType())._returnType());

        SimpleFunctionExpression mapExp = (SimpleFunctionExpression) testFunction._expressionSequence().getLast();
        VariableExpression mapColParam = (VariableExpression) mapExp._parametersValues().getFirst();
        InstanceValue mapFuncParam = (InstanceValue) mapExp._parametersValues().getLast();
        LambdaFunction<?> mapFunc = (LambdaFunction<?>) mapFuncParam._values().getOnly();
        expected.put(path + ".expressionSequence[1]", mapExp);
        expected.put(path + ".expressionSequence[1].genericType", mapExp._genericType());
        expected.put(path + ".expressionSequence[1].multiplicity", mapExp._multiplicity());
        expected.put(path + ".expressionSequence[1].parametersValues[0]", mapColParam);
        expected.put(path + ".expressionSequence[1].parametersValues[0].genericType", mapColParam._genericType());
        expected.put(path + ".expressionSequence[1].parametersValues[0].genericType.typeParameter", mapColParam._genericType()._typeParameter());
        expected.put(path + ".expressionSequence[1].parametersValues[0].multiplicity", mapColParam._multiplicity());
        expected.put(path + ".expressionSequence[1].parametersValues[1]", mapFuncParam);
        expected.put(path + ".expressionSequence[1].parametersValues[1].genericType", mapFuncParam._genericType());
        expected.put(path + ".expressionSequence[1].parametersValues[1].genericType.typeArguments[0]", mapFuncParam._genericType()._typeArguments().getOnly());
        expected.put(path + ".expressionSequence[1].parametersValues[1].genericType.typeArguments[0].rawType", mapFuncParam._genericType()._typeArguments().getOnly()._rawType());
        expected.put(
                path + ".expressionSequence[1].parametersValues[1].genericType.typeArguments[0].rawType.parameters['x']",
                ((FunctionType) mapFuncParam._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        expected.put(
                path + ".expressionSequence[1].parametersValues[1].genericType.typeArguments[0].rawType.parameters['x'].genericType",
                ((FunctionType) mapFuncParam._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        expected.put(
                path + ".expressionSequence[1].parametersValues[1].genericType.typeArguments[0].rawType.parameters['x'].genericType.typeParameter",
                ((FunctionType) mapFuncParam._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        expected.put(
                path + ".expressionSequence[1].parametersValues[1].genericType.typeArguments[0].rawType.returnType",
                ((FunctionType) mapFuncParam._genericType()._typeArguments().getOnly()._rawType())._returnType());
        expected.put(path + ".expressionSequence[1].parametersValues[1].values[0]", mapFunc);
//        expected.put(path + ".expressionSequence[1].parametersValues[1].values[0].classifierGenericType", mapFunc._classifierGenericType());
//        expected.put(path + ".expressionSequence[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0]", mapFunc._classifierGenericType()._typeArguments().getOnly());
        expected.put(path + ".expressionSequence[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType", mapFunc._classifierGenericType()._typeArguments().getOnly()._rawType());
        expected.put(
                path + ".expressionSequence[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['x']",
                ((FunctionType) mapFunc._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        expected.put(
                path + ".expressionSequence[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['x'].genericType",
                ((FunctionType) mapFunc._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        expected.put(
                path + ".expressionSequence[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['x'].genericType.typeParameter",
                ((FunctionType) mapFunc._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        expected.put(
                path + ".expressionSequence[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.returnType",
                ((FunctionType) mapFunc._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType());
        expected.put(path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0]", mapFunc._expressionSequence().getOnly());
        expected.put(path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0].genericType", mapFunc._expressionSequence().getOnly()._genericType());
        expected.put(
                path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]",
                ((SimpleFunctionExpression) mapFunc._expressionSequence().getOnly())._parametersValues().getFirst());
        expected.put(
                path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].genericType",
                ((SimpleFunctionExpression) mapFunc._expressionSequence().getOnly())._parametersValues().getFirst()._genericType());
        expected.put(
                path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].genericType.typeArguments[0]",
                ((SimpleFunctionExpression) mapFunc._expressionSequence().getOnly())._parametersValues().getFirst()._genericType()._typeArguments().getOnly());
        expected.put(
                path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].genericType.typeArguments[0].rawType",
                ((SimpleFunctionExpression) mapFunc._expressionSequence().getOnly())._parametersValues().getFirst()._genericType()._typeArguments().getOnly()._rawType());
        expected.put(
                path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].genericType.typeArguments[0].rawType.parameters['']",
                ((FunctionType) ((SimpleFunctionExpression) mapFunc._expressionSequence().getOnly())._parametersValues().getFirst()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        expected.put(
                path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].genericType.typeArguments[0].rawType.parameters[''].genericType",
                ((FunctionType) ((SimpleFunctionExpression) mapFunc._expressionSequence().getOnly())._parametersValues().getFirst()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        expected.put(
                path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].genericType.typeArguments[0].rawType.parameters[''].genericType.typeParameter",
                ((FunctionType) ((SimpleFunctionExpression) mapFunc._expressionSequence().getOnly())._parametersValues().getFirst()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        expected.put(
                path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].genericType.typeArguments[0].rawType.returnType",
                ((FunctionType) ((SimpleFunctionExpression) mapFunc._expressionSequence().getOnly())._parametersValues().getFirst()._genericType()._typeArguments().getOnly()._rawType())._returnType());
        expected.put(
                path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[1]",
                ((SimpleFunctionExpression) mapFunc._expressionSequence().getOnly())._parametersValues().getLast());
        expected.put(
                path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[1].genericType",
                ((SimpleFunctionExpression) mapFunc._expressionSequence().getOnly())._parametersValues().getLast()._genericType());
        expected.put(
                path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[1].genericType.typeParameter",
                ((SimpleFunctionExpression) mapFunc._expressionSequence().getOnly())._parametersValues().getLast()._genericType()._typeParameter());

        assertIds(path, expected);
    }

    @Test
    public void testGenericTypes()
    {
        String path = "test::testFunc_T_m__Function_$0_1$__String_m_";
        ConcreteFunctionDefinition<?> testFunction = getCoreInstance(path);
        assertGenericType(
                "meta::pure::metamodel::function::Function<{T[1]->String[1]}>",
                testFunction._expressionSequence().getFirst()._genericType());
        assertGenericType(
                "meta::pure::metamodel::function::Function<{T[1]->String[1]}>",
                ((SimpleFunctionExpression) testFunction._expressionSequence().getFirst())._parametersValues().toList().get(1)._genericType());
        assertGenericType(
                "meta::pure::metamodel::function::Function<{T[1]->String[1]}>",
                ((SimpleFunctionExpression) ((LambdaFunction<?>) ((InstanceValue) ((SimpleFunctionExpression) testFunction._expressionSequence().getLast())._parametersValues().getLast())._values().getOnly())._expressionSequence().getOnly())._parametersValues().getFirst()._genericType()
        );
    }

    private void assertGenericType(String expected, org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.generics.GenericType genericType)
    {
        String actual = GenericType.print(genericType, true, processorSupport);
        Assert.assertEquals(genericType.getSourceInformation().getMessage(), expected, actual);
    }

    @SuppressWarnings("unchecked")
    private <T> T getCoreInstance(String path)
    {
        return (T) runtime.getCoreInstance(path);
    }

    private void assertIds(org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.PackageableElement element)
    {
        MutableMap<CoreInstance, String> idsByInstance = idGenerator.generateIdsForElement(element);
        String path = PackageableElement.getUserPathForPackageableElement(element);
        Assert.assertEquals(path, idsByInstance.get(element));
        Assert.assertSame(element, reverseIdMap(idsByInstance, path).get(path));
    }

    private void assertIds(String path, MutableMap<String, ?> expected)
    {
        validateExpectedIds(path, expected);
        MutableMap<CoreInstance, String> idsByInstance = idGenerator.generateIdsForElement(path);
        MutableMap<String, CoreInstance> instancesById = reverseIdMap(idsByInstance, path);
        if (!expected.equals(instancesById))
        {
            MutableList<Pair<String, ?>> expectedMismatches = Lists.mutable.empty();
            Counter expectedMissing = new Counter();
            Counter mismatches = new Counter();
            Counter unexpected = new Counter();
            expected.forEachKeyValue((id, instance) ->
            {
                CoreInstance actualInstance = instancesById.get(id);
                if (!instance.equals(actualInstance))
                {
                    expectedMismatches.add(Tuples.pair(id, instance));
                    ((actualInstance == null) ? expectedMissing : mismatches).increment();
                }
            });
            MutableList<Pair<String, ?>> actualMismatches = Lists.mutable.empty();
            instancesById.forEachKeyValue((id, instance) ->
            {
                Object expectedInstance = expected.get(id);
                if (!instance.equals(expectedInstance))
                {
                    actualMismatches.add(Tuples.pair(id, instance));
                    if (expectedInstance == null)
                    {
                        unexpected.increment();
                    }
                }
            });
            Assert.assertEquals(
                    "Ids for " + path + " not as expected (" + expectedMissing.getCount() + " expected missing, " + mismatches.getCount() + " mismatches, " + unexpected.getCount() + " unexpected found)",
                    expectedMismatches.sortThis().makeString(System.lineSeparator()),
                    actualMismatches.sortThis().makeString(System.lineSeparator()));
        }
    }

    private void validateExpectedIds(String path, MutableMap<String, ?> expected)
    {
        MutableList<String> nullInstances = Lists.mutable.empty();
        expected.forEachKeyValue((id, instance) ->
        {
            if (instance == null)
            {
                nullInstances.add(id);
            }
        });
        if (nullInstances.notEmpty())
        {
            StringBuilder builder = new StringBuilder("Null instances for ").append(nullInstances.size()).append(" expected ids for \"").append(path).append("\":");
            nullInstances.sortThis().appendString(builder, "\n\t", "\n\t", "");
            Assert.fail(builder.toString());
        }
    }

    private MutableMap<String, CoreInstance> reverseIdMap(MutableMap<CoreInstance, String> idsByInstance, String path)
    {
        MutableMap<String, CoreInstance> instancesById = Maps.mutable.ofInitialCapacity(idsByInstance.size());
        MutableSet<String> duplicateIds = Sets.mutable.empty();
        idsByInstance.forEachKeyValue((instance, id) ->
        {
            if (instancesById.put(id, instance) != null)
            {
                duplicateIds.add(id);
            }
        });
        if (duplicateIds.notEmpty())
        {
            Assert.fail(duplicateIds.toSortedList().makeString("Duplicate ids for " + path + ": \"", "\", \"", "\""));
        }
        return instancesById;
    }

    private Property<?, ?> findProperty(PropertyOwner owner, String name)
    {
        RichIterable<? extends Property<?, ?>> properties = (owner instanceof Class) ? ((Class<?>) owner)._properties() : ((Association) owner)._properties();
        Property<?, ?> property = properties.detect(p -> name.equals(org.finos.legend.pure.m3.navigation.property.Property.getPropertyName(p)));
        if (property == null)
        {
            Assert.fail("Could not find property '" + name + "' for " + PackageableElement.getUserPathForPackageableElement(owner));
        }
        return property;
    }
}
