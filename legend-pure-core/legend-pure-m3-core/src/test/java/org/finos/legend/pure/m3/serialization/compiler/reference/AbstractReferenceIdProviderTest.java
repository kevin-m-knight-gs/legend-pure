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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.tuple.Tuples;
import org.finos.legend.pure.m3.coreinstance.Package;
import org.finos.legend.pure.m3.coreinstance.helper.AnyStubHelper;
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
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Measure;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Unit;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.InstanceValue;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.SimpleFunctionExpression;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.ValueSpecification;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.VariableExpression;
import org.finos.legend.pure.m3.navigation.M3PropertyPaths;
import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;
import org.finos.legend.pure.m3.navigation._package._Package;
import org.finos.legend.pure.m3.tools.PackageTreeIterable;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;
import org.finos.legend.pure.m4.tools.GraphNodeIterable;
import org.finos.legend.pure.m4.tools.GraphWalkFilterResult;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Comparator;
import java.util.Objects;

public abstract class AbstractReferenceIdProviderTest extends AbstractReferenceTest
{
    static ReferenceIdGenerator idGenerator;
    protected static ContainingElementIndex containingElementIndex;
    protected static ReferenceIdProvider referenceIdProvider;

    @BeforeClass
    public static void setUpIdGeneratorAndElementIndex()
    {
        idGenerator = new ReferenceIdGenerator(processorSupport);
        containingElementIndex = ContainingElementIndexes.fromModelRepository(repository);
    }

    @Test
    public void testVirtualPackages()
    {
        String testPath = "test";
        Package testPackage = getCoreInstance(testPath);
        Assert.assertNull(testPackage.getSourceInformation());
        assertId(testPath, testPackage);

        String testModelPath = "test::model";
        Package testModelPackage = getCoreInstance(testModelPath);
        Assert.assertNull(testModelPackage.getSourceInformation());
        assertId(testModelPath, testModelPackage);
    }

    @Test
    public void testSimpleClass()
    {
        String path = "test::model::SimpleClass";
        Class<?> simpleClass = getCoreInstance(path);
        assertId(path, simpleClass);

        Property<?, ?> name = findProperty(simpleClass, "name");
        assertId(path + ".properties['name']", name);
        assertId(path + ".properties['name'].classifierGenericType", name._classifierGenericType());
        assertId(path + ".properties['name'].classifierGenericType.typeArguments[1]", name._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".properties['name'].genericType", name._genericType());

        Property<?, ?> id = findProperty(simpleClass, "id");
        assertId(path + ".properties['id']", id);
        assertId(path + ".properties['id'].classifierGenericType", id._classifierGenericType());
        assertId(path + ".properties['id'].classifierGenericType.typeArguments[1]", id._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".properties['id'].genericType", id._genericType());
    }

    @Test
    public void testEnumeration()
    {
        String path = "test::model::SimpleEnumeration";
        Enumeration<? extends Enum> testEnumeration = getCoreInstance(path);
        assertId(path, testEnumeration);

        ListIterable<? extends Enum> enums = toList(testEnumeration._values());
        assertId(path + ".values['VAL1']", enums.get(0));
        assertId(path + ".values['VAL2']", enums.get(1));
    }

    @Test
    public void testAssociation()
    {
        String path = "test::model::LeftRight";
        Association leftRight = getCoreInstance(path);
        assertId(path, leftRight);

        Property<?, ?> toLeft = findProperty(leftRight, "toLeft");
        assertId(path + ".properties['toLeft']", toLeft);
        assertId(path + ".properties['toLeft'].classifierGenericType", toLeft._classifierGenericType());
        assertId(path + ".properties['toLeft'].classifierGenericType.typeArguments[0]", toLeft._classifierGenericType()._typeArguments().getFirst());
        assertId(path + ".properties['toLeft'].classifierGenericType.typeArguments[1]", toLeft._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".properties['toLeft'].genericType", toLeft._genericType());

        Property<?, ?> toRight = findProperty(leftRight, "toRight");
        assertId(path + ".properties['toRight']", toRight);
        assertId(path + ".properties['toRight'].classifierGenericType", toRight._classifierGenericType());
        assertId(path + ".properties['toRight'].classifierGenericType.typeArguments[0]", toRight._classifierGenericType()._typeArguments().getFirst());
        assertId(path + ".properties['toRight'].classifierGenericType.typeArguments[1]", toRight._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".properties['toRight'].genericType", toRight._genericType());

        QualifiedProperty<?> toLeftByName = findQualifiedProperty(leftRight, "toLeft(String[1])");
        assertId(path + ".qualifiedProperties[id='toLeft(String[1])']", toLeftByName);
        assertId(path + ".qualifiedProperties[id='toLeft(String[1])'].classifierGenericType.typeArguments[0].rawType", toLeftByName._classifierGenericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].classifierGenericType.typeArguments[0].rawType.parameters['name']",
                ((FunctionType) toLeftByName._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getLast());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].classifierGenericType.typeArguments[0].rawType.parameters['name'].genericType",
                ((FunctionType) toLeftByName._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getLast()._genericType());
        assertId(path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0]", toLeftByName._expressionSequence().getOnly());
        assertId(path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].genericType", toLeftByName._expressionSequence().getOnly()._genericType());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[0]",
                ((SimpleFunctionExpression) toLeftByName._expressionSequence().getOnly())._parametersValues().getFirst());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[0].genericType",
                ((SimpleFunctionExpression) toLeftByName._expressionSequence().getOnly())._parametersValues().getFirst()._genericType());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[0].parametersValues[0]",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) toLeftByName._expressionSequence().getOnly())._parametersValues().getFirst())._parametersValues().getOnly());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[0].parametersValues[0].genericType",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) toLeftByName._expressionSequence().getOnly())._parametersValues().getFirst())._parametersValues().getOnly()._genericType());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[0].propertyName",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) toLeftByName._expressionSequence().getOnly())._parametersValues().getFirst())._propertyName());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1]",
                ((SimpleFunctionExpression) toLeftByName._expressionSequence().getOnly())._parametersValues().getLast());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].genericType",
                ((SimpleFunctionExpression) toLeftByName._expressionSequence().getOnly())._parametersValues().getLast()._genericType());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].genericType.typeArguments[0]",
                ((SimpleFunctionExpression) toLeftByName._expressionSequence().getOnly())._parametersValues().getLast()._genericType()._typeArguments().getOnly());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].genericType.typeArguments[0].rawType",
                ((SimpleFunctionExpression) toLeftByName._expressionSequence().getOnly())._parametersValues().getLast()._genericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].genericType.typeArguments[0].rawType.parameters['l']",
                ((FunctionType) ((SimpleFunctionExpression) toLeftByName._expressionSequence().getOnly())._parametersValues().getLast()._genericType()._typeArguments().getOnly()._rawType())
                        ._parameters().getOnly());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].genericType.typeArguments[0].rawType.parameters['l'].genericType",
                ((FunctionType) ((SimpleFunctionExpression) toLeftByName._expressionSequence().getOnly())._parametersValues().getLast()._genericType()._typeArguments().getOnly()._rawType())
                        ._parameters().getOnly()._genericType());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].genericType.typeArguments[0].rawType.returnType",
                ((FunctionType) ((SimpleFunctionExpression) toLeftByName._expressionSequence().getOnly())._parametersValues().getLast()._genericType()._typeArguments().getOnly()._rawType())._returnType());
        LambdaFunction<?> nameFilterLambda = (LambdaFunction<?>) ((InstanceValue) ((SimpleFunctionExpression) toLeftByName._expressionSequence().getOnly())._parametersValues().getLast())._values().getOnly();
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].values[0]",
                nameFilterLambda);
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType",
                nameFilterLambda._classifierGenericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['l']",
                ((FunctionType) nameFilterLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['l'].genericType",
                ((FunctionType) nameFilterLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.returnType",
                ((FunctionType) nameFilterLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0]",
                nameFilterLambda._expressionSequence().getOnly());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].genericType",
                nameFilterLambda._expressionSequence().getOnly()._genericType());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]",
                ((SimpleFunctionExpression) nameFilterLambda._expressionSequence().getOnly())._parametersValues().getFirst());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].genericType",
                ((SimpleFunctionExpression) nameFilterLambda._expressionSequence().getOnly())._parametersValues().getFirst()._genericType());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].parametersValues[0]",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) nameFilterLambda._expressionSequence().getOnly())._parametersValues().getFirst())._parametersValues().getOnly());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].parametersValues[0].genericType",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) nameFilterLambda._expressionSequence().getOnly())._parametersValues().getFirst())._parametersValues().getOnly()._genericType());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].propertyName",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) nameFilterLambda._expressionSequence().getOnly())._parametersValues().getFirst())._propertyName());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[1]",
                ((SimpleFunctionExpression) nameFilterLambda._expressionSequence().getOnly())._parametersValues().getLast());
        assertId(
                path + ".qualifiedProperties[id='toLeft(String[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[1].genericType",
                ((SimpleFunctionExpression) nameFilterLambda._expressionSequence().getOnly())._parametersValues().getLast()._genericType());
        assertId(path + ".qualifiedProperties[id='toLeft(String[1])'].genericType", toLeftByName._genericType());

        QualifiedProperty<?> toRightById = findQualifiedProperty(leftRight, "toRight(Integer[1])");
        assertId(path + ".qualifiedProperties[id='toRight(Integer[1])']", toRightById);
        assertId(path + ".qualifiedProperties[id='toRight(Integer[1])'].classifierGenericType.typeArguments[0].rawType", toRightById._classifierGenericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].classifierGenericType.typeArguments[0].rawType.parameters['id']",
                ((FunctionType) toRightById._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getLast());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].classifierGenericType.typeArguments[0].rawType.parameters['id'].genericType",
                ((FunctionType) toRightById._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getLast()._genericType());
        assertId(path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0]", toRightById._expressionSequence().getOnly());
        assertId(path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].genericType", toRightById._expressionSequence().getOnly()._genericType());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[0]",
                ((SimpleFunctionExpression) toRightById._expressionSequence().getOnly())._parametersValues().getFirst());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[0].genericType",
                ((SimpleFunctionExpression) toRightById._expressionSequence().getOnly())._parametersValues().getFirst()._genericType());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[0].parametersValues[0]",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) toRightById._expressionSequence().getOnly())._parametersValues().getFirst())._parametersValues().getOnly());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[0].parametersValues[0].genericType",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) toRightById._expressionSequence().getOnly())._parametersValues().getFirst())._parametersValues().getOnly()._genericType());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[0].propertyName",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) toRightById._expressionSequence().getOnly())._parametersValues().getFirst())._propertyName());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[1]",
                ((SimpleFunctionExpression) toRightById._expressionSequence().getOnly())._parametersValues().getLast());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[1].genericType",
                ((SimpleFunctionExpression) toRightById._expressionSequence().getOnly())._parametersValues().getLast()._genericType());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[1].genericType.typeArguments[0]",
                ((SimpleFunctionExpression) toRightById._expressionSequence().getOnly())._parametersValues().getLast()._genericType()._typeArguments().getOnly());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[1].genericType.typeArguments[0].rawType",
                ((SimpleFunctionExpression) toRightById._expressionSequence().getOnly())._parametersValues().getLast()._genericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[1].genericType.typeArguments[0].rawType.parameters['r']",
                ((FunctionType) ((SimpleFunctionExpression) toRightById._expressionSequence().getOnly())._parametersValues().getLast()._genericType()._typeArguments().getOnly()._rawType())
                        ._parameters().getOnly());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[1].genericType.typeArguments[0].rawType.parameters['r'].genericType",
                ((FunctionType) ((SimpleFunctionExpression) toRightById._expressionSequence().getOnly())._parametersValues().getLast()._genericType()._typeArguments().getOnly()._rawType())
                        ._parameters().getOnly()._genericType());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[1].genericType.typeArguments[0].rawType.returnType",
                ((FunctionType) ((SimpleFunctionExpression) toRightById._expressionSequence().getOnly())._parametersValues().getLast()._genericType()._typeArguments().getOnly()._rawType())._returnType());
        LambdaFunction<?> idFilterLambda = (LambdaFunction<?>) ((InstanceValue) ((SimpleFunctionExpression) toRightById._expressionSequence().getOnly())._parametersValues().getLast())._values().getOnly();
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[1].values[0]",
                idFilterLambda);
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType",
                idFilterLambda._classifierGenericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['r']",
                ((FunctionType) idFilterLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['r'].genericType",
                ((FunctionType) idFilterLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.returnType",
                ((FunctionType) idFilterLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0]",
                idFilterLambda._expressionSequence().getOnly());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].genericType",
                idFilterLambda._expressionSequence().getOnly()._genericType());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]",
                ((SimpleFunctionExpression) idFilterLambda._expressionSequence().getOnly())._parametersValues().getFirst());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].genericType",
                ((SimpleFunctionExpression) idFilterLambda._expressionSequence().getOnly())._parametersValues().getFirst()._genericType());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].parametersValues[0]",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) idFilterLambda._expressionSequence().getOnly())._parametersValues().getFirst())._parametersValues().getOnly());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].parametersValues[0].genericType",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) idFilterLambda._expressionSequence().getOnly())._parametersValues().getFirst())._parametersValues().getOnly()._genericType());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].propertyName",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) idFilterLambda._expressionSequence().getOnly())._parametersValues().getFirst())._propertyName());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[1]",
                ((SimpleFunctionExpression) idFilterLambda._expressionSequence().getOnly())._parametersValues().getLast());
        assertId(
                path + ".qualifiedProperties[id='toRight(Integer[1])'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].parametersValues[1].genericType",
                ((SimpleFunctionExpression) idFilterLambda._expressionSequence().getOnly())._parametersValues().getLast()._genericType());
        assertId(path + ".qualifiedProperties[id='toRight(Integer[1])'].genericType", toRightById._genericType());

        String leftPath = "test::model::Left";
        Class<?> left = getCoreInstance(leftPath);
        assertId(leftPath, left);

        Property<?, ?> leftName = findProperty(left, "name");
        assertId(leftPath + ".properties['name']", leftName);
        assertId(leftPath + ".properties['name'].classifierGenericType", leftName._classifierGenericType());
        assertId(leftPath + ".properties['name'].classifierGenericType.typeArguments[1]", leftName._classifierGenericType()._typeArguments().getLast());
        assertId(leftPath + ".properties['name'].genericType", leftName._genericType());

        String rightPath = "test::model::Right";
        Class<?> right = getCoreInstance(rightPath);
        assertId(rightPath, right);

        Property<?, ?> rightId = findProperty(right, "id");
        assertId(rightPath + ".properties['id']", rightId);
        assertId(rightPath + ".properties['id'].classifierGenericType", rightId._classifierGenericType());
        assertId(rightPath + ".properties['id'].classifierGenericType.typeArguments[1]", rightId._classifierGenericType()._typeArguments().getLast());
        assertId(rightPath + ".properties['id'].genericType", rightId._genericType());
    }

    @Test
    public void testSimpleProfile()
    {
        String path = "test::model::SimpleProfile";
        Profile testProfile = getCoreInstance(path);
        assertId(path, testProfile);

        ListIterable<? extends Stereotype> stereotypes = toList(testProfile._p_stereotypes());
        assertId(path + ".p_stereotypes[value='st1']", stereotypes.get(0));
        assertId(path + ".p_stereotypes[value='st2']", stereotypes.get(1));

        ListIterable<? extends Tag> tags = toList(testProfile._p_tags());
        assertId(path + ".p_tags[value='t1']", tags.get(0));
        assertId(path + ".p_tags[value='t2']", tags.get(1));
        assertId(path + ".p_tags[value='t3']", tags.get(2));
    }

    @Test
    public void testClassWithGeneralizations()
    {
        String path = "test::model::BothSides";
        Class<?> bothSides = getCoreInstance(path);
        assertId(path, bothSides);

        ListIterable<? extends Generalization> generalizations = toList(bothSides._generalizations());
        assertId(path + ".generalizations[0].general", generalizations.get(0)._general());
        assertId(path + ".generalizations[1].general", generalizations.get(1)._general());

        Property<?, ?> leftCount = findProperty(bothSides, "leftCount");
        assertId(path + ".properties['leftCount']", leftCount);
        assertId(path + ".properties['leftCount'].classifierGenericType", leftCount._classifierGenericType());
        assertId(path + ".properties['leftCount'].classifierGenericType.typeArguments[1]", leftCount._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".properties['leftCount'].genericType", leftCount._genericType());

        Property<?, ?> rightCount = findProperty(bothSides, "rightCount");
        assertId(path + ".properties['rightCount']", rightCount);
        assertId(path + ".properties['rightCount'].classifierGenericType", rightCount._classifierGenericType());
        assertId(path + ".properties['rightCount'].classifierGenericType.typeArguments[1]", rightCount._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".properties['rightCount'].genericType", rightCount._genericType());
    }

    @Test
    public void testClassWithAnnotations()
    {
        String path = "test::model::ClassWithAnnotations";
        Class<?> classWithAnnotations = getCoreInstance(path);
        assertId(path, classWithAnnotations);
        assertId(path + ".taggedValues[0]", classWithAnnotations._taggedValues().getOnly());

        ListIterable<? extends Property<?, ?>> properties = toList(classWithAnnotations._properties());
        assertId(path + ".properties['deprecated']", properties.get(0));
        assertId(path + ".properties['deprecated'].classifierGenericType", properties.get(0)._classifierGenericType());
        assertId(path + ".properties['deprecated'].classifierGenericType.typeArguments[1]", properties.get(0)._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".properties['deprecated'].genericType", properties.get(0)._genericType());
        assertId(path + ".properties['alsoDeprecated']", properties.get(1));
        assertId(path + ".properties['alsoDeprecated'].classifierGenericType", properties.get(1)._classifierGenericType());
        assertId(path + ".properties['alsoDeprecated'].classifierGenericType.typeArguments[1]", properties.get(1)._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".properties['alsoDeprecated'].genericType", properties.get(1)._genericType());
        assertId(path + ".properties['alsoDeprecated'].taggedValues[0]", properties.get(1)._taggedValues().getOnly());
        assertId(path + ".properties['date']", properties.get(2));
        assertId(path + ".properties['date'].classifierGenericType", properties.get(2)._classifierGenericType());
        assertId(path + ".properties['date'].classifierGenericType.typeArguments[1]", properties.get(2)._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".properties['date'].genericType", properties.get(2)._genericType());
        assertId(path + ".properties['date'].taggedValues[0]", properties.get(2)._taggedValues().getFirst());
        assertId(path + ".properties['date'].taggedValues[1]", properties.get(2)._taggedValues().getLast());
    }

    @Test
    public void testClassWithTypeAndMultiplicityParameters()
    {
        String path = "test::model::ClassWithTypeAndMultParams";
        Class<?> classWithTypeMultParams = getCoreInstance(path);
        assertId(path, classWithTypeMultParams);

        ListIterable<? extends Property<?, ?>> properties = toList(classWithTypeMultParams._properties());
        assertId(path + ".properties['propT']", properties.get(0));
        assertId(path + ".properties['propT'].classifierGenericType", properties.get(0)._classifierGenericType());
        assertId(path + ".properties['propT'].classifierGenericType.typeArguments[1]", properties.get(0)._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".properties['propT'].genericType", properties.get(0)._genericType());
        assertId(path + ".properties['propT'].multiplicity", properties.get(0)._multiplicity());
        assertId(path + ".properties['propV']", properties.get(1));
        assertId(path + ".properties['propV'].classifierGenericType", properties.get(1)._classifierGenericType());
        assertId(path + ".properties['propV'].classifierGenericType.typeArguments[1]", properties.get(1)._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".properties['propV'].genericType", properties.get(1)._genericType());
        assertId(path + ".properties['propV'].multiplicity", properties.get(1)._multiplicity());
    }

    @Test
    public void testClassWithQualifiedProperties()
    {
        String path = "test::model::ClassWithQualifiedProperties";
        Class<?> classWithQualifiedProps = getCoreInstance(path);
        assertId(path, classWithQualifiedProps);

        Property<?, ?> names = classWithQualifiedProps._properties().getFirst();
        assertId(path + ".properties['names']", names);
        assertId(path + ".properties['names'].classifierGenericType", names._classifierGenericType());
        assertId(path + ".properties['names'].classifierGenericType.typeArguments[1]", names._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".properties['names'].genericType", names._genericType());

        Property<?, ?> title = classWithQualifiedProps._properties().getLast();
        assertId(path + ".properties['title']", title);
        assertId(path + ".properties['title'].classifierGenericType", title._classifierGenericType());
        assertId(path + ".properties['title'].classifierGenericType.typeArguments[1]", title._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".properties['title'].genericType", title._genericType());


        ListIterable<? extends QualifiedProperty<?>> qualifiedProperties = toList(classWithQualifiedProps._qualifiedProperties());

        QualifiedProperty<?> firstName = qualifiedProperties.get(0);
        SimpleFunctionExpression firstNameIfExp = (SimpleFunctionExpression) firstName._expressionSequence().getOnly();
        ListIterable<? extends ValueSpecification> firstNameIfParams = toList(firstNameIfExp._parametersValues());
        SimpleFunctionExpression firstNameIfCond = (SimpleFunctionExpression) firstNameIfParams.get(0);
        InstanceValue firstNameIfTrue = (InstanceValue) firstNameIfParams.get(1);
        LambdaFunction<?> firstNameIfTrueLambda = (LambdaFunction<?>) firstNameIfTrue._values().getOnly();
        InstanceValue firstNameIfFalse = (InstanceValue) firstNameIfParams.get(2);
        LambdaFunction<?> firstNameIfFalseLambda = (LambdaFunction<?>) firstNameIfFalse._values().getOnly();
        assertId(path + ".qualifiedProperties[id='firstName()']", firstName);
        assertId(path + ".qualifiedProperties[id='firstName()'].classifierGenericType.typeArguments[0].rawType", firstName._classifierGenericType()._typeArguments().getOnly()._rawType());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0]", firstName._expressionSequence().getOnly());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].genericType", firstName._expressionSequence().getOnly()._genericType());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[0]", firstNameIfParams.get(0));
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[0].genericType", firstNameIfParams.get(0)._genericType());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[0].parametersValues[0]", firstNameIfCond._parametersValues().getOnly());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[0].parametersValues[0].genericType", firstNameIfCond._parametersValues().getOnly()._genericType());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[0].parametersValues[0].parametersValues[0]", ((SimpleFunctionExpression) firstNameIfCond._parametersValues().getOnly())._parametersValues().getOnly());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[0].parametersValues[0].parametersValues[0].genericType", ((SimpleFunctionExpression) firstNameIfCond._parametersValues().getOnly())._parametersValues().getOnly()._genericType());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[0].parametersValues[0].propertyName", ((SimpleFunctionExpression) firstNameIfCond._parametersValues().getOnly())._propertyName());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[1]", firstNameIfParams.get(1));
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[1].genericType", firstNameIfParams.get(1)._genericType());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[1].genericType.typeArguments[0]", firstNameIfParams.get(1)._genericType()._typeArguments().getOnly());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[1].genericType.typeArguments[0].rawType", firstNameIfParams.get(1)._genericType()._typeArguments().getOnly()._rawType());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[1].genericType.typeArguments[0].rawType.returnType", ((FunctionType) firstNameIfParams.get(1)._genericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[1].values[0]", firstNameIfTrueLambda);
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType", firstNameIfTrueLambda._classifierGenericType()._typeArguments().getOnly()._rawType());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.returnType", ((FunctionType) firstNameIfTrueLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0]", firstNameIfTrueLambda._expressionSequence().getOnly());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[1].values[0].expressionSequence[0].genericType", firstNameIfTrueLambda._expressionSequence().getOnly()._genericType());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2]", firstNameIfFalse);
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].genericType", firstNameIfFalse._genericType());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].genericType.typeArguments[0]", firstNameIfFalse._genericType()._typeArguments().getOnly());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].genericType.typeArguments[0].rawType", firstNameIfFalse._genericType()._typeArguments().getOnly()._rawType());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].genericType.typeArguments[0].rawType.returnType", ((FunctionType) firstNameIfFalse._genericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0]", firstNameIfFalseLambda);
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType", firstNameIfFalseLambda._classifierGenericType()._typeArguments().getOnly()._rawType());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType.returnType", ((FunctionType) firstNameIfFalseLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].expressionSequence[0]", firstNameIfFalseLambda._expressionSequence().getOnly());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].expressionSequence[0].genericType", firstNameIfFalseLambda._expressionSequence().getOnly()._genericType());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].expressionSequence[0].parametersValues[0]", ((SimpleFunctionExpression) firstNameIfFalseLambda._expressionSequence().getOnly())._parametersValues().getFirst());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].expressionSequence[0].parametersValues[0].genericType", ((SimpleFunctionExpression) firstNameIfFalseLambda._expressionSequence().getOnly())._parametersValues().getFirst()._genericType());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].expressionSequence[0].parametersValues[0].parametersValues[0]", ((SimpleFunctionExpression) ((SimpleFunctionExpression) firstNameIfFalseLambda._expressionSequence().getOnly())._parametersValues().getFirst())._parametersValues().getOnly());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].expressionSequence[0].parametersValues[0].parametersValues[0].genericType", ((SimpleFunctionExpression) ((SimpleFunctionExpression) firstNameIfFalseLambda._expressionSequence().getOnly())._parametersValues().getFirst())._parametersValues().getOnly()._genericType());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].expressionSequence[0].parametersValues[0].propertyName", ((SimpleFunctionExpression) ((SimpleFunctionExpression) firstNameIfFalseLambda._expressionSequence().getOnly())._parametersValues().getFirst())._propertyName());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].expressionSequence[0].parametersValues[1]", ((SimpleFunctionExpression) firstNameIfFalseLambda._expressionSequence().getOnly())._parametersValues().getLast());
        assertId(path + ".qualifiedProperties[id='firstName()'].expressionSequence[0].parametersValues[2].values[0].expressionSequence[0].parametersValues[1].genericType", ((SimpleFunctionExpression) firstNameIfFalseLambda._expressionSequence().getOnly())._parametersValues().getLast()._genericType());
        assertId(path + ".qualifiedProperties[id='firstName()'].genericType", firstName._genericType());

        QualifiedProperty<?> fullNameNoTitle = qualifiedProperties.get(1);
        SimpleFunctionExpression fullNameExpression = (SimpleFunctionExpression) fullNameNoTitle._expressionSequence().getOnly();
        assertId(path + ".qualifiedProperties[id='fullName()']", fullNameNoTitle);
        assertId(path + ".qualifiedProperties[id='fullName()'].classifierGenericType.typeArguments[0].rawType", fullNameNoTitle._classifierGenericType()._typeArguments().getOnly()._rawType());
        assertId(path + ".qualifiedProperties[id='fullName()'].expressionSequence[0]", fullNameNoTitle._expressionSequence().getOnly());
        assertId(path + ".qualifiedProperties[id='fullName()'].expressionSequence[0].genericType", fullNameNoTitle._expressionSequence().getOnly()._genericType());
        assertId(path + ".qualifiedProperties[id='fullName()'].expressionSequence[0].parametersValues[0]", fullNameExpression._parametersValues().getFirst());
        assertId(path + ".qualifiedProperties[id='fullName()'].expressionSequence[0].parametersValues[0].genericType", fullNameExpression._parametersValues().getFirst()._genericType());
        assertId(path + ".qualifiedProperties[id='fullName()'].expressionSequence[0].parametersValues[1]", fullNameExpression._parametersValues().getLast());
        assertId(path + ".qualifiedProperties[id='fullName()'].expressionSequence[0].parametersValues[1].genericType", fullNameExpression._parametersValues().getLast()._genericType());
        assertId(path + ".qualifiedProperties[id='fullName()'].expressionSequence[0].qualifiedPropertyName", fullNameExpression._qualifiedPropertyName());
        assertId(path + ".qualifiedProperties[id='fullName()'].genericType", fullNameNoTitle._genericType());

        QualifiedProperty<?> fullNameWithTitle = qualifiedProperties.get(2);
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])']", fullNameWithTitle);

        FunctionType fullNameWithTitleFunctionType = (FunctionType) fullNameWithTitle._classifierGenericType()._typeArguments().getOnly()._rawType();
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].classifierGenericType.typeArguments[0].rawType", fullNameWithTitleFunctionType);
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].classifierGenericType.typeArguments[0].rawType.parameters['withTitle']", fullNameWithTitleFunctionType._parameters().getLast());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].classifierGenericType.typeArguments[0].rawType.parameters['withTitle'].genericType", fullNameWithTitleFunctionType._parameters().getLast()._genericType());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].genericType", fullNameWithTitle._genericType());

        SimpleFunctionExpression fullNameWithTitleLetExp = (SimpleFunctionExpression) fullNameWithTitle._expressionSequence().getFirst();
        InstanceValue fullNameWithTitleLetVarExp = (InstanceValue) fullNameWithTitleLetExp._parametersValues().getFirst();
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0]", fullNameWithTitleLetExp);
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].genericType", fullNameWithTitleLetExp._genericType());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[0]", fullNameWithTitleLetVarExp);
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[0].genericType", fullNameWithTitleLetVarExp._genericType());

        SimpleFunctionExpression fullNameWithTitleLetValExp = (SimpleFunctionExpression) fullNameWithTitleLetExp._parametersValues().getLast();
        ListIterable<? extends ValueSpecification> fullNameWithTitleLetValIfParams = toList(fullNameWithTitleLetValExp._parametersValues());
        SimpleFunctionExpression fullNameWithTitleLetValIfCond = (SimpleFunctionExpression) fullNameWithTitleLetValIfParams.get(0);
        InstanceValue fullNameWithTitleLetValIfTrue = (InstanceValue) fullNameWithTitleLetValIfParams.get(1);
        LambdaFunction<?> fullNameWithTitleLetValIfTrueLambda = (LambdaFunction<?>) fullNameWithTitleLetValIfTrue._values().getOnly();
        InstanceValue fullNameWithTitleLetValIfFalse = (InstanceValue) fullNameWithTitleLetValIfParams.get(2);
        LambdaFunction<?> fullNameWithTitleLetValIfFalseLambda = (LambdaFunction<?>) fullNameWithTitleLetValIfFalse._values().getOnly();
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1]", fullNameWithTitleLetValExp);
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].genericType", fullNameWithTitleLetValExp._genericType());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0]", fullNameWithTitleLetValIfCond);
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].genericType", fullNameWithTitleLetValIfCond._genericType());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[0]", fullNameWithTitleLetValIfCond._parametersValues().getFirst());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[0].genericType", fullNameWithTitleLetValIfCond._parametersValues().getFirst()._genericType());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[1]", fullNameWithTitleLetValIfCond._parametersValues().getLast());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[1].genericType", fullNameWithTitleLetValIfCond._parametersValues().getLast()._genericType());
        assertId(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[1].parametersValues[0]",
                ((SimpleFunctionExpression) fullNameWithTitleLetValIfCond._parametersValues().getLast())._parametersValues().getOnly());
        assertId(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[1].parametersValues[0].genericType",
                ((SimpleFunctionExpression) fullNameWithTitleLetValIfCond._parametersValues().getLast())._parametersValues().getOnly()._genericType());
        assertId(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[1].parametersValues[0].parametersValues[0]",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) fullNameWithTitleLetValIfCond._parametersValues().getLast())._parametersValues().getOnly())._parametersValues().getOnly());
        assertId(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[1].parametersValues[0].parametersValues[0].genericType",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) fullNameWithTitleLetValIfCond._parametersValues().getLast())._parametersValues().getOnly())._parametersValues().getOnly()._genericType());
        assertId(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[1].parametersValues[0].parametersValues[0].parametersValues[0]",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) ((SimpleFunctionExpression) fullNameWithTitleLetValIfCond._parametersValues().getLast())._parametersValues().getOnly())._parametersValues().getOnly())._parametersValues().getOnly());
        assertId(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[1].parametersValues[0].parametersValues[0].parametersValues[0].genericType",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) ((SimpleFunctionExpression) fullNameWithTitleLetValIfCond._parametersValues().getLast())._parametersValues().getOnly())._parametersValues().getOnly())._parametersValues().getOnly()._genericType());
        assertId(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[1].parametersValues[0].parametersValues[0].propertyName",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) ((SimpleFunctionExpression) fullNameWithTitleLetValIfCond._parametersValues().getLast())._parametersValues().getOnly())._parametersValues().getOnly())._propertyName());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1]", fullNameWithTitleLetValIfTrue);
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].genericType", fullNameWithTitleLetValIfTrue._genericType());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].genericType.typeArguments[0]", fullNameWithTitleLetValIfTrue._genericType()._typeArguments().getOnly());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].genericType.typeArguments[0].rawType", fullNameWithTitleLetValIfTrue._genericType()._typeArguments().getOnly()._rawType());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].genericType.typeArguments[0].rawType.returnType", ((FunctionType) fullNameWithTitleLetValIfTrue._genericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0]", fullNameWithTitleLetValIfTrueLambda);
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType", fullNameWithTitleLetValIfTrueLambda._classifierGenericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.returnType",
                ((FunctionType) fullNameWithTitleLetValIfTrueLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0]", fullNameWithTitleLetValIfTrueLambda._expressionSequence().getOnly());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].genericType", fullNameWithTitleLetValIfTrueLambda._expressionSequence().getOnly()._genericType());
        assertId(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].genericType",
                ((SimpleFunctionExpression) fullNameWithTitleLetValIfTrueLambda._expressionSequence().getOnly())._parametersValues().getOnly()._genericType());
        assertId(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].values[0]",
                (SimpleFunctionExpression) ((InstanceValue) ((SimpleFunctionExpression) fullNameWithTitleLetValIfTrueLambda._expressionSequence().getOnly())._parametersValues().getOnly())._values().getFirst());
        assertId(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].values[0].genericType",
                ((SimpleFunctionExpression) ((InstanceValue) ((SimpleFunctionExpression) fullNameWithTitleLetValIfTrueLambda._expressionSequence().getOnly())._parametersValues().getOnly())._values().getFirst())._genericType());
        assertId(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].values[0].parametersValues[0]",
                ((SimpleFunctionExpression) ((InstanceValue) ((SimpleFunctionExpression) fullNameWithTitleLetValIfTrueLambda._expressionSequence().getOnly())._parametersValues().getOnly())._values().getFirst())._parametersValues().getOnly());
        assertId(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].values[0].parametersValues[0].genericType",
                ((SimpleFunctionExpression) ((InstanceValue) ((SimpleFunctionExpression) fullNameWithTitleLetValIfTrueLambda._expressionSequence().getOnly())._parametersValues().getOnly())._values().getFirst())._parametersValues().getOnly()._genericType());
        assertId(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].values[0].parametersValues[0].parametersValues[0]",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) ((InstanceValue) ((SimpleFunctionExpression) fullNameWithTitleLetValIfTrueLambda._expressionSequence().getOnly())._parametersValues().getOnly())._values().getFirst())._parametersValues().getOnly())._parametersValues().getOnly());
        assertId(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].values[0].parametersValues[0].parametersValues[0].genericType",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) ((InstanceValue) ((SimpleFunctionExpression) fullNameWithTitleLetValIfTrueLambda._expressionSequence().getOnly())._parametersValues().getOnly())._values().getFirst())._parametersValues().getOnly())._parametersValues().getOnly()._genericType());
        assertId(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].values[0].parametersValues[0].propertyName",
                ((SimpleFunctionExpression) ((SimpleFunctionExpression) ((InstanceValue) ((SimpleFunctionExpression) fullNameWithTitleLetValIfTrueLambda._expressionSequence().getOnly())._parametersValues().getOnly())._values().getFirst())._parametersValues().getOnly())._propertyName());
        assertId(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].values[1]",
                (InstanceValue) ((InstanceValue) ((SimpleFunctionExpression) fullNameWithTitleLetValIfTrueLambda._expressionSequence().getOnly())._parametersValues().getOnly())._values().getLast());
        assertId(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].values[1].genericType",
                ((InstanceValue) ((InstanceValue) ((SimpleFunctionExpression) fullNameWithTitleLetValIfTrueLambda._expressionSequence().getOnly())._parametersValues().getOnly())._values().getLast())._genericType());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[2]", fullNameWithTitleLetValIfFalse);
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[2].genericType", fullNameWithTitleLetValIfFalse._genericType());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[2].genericType.typeArguments[0]", fullNameWithTitleLetValIfFalse._genericType()._typeArguments().getOnly());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[2].genericType.typeArguments[0].rawType", fullNameWithTitleLetValIfFalse._genericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[2].genericType.typeArguments[0].rawType.returnType",
                ((FunctionType) fullNameWithTitleLetValIfFalse._genericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[2].values[0]", fullNameWithTitleLetValIfFalseLambda);
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType", fullNameWithTitleLetValIfFalseLambda._classifierGenericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType.returnType",
                ((FunctionType) fullNameWithTitleLetValIfFalseLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0]", fullNameWithTitleLetValIfFalseLambda._expressionSequence().getOnly());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].genericType", fullNameWithTitleLetValIfFalseLambda._expressionSequence().getOnly()._genericType());

        SimpleFunctionExpression fullNameWithTitleJoinStrExp = (SimpleFunctionExpression) fullNameWithTitle._expressionSequence().getLast();
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1]", fullNameWithTitleJoinStrExp);
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1].genericType", fullNameWithTitleJoinStrExp._genericType());
        ListIterable<? extends ValueSpecification> fullNameWithTitleJoinStrExpParams = toList(fullNameWithTitleJoinStrExp._parametersValues());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1].parametersValues[0]", fullNameWithTitleJoinStrExpParams.get(0));
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1].parametersValues[0].genericType", fullNameWithTitleJoinStrExpParams.get(0)._genericType());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1].parametersValues[0].parametersValues[0]", ((SimpleFunctionExpression) fullNameWithTitleJoinStrExpParams.get(0))._parametersValues().getOnly());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1].parametersValues[0].parametersValues[0].genericType", ((SimpleFunctionExpression) fullNameWithTitleJoinStrExpParams.get(0))._parametersValues().getOnly()._genericType());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1].parametersValues[0].propertyName", ((SimpleFunctionExpression) fullNameWithTitleJoinStrExpParams.get(0))._propertyName());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1].parametersValues[1]", fullNameWithTitleJoinStrExpParams.get(1));
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1].parametersValues[1].genericType", fullNameWithTitleJoinStrExpParams.get(1)._genericType());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1].parametersValues[2]", fullNameWithTitleJoinStrExpParams.get(2));
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1].parametersValues[2].genericType", fullNameWithTitleJoinStrExpParams.get(2)._genericType());
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1].parametersValues[3]", fullNameWithTitleJoinStrExpParams.get(3));
        assertId(path + ".qualifiedProperties[id='fullName(Boolean[1])'].expressionSequence[1].parametersValues[3].genericType", fullNameWithTitleJoinStrExpParams.get(3)._genericType());
    }

    @Test
    public void testClassWithMilestoning1()
    {
        String path = "test::model::ClassWithMilestoning1";
        Class<?> classWithMilestoning1 = getCoreInstance(path);
        assertId(path, classWithMilestoning1);

        ListIterable<? extends Property<?, ?>> originalMilestonedProperties = toList(classWithMilestoning1._originalMilestonedProperties());
        Property<?, ?> toClass2Original = originalMilestonedProperties.get(0);
        assertId(path + ".originalMilestonedProperties['toClass2']", toClass2Original);
        assertId(path + ".originalMilestonedProperties['toClass2'].classifierGenericType", toClass2Original._classifierGenericType());
        assertId(path + ".originalMilestonedProperties['toClass2'].classifierGenericType.typeArguments[1]", toClass2Original._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".originalMilestonedProperties['toClass2'].genericType", toClass2Original._genericType());

        Property<?, ?> toClass3Original = originalMilestonedProperties.get(1);
        assertId(path + ".originalMilestonedProperties['toClass3']", toClass3Original);
        assertId(path + ".originalMilestonedProperties['toClass3'].classifierGenericType", toClass3Original._classifierGenericType());
        assertId(path + ".originalMilestonedProperties['toClass3'].classifierGenericType.typeArguments[1]", toClass3Original._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".originalMilestonedProperties['toClass3'].genericType", toClass3Original._genericType());

        ListIterable<? extends Property<?, ?>> properties = toList(classWithMilestoning1._properties());
        assertId(path + ".properties['businessDate']", properties.get(0));
        assertId(path + ".properties['milestoning']", properties.get(1));

        Property<?, ?> toClass2AllVersions = properties.get(2);
        assertId(path + ".properties['toClass2AllVersions']", toClass2AllVersions);
        assertId(path + ".properties['toClass2AllVersions'].genericType", toClass2AllVersions._genericType());

        Property<?, ?> toClass3AllVersions = properties.get(3);
        assertId(path + ".properties['toClass3AllVersions']", toClass3AllVersions);
        assertId(path + ".properties['toClass3AllVersions'].genericType", toClass3AllVersions._genericType());

        ListIterable<? extends QualifiedProperty<?>> qualifiedProperties = toList(classWithMilestoning1._qualifiedProperties());
        QualifiedProperty<?> toClass2 = qualifiedProperties.get(0);
        assertId(path + ".qualifiedProperties[id='toClass2(Date[1])']", toClass2);
        assertId(path + ".qualifiedProperties[id='toClass2(Date[1])'].genericType", toClass2._genericType());

        QualifiedProperty<?> toClass2AllVersionsInRange = qualifiedProperties.get(1);
        assertId(path + ".qualifiedProperties[id='toClass2AllVersionsInRange(Date[1],Date[1])']", toClass2AllVersionsInRange);
        assertId(path + ".qualifiedProperties[id='toClass2AllVersionsInRange(Date[1],Date[1])'].genericType", toClass2AllVersionsInRange._genericType());

        QualifiedProperty<?> toClass3_date_date = qualifiedProperties.get(2);
        assertId(path + ".qualifiedProperties[id='toClass3(Date[1],Date[1])']", toClass3_date_date);
        assertId(path + ".qualifiedProperties[id='toClass3(Date[1],Date[1])'].genericType", toClass3_date_date._genericType());

        QualifiedProperty<?> toClass3_date = qualifiedProperties.get(3);
        assertId(path + ".qualifiedProperties[id='toClass3(Date[1])']", toClass3_date);
        assertId(path + ".qualifiedProperties[id='toClass3(Date[1])'].genericType", toClass3_date._genericType());
    }

    @Test
    public void testClassWithMilestoning2()
    {
        String path = "test::model::ClassWithMilestoning2";
        Class<?> classWithMilestoning2 = getCoreInstance(path);
        assertId(path, classWithMilestoning2);

        ListIterable<? extends Property<?, ?>> originalMilestonedProperties = toList(classWithMilestoning2._originalMilestonedProperties());
        Property<?, ?> toClass1Original = originalMilestonedProperties.get(0);
        assertId(path + ".originalMilestonedProperties['toClass1']", toClass1Original);
        assertId(path + ".originalMilestonedProperties['toClass1'].classifierGenericType", toClass1Original._classifierGenericType());
        assertId(path + ".originalMilestonedProperties['toClass1'].classifierGenericType.typeArguments[1]", toClass1Original._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".originalMilestonedProperties['toClass1'].genericType", toClass1Original._genericType());

        Property<?, ?> toClass3Original = originalMilestonedProperties.get(1);
        assertId(path + ".originalMilestonedProperties['toClass3']", toClass3Original);
        assertId(path + ".originalMilestonedProperties['toClass3'].classifierGenericType", toClass3Original._classifierGenericType());
        assertId(path + ".originalMilestonedProperties['toClass3'].classifierGenericType.typeArguments[1]", toClass3Original._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".originalMilestonedProperties['toClass3'].genericType", toClass3Original._genericType());

        ListIterable<? extends Property<?, ?>> properties = toList(classWithMilestoning2._properties());
        assertId(path + ".properties['processingDate']", properties.get(0));

        assertId(path + ".properties['milestoning']", properties.get(1));

        Property<?, ?> toClass1AllVersions = properties.get(2);
        assertId(path + ".properties['toClass1AllVersions']", toClass1AllVersions);
        assertId(path + ".properties['toClass1AllVersions'].genericType", toClass1AllVersions._genericType());

        Property<?, ?> toClass3AllVersions = properties.get(3);
        assertId(path + ".properties['toClass3AllVersions']", toClass3AllVersions);
        assertId(path + ".properties['toClass3AllVersions'].genericType", toClass3AllVersions._genericType());

        ListIterable<? extends QualifiedProperty<?>> qualifiedProperties = toList(classWithMilestoning2._qualifiedProperties());
        QualifiedProperty<?> toClass1 = qualifiedProperties.get(0);
        assertId(path + ".qualifiedProperties[id='toClass1(Date[1])']", toClass1);
        assertId(path + ".qualifiedProperties[id='toClass1(Date[1])'].genericType", toClass1._genericType());

        QualifiedProperty<?> toClass1AllVersionsInRange = qualifiedProperties.get(1);
        assertId(path + ".qualifiedProperties[id='toClass1AllVersionsInRange(Date[1],Date[1])']", toClass1AllVersionsInRange);
        assertId(path + ".qualifiedProperties[id='toClass1AllVersionsInRange(Date[1],Date[1])'].genericType", toClass1AllVersionsInRange._genericType());

        QualifiedProperty<?> toClass3_date_date = qualifiedProperties.get(2);
        assertId(path + ".qualifiedProperties[id='toClass3(Date[1],Date[1])']", toClass3_date_date);
        assertId(path + ".qualifiedProperties[id='toClass3(Date[1],Date[1])'].genericType", toClass3_date_date._genericType());

        QualifiedProperty<?> toClass3_date = qualifiedProperties.get(3);
        assertId(path + ".qualifiedProperties[id='toClass3(Date[1])']", toClass3_date);
        assertId(path + ".qualifiedProperties[id='toClass3(Date[1])'].genericType", toClass3_date._genericType());
    }

    @Test
    public void testClassWithMilestoning3()
    {
        String path = "test::model::ClassWithMilestoning3";
        Class<?> classWithMilestoning3 = getCoreInstance(path);
        assertId(path, classWithMilestoning3);

        ListIterable<? extends Property<?, ?>> originalMilestonedProperties = toList(classWithMilestoning3._originalMilestonedProperties());
        Property<?, ?> toClass1Original = originalMilestonedProperties.get(0);
        assertId(path + ".originalMilestonedProperties['toClass1']", toClass1Original);
        assertId(path + ".originalMilestonedProperties['toClass1'].classifierGenericType", toClass1Original._classifierGenericType());
        assertId(path + ".originalMilestonedProperties['toClass1'].classifierGenericType.typeArguments[1]", toClass1Original._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".originalMilestonedProperties['toClass1'].genericType", toClass1Original._genericType());

        Property<?, ?> toClass2Original = originalMilestonedProperties.get(1);
        assertId(path + ".originalMilestonedProperties['toClass2']", toClass2Original);
        assertId(path + ".originalMilestonedProperties['toClass2'].classifierGenericType", toClass2Original._classifierGenericType());
        assertId(path + ".originalMilestonedProperties['toClass2'].classifierGenericType.typeArguments[1]", toClass2Original._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".originalMilestonedProperties['toClass2'].genericType", toClass2Original._genericType());

        ListIterable<? extends Property<?, ?>> properties = toList(classWithMilestoning3._properties());
        assertId(path + ".properties['processingDate']", properties.get(0));
        assertId(path + ".properties['businessDate']", properties.get(1));
        assertId(path + ".properties['milestoning']", properties.get(2));

        Property<?, ?> toClass1AllVersions = properties.get(3);
        assertId(path + ".properties['toClass1AllVersions']", toClass1AllVersions);
        assertId(path + ".properties['toClass1AllVersions'].genericType", toClass1AllVersions._genericType());

        Property<?, ?> toClass2AllVersions = properties.get(4);
        assertId(path + ".properties['toClass2AllVersions']", toClass2AllVersions);
        assertId(path + ".properties['toClass2AllVersions'].genericType", toClass2AllVersions._genericType());

        ListIterable<? extends QualifiedProperty<?>> qualifiedProperties = toList(classWithMilestoning3._qualifiedProperties());
        QualifiedProperty<?> toClass1_noDate = qualifiedProperties.get(0);
        assertId(path + ".qualifiedProperties[id='toClass1()']", toClass1_noDate);
        assertId(path + ".qualifiedProperties[id='toClass1()'].genericType", toClass1_noDate._genericType());

        QualifiedProperty<?> toClass1_date = qualifiedProperties.get(1);
        assertId(path + ".qualifiedProperties[id='toClass1(Date[1])']", toClass1_date);
        assertId(path + ".qualifiedProperties[id='toClass1(Date[1])'].genericType", toClass1_date._genericType());

        QualifiedProperty<?> toClass1AllVersionsInRange = qualifiedProperties.get(2);
        assertId(path + ".qualifiedProperties[id='toClass1AllVersionsInRange(Date[1],Date[1])']", toClass1AllVersionsInRange);
        assertId(path + ".qualifiedProperties[id='toClass1AllVersionsInRange(Date[1],Date[1])'].genericType", toClass1AllVersionsInRange._genericType());

        QualifiedProperty<?> toClass2_noDate = qualifiedProperties.get(3);
        assertId(path + ".qualifiedProperties[id='toClass2()']", toClass2_noDate);
        assertId(path + ".qualifiedProperties[id='toClass2()'].genericType", toClass2_noDate._genericType());

        QualifiedProperty<?> toClass2_date = qualifiedProperties.get(4);
        assertId(path + ".qualifiedProperties[id='toClass2(Date[1])']", toClass2_date);
        assertId(path + ".qualifiedProperties[id='toClass2(Date[1])'].genericType", toClass2_date._genericType());

        QualifiedProperty<?> toClass2AllVersionsInRange = qualifiedProperties.get(5);
        assertId(path + ".qualifiedProperties[id='toClass2AllVersionsInRange(Date[1],Date[1])']", toClass2AllVersionsInRange);
        assertId(path + ".qualifiedProperties[id='toClass2AllVersionsInRange(Date[1],Date[1])'].genericType", toClass2AllVersionsInRange._genericType());
    }

    @Test
    public void testAssociationWithMilestoning1()
    {
        String path = "test::model::AssociationWithMilestoning1";
        Association association = getCoreInstance(path);
        assertId(path, association);

        ListIterable<? extends Property<?, ?>> originalMilestonedProperties = toList(association._originalMilestonedProperties());
        Property<?, ?> toClass1AOriginal = originalMilestonedProperties.get(0);
        assertId(path + ".originalMilestonedProperties['toClass1A']", toClass1AOriginal);
        assertId(path + ".originalMilestonedProperties['toClass1A'].classifierGenericType", toClass1AOriginal._classifierGenericType());
        assertId(path + ".originalMilestonedProperties['toClass1A'].classifierGenericType.typeArguments[1]", toClass1AOriginal._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".originalMilestonedProperties['toClass1A'].genericType", toClass1AOriginal._genericType());

        Property<?, ?> toClass2AOriginal = originalMilestonedProperties.get(1);
        assertId(path + ".originalMilestonedProperties['toClass2A']", toClass2AOriginal);
        assertId(path + ".originalMilestonedProperties['toClass2A'].classifierGenericType", toClass2AOriginal._classifierGenericType());
        assertId(path + ".originalMilestonedProperties['toClass2A'].classifierGenericType.typeArguments[1]", toClass2AOriginal._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".originalMilestonedProperties['toClass2A'].genericType", toClass2AOriginal._genericType());

        ListIterable<? extends Property<?, ?>> properties = toList(association._properties());
        Property<?, ?> toClass1AAllVersions = properties.get(0);
        assertId(path + ".properties['toClass1AAllVersions']", toClass1AAllVersions);
        assertId(path + ".properties['toClass1AAllVersions'].classifierGenericType.typeArguments[0]", toClass1AAllVersions._classifierGenericType()._typeArguments().getFirst());
        assertId(path + ".properties['toClass1AAllVersions'].genericType", toClass1AAllVersions._genericType());

        Property<?, ?> toClass2AAllVersions = properties.get(1);
        assertId(path + ".properties['toClass2AAllVersions']", toClass2AAllVersions);
        assertId(path + ".properties['toClass2AAllVersions'].classifierGenericType.typeArguments[0]", toClass2AAllVersions._classifierGenericType()._typeArguments().getFirst());
        assertId(path + ".properties['toClass2AAllVersions'].genericType", toClass2AAllVersions._genericType());

        ListIterable<? extends QualifiedProperty<?>> qualifiedProperties = toList(association._qualifiedProperties());
        QualifiedProperty<?> toClass1A = qualifiedProperties.get(0);
        assertId(path + ".qualifiedProperties[id='toClass1A(Date[1])']", toClass1A);
        assertId(path + ".qualifiedProperties[id='toClass1A(Date[1])'].genericType", toClass1A._genericType());

        QualifiedProperty<?> toClass1AAllVersionsInRange = qualifiedProperties.get(1);
        assertId(path + ".qualifiedProperties[id='toClass1AAllVersionsInRange(Date[1],Date[1])']", toClass1AAllVersionsInRange);
        assertId(path + ".qualifiedProperties[id='toClass1AAllVersionsInRange(Date[1],Date[1])'].genericType", toClass1AAllVersionsInRange._genericType());

        QualifiedProperty<?> toClass2A = qualifiedProperties.get(2);
        assertId(path + ".qualifiedProperties[id='toClass2A(Date[1])']", toClass2A);
        assertId(path + ".qualifiedProperties[id='toClass2A(Date[1])'].genericType", toClass2A._genericType());

        QualifiedProperty<?> toClass2AAllVersionsInRange = qualifiedProperties.get(3);
        assertId(path + ".qualifiedProperties[id='toClass2AAllVersionsInRange(Date[1],Date[1])']", toClass2AAllVersionsInRange);
        assertId(path + ".qualifiedProperties[id='toClass2AAllVersionsInRange(Date[1],Date[1])'].genericType", toClass2AAllVersionsInRange._genericType());
    }

    @Test
    public void testAssociationWithMilestoning2()
    {
        String path = "test::model::AssociationWithMilestoning2";
        Association association = getCoreInstance(path);
        assertId(path, association);

        ListIterable<? extends Property<?, ?>> originalMilestonedProperties = toList(association._originalMilestonedProperties());
        Property<?, ?> toClass1BOriginal = originalMilestonedProperties.get(0);
        assertId(path + ".originalMilestonedProperties['toClass1B']", toClass1BOriginal);
        assertId(path + ".originalMilestonedProperties['toClass1B'].classifierGenericType", toClass1BOriginal._classifierGenericType());
        assertId(path + ".originalMilestonedProperties['toClass1B'].classifierGenericType.typeArguments[1]", toClass1BOriginal._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".originalMilestonedProperties['toClass1B'].genericType", toClass1BOriginal._genericType());

        Property<?, ?> toClass3BOriginal = originalMilestonedProperties.get(1);
        assertId(path + ".originalMilestonedProperties['toClass3B']", toClass3BOriginal);
        assertId(path + ".originalMilestonedProperties['toClass3B'].classifierGenericType", toClass3BOriginal._classifierGenericType());
        assertId(path + ".originalMilestonedProperties['toClass3B'].classifierGenericType.typeArguments[1]", toClass3BOriginal._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".originalMilestonedProperties['toClass3B'].genericType", toClass3BOriginal._genericType());

        ListIterable<? extends Property<?, ?>> properties = toList(association._properties());
        Property<?, ?> toClass1BAllVersions = properties.get(0);
        assertId(path + ".properties['toClass1BAllVersions']", toClass1BAllVersions);
        assertId(path + ".properties['toClass1BAllVersions'].classifierGenericType.typeArguments[0]", toClass1BAllVersions._classifierGenericType()._typeArguments().getFirst());
        assertId(path + ".properties['toClass1BAllVersions'].genericType", toClass1BAllVersions._genericType());

        Property<?, ?> toClass3BAllVersions = properties.get(1);
        assertId(path + ".properties['toClass3BAllVersions']", toClass3BAllVersions);
        assertId(path + ".properties['toClass3BAllVersions'].classifierGenericType.typeArguments[0]", toClass3BAllVersions._classifierGenericType()._typeArguments().getFirst());
        assertId(path + ".properties['toClass3BAllVersions'].genericType", toClass3BAllVersions._genericType());

        ListIterable<? extends QualifiedProperty<?>> qualifiedProperties = toList(association._qualifiedProperties());
        QualifiedProperty<?> toClass1B_noDate = qualifiedProperties.get(0);
        assertId(path + ".qualifiedProperties[id='toClass1B()']", toClass1B_noDate);
        assertId(path + ".qualifiedProperties[id='toClass1B()'].genericType", toClass1B_noDate._genericType());

        QualifiedProperty<?> toClass1B_date = qualifiedProperties.get(1);
        assertId(path + ".qualifiedProperties[id='toClass1B(Date[1])']", toClass1B_date);
        assertId(path + ".qualifiedProperties[id='toClass1B(Date[1])'].genericType", toClass1B_date._genericType());

        QualifiedProperty<?> toClass1BAllVersionsInRange = qualifiedProperties.get(2);
        assertId(path + ".qualifiedProperties[id='toClass1BAllVersionsInRange(Date[1],Date[1])']", toClass1BAllVersionsInRange);
        assertId(path + ".qualifiedProperties[id='toClass1BAllVersionsInRange(Date[1],Date[1])'].genericType", toClass1BAllVersionsInRange._genericType());

        QualifiedProperty<?> toClass3B_date_date = qualifiedProperties.get(3);
        assertId(path + ".qualifiedProperties[id='toClass3B(Date[1],Date[1])']", toClass3B_date_date);
        assertId(path + ".qualifiedProperties[id='toClass3B(Date[1],Date[1])'].genericType", toClass3B_date_date._genericType());

        QualifiedProperty<?> toClass3B_date = qualifiedProperties.get(4);
        assertId(path + ".qualifiedProperties[id='toClass3B(Date[1])']", toClass3B_date);
        assertId(path + ".qualifiedProperties[id='toClass3B(Date[1])'].genericType", toClass3B_date._genericType());
    }

    @Test
    public void testAssociationWithMilestoning3()
    {
        String path = "test::model::AssociationWithMilestoning3";
        Association association = getCoreInstance(path);
        assertId(path, association);

        ListIterable<? extends Property<?, ?>> originalMilestonedProperties = toList(association._originalMilestonedProperties());
        Property<?, ?> toClass2COriginal = originalMilestonedProperties.get(0);
        assertId(path + ".originalMilestonedProperties['toClass2C']", toClass2COriginal);
        assertId(path + ".originalMilestonedProperties['toClass2C'].classifierGenericType", toClass2COriginal._classifierGenericType());
        assertId(path + ".originalMilestonedProperties['toClass2C'].classifierGenericType.typeArguments[1]", toClass2COriginal._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".originalMilestonedProperties['toClass2C'].genericType", toClass2COriginal._genericType());

        Property<?, ?> toClass3COriginal = originalMilestonedProperties.get(1);
        assertId(path + ".originalMilestonedProperties['toClass3C']", toClass3COriginal);
        assertId(path + ".originalMilestonedProperties['toClass3C'].classifierGenericType", toClass3COriginal._classifierGenericType());
        assertId(path + ".originalMilestonedProperties['toClass3C'].classifierGenericType.typeArguments[1]", toClass3COriginal._classifierGenericType()._typeArguments().getLast());
        assertId(path + ".originalMilestonedProperties['toClass3C'].genericType", toClass3COriginal._genericType());

        ListIterable<? extends Property<?, ?>> properties = toList(association._properties());
        Property<?, ?> toClass2CAllVersions = properties.get(0);
        assertId(path + ".properties['toClass2CAllVersions']", toClass2CAllVersions);
        assertId(path + ".properties['toClass2CAllVersions'].classifierGenericType.typeArguments[0]", toClass2CAllVersions._classifierGenericType()._typeArguments().getFirst());
        assertId(path + ".properties['toClass2CAllVersions'].genericType", toClass2CAllVersions._genericType());

        Property<?, ?> toClass3CAllVersions = properties.get(1);
        assertId(path + ".properties['toClass3CAllVersions']", toClass3CAllVersions);
        assertId(path + ".properties['toClass3CAllVersions'].classifierGenericType.typeArguments[0]", toClass3CAllVersions._classifierGenericType()._typeArguments().getFirst());
        assertId(path + ".properties['toClass3CAllVersions'].genericType", toClass3CAllVersions._genericType());

        ListIterable<? extends QualifiedProperty<?>> qualifiedProperties = toList(association._qualifiedProperties());
        QualifiedProperty<?> toClass2C_noDate = qualifiedProperties.get(0);
        assertId(path + ".qualifiedProperties[id='toClass2C()']", toClass2C_noDate);
        assertId(path + ".qualifiedProperties[id='toClass2C()'].genericType", toClass2C_noDate._genericType());

        QualifiedProperty<?> toClass2C_date = qualifiedProperties.get(1);
        assertId(path + ".qualifiedProperties[id='toClass2C(Date[1])']", toClass2C_date);
        assertId(path + ".qualifiedProperties[id='toClass2C(Date[1])'].genericType", toClass2C_date._genericType());

        QualifiedProperty<?> toClass2CAllVersionsInRange = qualifiedProperties.get(2);
        assertId(path + ".qualifiedProperties[id='toClass2CAllVersionsInRange(Date[1],Date[1])']", toClass2CAllVersionsInRange);
        assertId(path + ".qualifiedProperties[id='toClass2CAllVersionsInRange(Date[1],Date[1])'].genericType", toClass2CAllVersionsInRange._genericType());

        QualifiedProperty<?> toClass3C_date_date = qualifiedProperties.get(3);
        assertId(path + ".qualifiedProperties[id='toClass3C(Date[1],Date[1])']", toClass3C_date_date);
        assertId(path + ".qualifiedProperties[id='toClass3C(Date[1],Date[1])'].genericType", toClass3C_date_date._genericType());

        QualifiedProperty<?> toClass3C_date = qualifiedProperties.get(4);
        assertId(path + ".qualifiedProperties[id='toClass3C(Date[1])']", toClass3C_date);
        assertId(path + ".qualifiedProperties[id='toClass3C(Date[1])'].genericType", toClass3C_date._genericType());
    }

    @Test
    public void testNativeFunction()
    {
        String path = "meta::pure::functions::lang::compare_T_1__T_1__Integer_1_";
        NativeFunction<?> compare = getCoreInstance(path);
        assertId(path, compare);
        assertId(path + ".classifierGenericType.typeArguments[0].rawType", compare._classifierGenericType()._typeArguments().getOnly()._rawType());
        assertId(path + ".classifierGenericType.typeArguments[0].rawType.parameters['a']", ((FunctionType) compare._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getFirst());
        assertId(path + ".classifierGenericType.typeArguments[0].rawType.parameters['a'].genericType", ((FunctionType) compare._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getFirst()._genericType());
        assertId(path + ".classifierGenericType.typeArguments[0].rawType.parameters['b']", ((FunctionType) compare._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getLast());
        assertId(path + ".classifierGenericType.typeArguments[0].rawType.parameters['b'].genericType", ((FunctionType) compare._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getLast()._genericType());
        assertId(path + ".classifierGenericType.typeArguments[0].rawType.returnType", ((FunctionType) compare._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(path + ".taggedValues[0]", compare._taggedValues().getFirst());
        assertId(path + ".taggedValues[1]", compare._taggedValues().getLast());
    }

    @Test
    public void testFunction()
    {
        String path = "test::model::testFunc_T_m__Function_$0_1$__String_m_";
        ConcreteFunctionDefinition<?> testFunction = getCoreInstance(path);
        assertId(path, testFunction);

        FunctionType functionType = (FunctionType) testFunction._classifierGenericType()._typeArguments().getOnly()._rawType();
        assertId(path + ".classifierGenericType.typeArguments[0].rawType", functionType);
        assertId(path + ".classifierGenericType.typeArguments[0].rawType.parameters['col']", functionType._parameters().getFirst());
        assertId(path + ".classifierGenericType.typeArguments[0].rawType.parameters['col'].genericType", functionType._parameters().getFirst()._genericType());
        assertId(path + ".classifierGenericType.typeArguments[0].rawType.parameters['col'].multiplicity", functionType._parameters().getFirst()._multiplicity());
        assertId(path + ".classifierGenericType.typeArguments[0].rawType.parameters['func']", functionType._parameters().getLast());
        assertId(path + ".classifierGenericType.typeArguments[0].rawType.parameters['func'].genericType", functionType._parameters().getLast()._genericType());
        assertId(path + ".classifierGenericType.typeArguments[0].rawType.parameters['func'].genericType.typeArguments[0].rawType", functionType._parameters().getLast()._genericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".classifierGenericType.typeArguments[0].rawType.parameters['func'].genericType.typeArguments[0].rawType.parameters[''].genericType",
                ((FunctionType) functionType._parameters().getLast()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        assertId(
                path + ".classifierGenericType.typeArguments[0].rawType.parameters['func'].genericType.typeArguments[0].rawType.returnType",
                ((FunctionType) functionType._parameters().getLast()._genericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(path + ".classifierGenericType.typeArguments[0].rawType.returnMultiplicity", functionType._returnMultiplicity());
        assertId(path + ".classifierGenericType.typeArguments[0].rawType.returnType", functionType._returnType());

        SimpleFunctionExpression letExp = (SimpleFunctionExpression) testFunction._expressionSequence().getFirst();
        assertId(path + ".expressionSequence[0]", letExp);
        assertId(path + ".expressionSequence[0].genericType", letExp._genericType());
        assertId(path + ".expressionSequence[0].genericType.typeArguments[0]", letExp._genericType()._typeArguments().getOnly());
        assertId(path + ".expressionSequence[0].genericType.typeArguments[0].rawType", letExp._genericType()._typeArguments().getOnly()._rawType());
        assertId(path + ".expressionSequence[0].genericType.typeArguments[0].rawType.parameters['']", ((FunctionType) letExp._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        assertId(path + ".expressionSequence[0].genericType.typeArguments[0].rawType.parameters[''].genericType", ((FunctionType) letExp._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        assertId(path + ".expressionSequence[0].genericType.typeArguments[0].rawType.parameters[''].genericType.typeParameter", ((FunctionType) letExp._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        assertId(path + ".expressionSequence[0].genericType.typeArguments[0].rawType.returnType", ((FunctionType) letExp._genericType()._typeArguments().getOnly()._rawType())._returnType());

        InstanceValue letVar = (InstanceValue) letExp._parametersValues().getFirst();
        assertId(path + ".expressionSequence[0].parametersValues[0]", letVar);
        assertId(path + ".expressionSequence[0].parametersValues[0].genericType", letVar._genericType());

        SimpleFunctionExpression letValIfExp = (SimpleFunctionExpression) letExp._parametersValues().getLast();
        assertId(path + ".expressionSequence[0].parametersValues[1]", letValIfExp);
        assertId(path + ".expressionSequence[0].parametersValues[1].genericType", letValIfExp._genericType());
        assertId(path + ".expressionSequence[0].parametersValues[1].genericType.typeArguments[0]", letValIfExp._genericType()._typeArguments().getOnly());
        assertId(path + ".expressionSequence[0].parametersValues[1].genericType.typeArguments[0].rawType", letValIfExp._genericType()._typeArguments().getOnly()._rawType());
        assertId(path + ".expressionSequence[0].parametersValues[1].genericType.typeArguments[0].rawType.parameters['']", ((FunctionType) letValIfExp._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        assertId(path + ".expressionSequence[0].parametersValues[1].genericType.typeArguments[0].rawType.parameters[''].genericType", ((FunctionType) letValIfExp._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        assertId(path + ".expressionSequence[0].parametersValues[1].genericType.typeArguments[0].rawType.parameters[''].genericType.typeParameter", ((FunctionType) letValIfExp._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        assertId(path + ".expressionSequence[0].parametersValues[1].genericType.typeArguments[0].rawType.returnType", ((FunctionType) letValIfExp._genericType()._typeArguments().getOnly()._rawType())._returnType());

        ListIterable<? extends ValueSpecification> letValIfParams = toList(letValIfExp._parametersValues());
        SimpleFunctionExpression letValIfCond = (SimpleFunctionExpression) letValIfParams.get(0);
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[0]", letValIfCond);
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[0].genericType", letValIfCond._genericType());
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[0]", letValIfCond._parametersValues().getOnly());
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[0].genericType", letValIfCond._parametersValues().getOnly()._genericType());
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[0].genericType.typeArguments[0]", letValIfCond._parametersValues().getOnly()._genericType()._typeArguments().getOnly());
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[0].genericType.typeArguments[0].rawType", letValIfCond._parametersValues().getOnly()._genericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[0].genericType.typeArguments[0].rawType.parameters['']",
                ((FunctionType) letValIfCond._parametersValues().getOnly()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[0].genericType.typeArguments[0].rawType.parameters[''].genericType",
                ((FunctionType) letValIfCond._parametersValues().getOnly()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[0].genericType.typeArguments[0].rawType.parameters[''].genericType.typeParameter",
                ((FunctionType) letValIfCond._parametersValues().getOnly()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[0].parametersValues[0].genericType.typeArguments[0].rawType.returnType",
                ((FunctionType) letValIfCond._parametersValues().getOnly()._genericType()._typeArguments().getOnly()._rawType())._returnType());

        InstanceValue letValIfTrue = (InstanceValue) letValIfParams.get(1);
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[1]", letValIfTrue);
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].genericType", letValIfTrue._genericType());
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].genericType.typeArguments[0]", letValIfTrue._genericType()._typeArguments().getOnly());
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].genericType.typeArguments[0].rawType", letValIfTrue._genericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].genericType.typeArguments[0].rawType.returnType",
                ((FunctionType) letValIfTrue._genericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].genericType.typeArguments[0].rawType.returnType.typeArguments[0]",
                ((FunctionType) letValIfTrue._genericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].genericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType",
                ((FunctionType) letValIfTrue._genericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].genericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.parameters['x']",
                ((FunctionType) ((FunctionType) letValIfTrue._genericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].genericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.parameters['x'].genericType",
                ((FunctionType) ((FunctionType) letValIfTrue._genericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].genericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.parameters['x'].genericType.typeParameter",
                ((FunctionType) ((FunctionType) letValIfTrue._genericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].genericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.returnType",
                ((FunctionType) ((FunctionType) letValIfTrue._genericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._returnType());

        LambdaFunction<?> letValIfTrueLambda = (LambdaFunction<?>) letValIfTrue._values().getOnly();
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0]", letValIfTrueLambda);
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType", letValIfTrueLambda._classifierGenericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.returnType",
                ((FunctionType) letValIfTrueLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.returnType.typeArguments[0]",
                ((FunctionType) letValIfTrueLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType",
                ((FunctionType) letValIfTrueLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.parameters['x']",
                ((FunctionType) ((FunctionType) letValIfTrueLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.parameters['x'].genericType",
                ((FunctionType) ((FunctionType) letValIfTrueLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.parameters['x'].genericType.typeParameter",
                ((FunctionType) ((FunctionType) letValIfTrueLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.returnType",
                ((FunctionType) ((FunctionType) letValIfTrueLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0]", letValIfTrueLambda._expressionSequence().getOnly());
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].genericType", letValIfTrueLambda._expressionSequence().getOnly()._genericType());
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].genericType.typeArguments[0]", letValIfTrueLambda._expressionSequence().getOnly()._genericType()._typeArguments().getOnly());
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].genericType.typeArguments[0].rawType", letValIfTrueLambda._expressionSequence().getOnly()._genericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].genericType.typeArguments[0].rawType.parameters['x']",
                ((FunctionType) letValIfTrueLambda._expressionSequence().getOnly()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].genericType.typeArguments[0].rawType.parameters['x'].genericType",
                ((FunctionType) letValIfTrueLambda._expressionSequence().getOnly()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].genericType.typeArguments[0].rawType.parameters['x'].genericType.typeParameter",
                ((FunctionType) letValIfTrueLambda._expressionSequence().getOnly()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].genericType.typeArguments[0].rawType.returnType",
                ((FunctionType) letValIfTrueLambda._expressionSequence().getOnly()._genericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].values[0]",
                (LambdaFunction<?>) ((InstanceValue) letValIfTrueLambda._expressionSequence().getOnly())._values().getOnly());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].values[0].classifierGenericType.typeArguments[0].rawType",
                ((LambdaFunction<?>) ((InstanceValue) letValIfTrueLambda._expressionSequence().getOnly())._values().getOnly())._classifierGenericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].values[0].classifierGenericType.typeArguments[0].rawType.parameters['x']",
                ((FunctionType) ((LambdaFunction<?>) ((InstanceValue) letValIfTrueLambda._expressionSequence().getOnly())._values().getOnly())._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].values[0].classifierGenericType.typeArguments[0].rawType.parameters['x'].genericType",
                ((FunctionType) ((LambdaFunction<?>) ((InstanceValue) letValIfTrueLambda._expressionSequence().getOnly())._values().getOnly())._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].values[0].classifierGenericType.typeArguments[0].rawType.returnType",
                ((FunctionType) ((LambdaFunction<?>) ((InstanceValue) letValIfTrueLambda._expressionSequence().getOnly())._values().getOnly())._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].values[0].expressionSequence[0]",
                ((LambdaFunction<?>) ((InstanceValue) letValIfTrueLambda._expressionSequence().getOnly())._values().getOnly())._expressionSequence().getOnly());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].values[0].expressionSequence[0].genericType",
                ((LambdaFunction<?>) ((InstanceValue) letValIfTrueLambda._expressionSequence().getOnly())._values().getOnly())._expressionSequence().getOnly()._genericType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].values[0].expressionSequence[0].parametersValues[0]",
                ((SimpleFunctionExpression) ((LambdaFunction<?>) ((InstanceValue) letValIfTrueLambda._expressionSequence().getOnly())._values().getOnly())._expressionSequence().getOnly())._parametersValues().getOnly());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].values[0].expressionSequence[0].parametersValues[0].genericType",
                ((SimpleFunctionExpression) ((LambdaFunction<?>) ((InstanceValue) letValIfTrueLambda._expressionSequence().getOnly())._values().getOnly())._expressionSequence().getOnly())._parametersValues().getOnly()._genericType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[1].values[0].expressionSequence[0].values[0].expressionSequence[0].parametersValues[0].genericType.typeParameter",
                ((SimpleFunctionExpression) ((LambdaFunction<?>) ((InstanceValue) letValIfTrueLambda._expressionSequence().getOnly())._values().getOnly())._expressionSequence().getOnly())._parametersValues().getOnly()._genericType()._typeParameter());

        InstanceValue letValIfFalse = (InstanceValue) letValIfParams.get(2);
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[2]", letValIfFalse);
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[2].genericType", letValIfFalse._genericType());
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[2].genericType.typeArguments[0]", letValIfFalse._genericType()._typeArguments().getOnly());
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[2].genericType.typeArguments[0].rawType", letValIfFalse._genericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].genericType.typeArguments[0].rawType.returnType",
                ((FunctionType) letValIfFalse._genericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].genericType.typeArguments[0].rawType.returnType.typeArguments[0]",
                ((FunctionType) letValIfFalse._genericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].genericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType",
                ((FunctionType) letValIfFalse._genericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].genericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.parameters['']",
                ((FunctionType) ((FunctionType) letValIfFalse._genericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].genericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.parameters[''].genericType",
                ((FunctionType) ((FunctionType) letValIfFalse._genericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].genericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.parameters[''].genericType.typeParameter",
                ((FunctionType) ((FunctionType) letValIfFalse._genericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].genericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.returnType",
                ((FunctionType) ((FunctionType) letValIfFalse._genericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._returnType());

        LambdaFunction<?> letValIfFalseLambda = (LambdaFunction<?>) letValIfFalse._values().getOnly();
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0]", letValIfFalseLambda);
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType", letValIfFalseLambda._classifierGenericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType.returnType",
                ((FunctionType) letValIfFalseLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType.returnType.typeArguments[0]",
                ((FunctionType) letValIfFalseLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType",
                ((FunctionType) letValIfFalseLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.parameters['']",
                ((FunctionType) ((FunctionType) letValIfFalseLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.parameters[''].genericType",
                ((FunctionType) ((FunctionType) letValIfFalseLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.parameters[''].genericType.typeParameter",
                ((FunctionType) ((FunctionType) letValIfFalseLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].classifierGenericType.typeArguments[0].rawType.returnType.typeArguments[0].rawType.returnType",
                ((FunctionType) ((FunctionType) letValIfFalseLambda._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0]", letValIfFalseLambda._expressionSequence().getOnly());
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].genericType", letValIfFalseLambda._expressionSequence().getOnly()._genericType());
        assertId(path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].genericType.typeArguments[0]", letValIfFalseLambda._expressionSequence().getOnly()._genericType()._typeArguments().getOnly());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].genericType.typeArguments[0].rawType",
                letValIfFalseLambda._expressionSequence().getOnly()._genericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].genericType.typeArguments[0].rawType.parameters['']",
                ((FunctionType) letValIfFalseLambda._expressionSequence().getOnly()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].genericType.typeArguments[0].rawType.parameters[''].genericType",
                ((FunctionType) letValIfFalseLambda._expressionSequence().getOnly()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].genericType.typeArguments[0].rawType.parameters[''].genericType.typeParameter",
                ((FunctionType) letValIfFalseLambda._expressionSequence().getOnly()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].genericType.typeArguments[0].rawType.returnType",
                ((FunctionType) letValIfFalseLambda._expressionSequence().getOnly()._genericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].parametersValues[0]",
                ((SimpleFunctionExpression) letValIfFalseLambda._expressionSequence().getOnly())._parametersValues().getOnly());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].parametersValues[0].genericType",
                ((SimpleFunctionExpression) letValIfFalseLambda._expressionSequence().getOnly())._parametersValues().getOnly()._genericType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].parametersValues[0].genericType.typeArguments[0]",
                ((SimpleFunctionExpression) letValIfFalseLambda._expressionSequence().getOnly())._parametersValues().getOnly()._genericType()._typeArguments().getOnly());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].parametersValues[0].genericType.typeArguments[0].rawType",
                ((SimpleFunctionExpression) letValIfFalseLambda._expressionSequence().getOnly())._parametersValues().getOnly()._genericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].parametersValues[0].genericType.typeArguments[0].rawType.parameters['']",
                ((FunctionType) ((SimpleFunctionExpression) letValIfFalseLambda._expressionSequence().getOnly())._parametersValues().getOnly()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].parametersValues[0].genericType.typeArguments[0].rawType.parameters[''].genericType",
                ((FunctionType) ((SimpleFunctionExpression) letValIfFalseLambda._expressionSequence().getOnly())._parametersValues().getOnly()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].parametersValues[0].genericType.typeArguments[0].rawType.parameters[''].genericType.typeParameter",
                ((FunctionType) ((SimpleFunctionExpression) letValIfFalseLambda._expressionSequence().getOnly())._parametersValues().getOnly()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        assertId(
                path + ".expressionSequence[0].parametersValues[1].parametersValues[2].values[0].expressionSequence[0].parametersValues[0].genericType.typeArguments[0].rawType.returnType",
                ((FunctionType) ((SimpleFunctionExpression) letValIfFalseLambda._expressionSequence().getOnly())._parametersValues().getOnly()._genericType()._typeArguments().getOnly()._rawType())._returnType());

        SimpleFunctionExpression mapExp = (SimpleFunctionExpression) testFunction._expressionSequence().getLast();
        VariableExpression mapColParam = (VariableExpression) mapExp._parametersValues().getFirst();
        InstanceValue mapFuncParam = (InstanceValue) mapExp._parametersValues().getLast();
        LambdaFunction<?> mapFunc = (LambdaFunction<?>) mapFuncParam._values().getOnly();
        assertId(path + ".expressionSequence[1]", mapExp);
        assertId(path + ".expressionSequence[1].genericType", mapExp._genericType());
        assertId(path + ".expressionSequence[1].multiplicity", mapExp._multiplicity());
        assertId(path + ".expressionSequence[1].parametersValues[0]", mapColParam);
        assertId(path + ".expressionSequence[1].parametersValues[0].genericType", mapColParam._genericType());
        assertId(path + ".expressionSequence[1].parametersValues[0].genericType.typeParameter", mapColParam._genericType()._typeParameter());
        assertId(path + ".expressionSequence[1].parametersValues[0].multiplicity", mapColParam._multiplicity());
        assertId(path + ".expressionSequence[1].parametersValues[1]", mapFuncParam);
        assertId(path + ".expressionSequence[1].parametersValues[1].genericType", mapFuncParam._genericType());
        assertId(path + ".expressionSequence[1].parametersValues[1].genericType.typeArguments[0]", mapFuncParam._genericType()._typeArguments().getOnly());
        assertId(path + ".expressionSequence[1].parametersValues[1].genericType.typeArguments[0].rawType", mapFuncParam._genericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".expressionSequence[1].parametersValues[1].genericType.typeArguments[0].rawType.parameters['x']",
                ((FunctionType) mapFuncParam._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        assertId(
                path + ".expressionSequence[1].parametersValues[1].genericType.typeArguments[0].rawType.parameters['x'].genericType",
                ((FunctionType) mapFuncParam._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        assertId(
                path + ".expressionSequence[1].parametersValues[1].genericType.typeArguments[0].rawType.parameters['x'].genericType.typeParameter",
                ((FunctionType) mapFuncParam._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        assertId(
                path + ".expressionSequence[1].parametersValues[1].genericType.typeArguments[0].rawType.returnType",
                ((FunctionType) mapFuncParam._genericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(path + ".expressionSequence[1].parametersValues[1].values[0]", mapFunc);
        assertId(path + ".expressionSequence[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType", mapFunc._classifierGenericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".expressionSequence[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['x']",
                ((FunctionType) mapFunc._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        assertId(
                path + ".expressionSequence[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['x'].genericType",
                ((FunctionType) mapFunc._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        assertId(
                path + ".expressionSequence[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.parameters['x'].genericType.typeParameter",
                ((FunctionType) mapFunc._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        assertId(
                path + ".expressionSequence[1].parametersValues[1].values[0].classifierGenericType.typeArguments[0].rawType.returnType",
                ((FunctionType) mapFunc._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0]", mapFunc._expressionSequence().getOnly());
        assertId(path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0].genericType", mapFunc._expressionSequence().getOnly()._genericType());
        assertId(
                path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0]",
                ((SimpleFunctionExpression) mapFunc._expressionSequence().getOnly())._parametersValues().getFirst());
        assertId(
                path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].genericType",
                ((SimpleFunctionExpression) mapFunc._expressionSequence().getOnly())._parametersValues().getFirst()._genericType());
        assertId(
                path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].genericType.typeArguments[0]",
                ((SimpleFunctionExpression) mapFunc._expressionSequence().getOnly())._parametersValues().getFirst()._genericType()._typeArguments().getOnly());
        assertId(
                path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].genericType.typeArguments[0].rawType",
                ((SimpleFunctionExpression) mapFunc._expressionSequence().getOnly())._parametersValues().getFirst()._genericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].genericType.typeArguments[0].rawType.parameters['']",
                ((FunctionType) ((SimpleFunctionExpression) mapFunc._expressionSequence().getOnly())._parametersValues().getFirst()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        assertId(
                path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].genericType.typeArguments[0].rawType.parameters[''].genericType",
                ((FunctionType) ((SimpleFunctionExpression) mapFunc._expressionSequence().getOnly())._parametersValues().getFirst()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType());
        assertId(
                path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].genericType.typeArguments[0].rawType.parameters[''].genericType.typeParameter",
                ((FunctionType) ((SimpleFunctionExpression) mapFunc._expressionSequence().getOnly())._parametersValues().getFirst()._genericType()._typeArguments().getOnly()._rawType())._parameters().getOnly()._genericType()._typeParameter());
        assertId(
                path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[0].genericType.typeArguments[0].rawType.returnType",
                ((FunctionType) ((SimpleFunctionExpression) mapFunc._expressionSequence().getOnly())._parametersValues().getFirst()._genericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(
                path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[1]",
                ((SimpleFunctionExpression) mapFunc._expressionSequence().getOnly())._parametersValues().getLast());
        assertId(
                path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[1].genericType",
                ((SimpleFunctionExpression) mapFunc._expressionSequence().getOnly())._parametersValues().getLast()._genericType());
        assertId(
                path + ".expressionSequence[1].parametersValues[1].values[0].expressionSequence[0].parametersValues[1].genericType.typeParameter",
                ((SimpleFunctionExpression) mapFunc._expressionSequence().getOnly())._parametersValues().getLast()._genericType()._typeParameter());
    }

    @Test
    public void testFunction2()
    {
        String path = "test::model::testFunc2__String_1_";
        ConcreteFunctionDefinition<?> testFunction = getCoreInstance(path);
        assertId(path, testFunction);

        FunctionType functionType = (FunctionType) testFunction._classifierGenericType()._typeArguments().getOnly()._rawType();
        assertId(path + ".classifierGenericType.typeArguments[0].rawType", functionType);
        assertId(path + ".classifierGenericType.typeArguments[0].rawType.returnType", functionType._returnType());

        ListIterable<? extends ValueSpecification> expressionSequence = toList(testFunction._expressionSequence());
        SimpleFunctionExpression letPkg = (SimpleFunctionExpression) expressionSequence.get(0);
        assertId(path + ".expressionSequence[0]", letPkg);
        assertId(path + ".expressionSequence[0].genericType", letPkg._genericType());
        assertId(path + ".expressionSequence[0].parametersValues[0]", letPkg._parametersValues().getFirst());
        assertId(path + ".expressionSequence[0].parametersValues[0].genericType", letPkg._parametersValues().getFirst()._genericType());
        assertId(path + ".expressionSequence[0].parametersValues[1]", letPkg._parametersValues().getLast());
        assertId(path + ".expressionSequence[0].parametersValues[1].genericType", letPkg._parametersValues().getLast()._genericType());

        SimpleFunctionExpression letUnit = (SimpleFunctionExpression) expressionSequence.get(1);
        assertId(path + ".expressionSequence[1]", letUnit);
        assertId(path + ".expressionSequence[1].genericType", letUnit._genericType());
        assertId(path + ".expressionSequence[1].parametersValues[0]", letUnit._parametersValues().getFirst());
        assertId(path + ".expressionSequence[1].parametersValues[0].genericType", letUnit._parametersValues().getFirst()._genericType());
        assertId(path + ".expressionSequence[1].parametersValues[1]", letUnit._parametersValues().getLast());
        assertId(path + ".expressionSequence[1].parametersValues[1].genericType", letUnit._parametersValues().getLast()._genericType());

        SimpleFunctionExpression joinStrs = (SimpleFunctionExpression) expressionSequence.get(2);
        assertId(path + ".expressionSequence[2]", joinStrs);
        assertId(path + ".expressionSequence[2].genericType", joinStrs._genericType());
        assertId(path + ".expressionSequence[2].parametersValues[0].genericType", joinStrs._parametersValues().getOnly()._genericType());

        ListIterable<?> stringValSpecs = toList(((InstanceValue) joinStrs._parametersValues().getOnly())._values());

        SimpleFunctionExpression eltToPath = (SimpleFunctionExpression) stringValSpecs.get(0);
        assertId(path + ".expressionSequence[2].parametersValues[0].values[0]", eltToPath);
        assertId(path + ".expressionSequence[2].parametersValues[0].values[0].genericType", eltToPath._genericType());
        assertId(path + ".expressionSequence[2].parametersValues[0].values[0].parametersValues[0]", eltToPath._parametersValues().getOnly());
        assertId(path + ".expressionSequence[2].parametersValues[0].values[0].parametersValues[0].genericType", eltToPath._parametersValues().getOnly()._genericType());

        assertId(path + ".expressionSequence[2].parametersValues[0].values[1]", (ValueSpecification) stringValSpecs.get(1));
        assertId(path + ".expressionSequence[2].parametersValues[0].values[1].genericType", ((ValueSpecification) stringValSpecs.get(1))._genericType());

        SimpleFunctionExpression measureNameToOne = (SimpleFunctionExpression) stringValSpecs.get(2);
        SimpleFunctionExpression measureName = (SimpleFunctionExpression) measureNameToOne._parametersValues().getOnly();
        SimpleFunctionExpression unitMeasure = (SimpleFunctionExpression) measureName._parametersValues().getOnly();
        assertId(path + ".expressionSequence[2].parametersValues[0].values[2]", measureNameToOne);
        assertId(path + ".expressionSequence[2].parametersValues[0].values[2].genericType", measureNameToOne._genericType());
        assertId(path + ".expressionSequence[2].parametersValues[0].values[2].parametersValues[0]", measureName);
        assertId(path + ".expressionSequence[2].parametersValues[0].values[2].parametersValues[0].genericType", measureName._genericType());
        assertId(path + ".expressionSequence[2].parametersValues[0].values[2].parametersValues[0].parametersValues[0]", unitMeasure);
        assertId(path + ".expressionSequence[2].parametersValues[0].values[2].parametersValues[0].parametersValues[0].genericType", unitMeasure._genericType());
        assertId(path + ".expressionSequence[2].parametersValues[0].values[2].parametersValues[0].parametersValues[0].propertyName", unitMeasure._propertyName());
        assertId(path + ".expressionSequence[2].parametersValues[0].values[2].parametersValues[0].parametersValues[0].parametersValues[0]", unitMeasure._parametersValues().getOnly());
        assertId(path + ".expressionSequence[2].parametersValues[0].values[2].parametersValues[0].parametersValues[0].parametersValues[0].genericType", unitMeasure._parametersValues().getOnly()._genericType());
        assertId(path + ".expressionSequence[2].parametersValues[0].values[2].parametersValues[0].propertyName", measureName._propertyName());

        assertId(path + ".expressionSequence[2].parametersValues[0].values[3]", (ValueSpecification) stringValSpecs.get(3));
        assertId(path + ".expressionSequence[2].parametersValues[0].values[3].genericType", ((ValueSpecification) stringValSpecs.get(3))._genericType());

        SimpleFunctionExpression unitNameToOne = (SimpleFunctionExpression) stringValSpecs.get(4);
        SimpleFunctionExpression unitName = (SimpleFunctionExpression) unitNameToOne._parametersValues().getOnly();
        assertId(path + ".expressionSequence[2].parametersValues[0].values[4]", unitNameToOne);
        assertId(path + ".expressionSequence[2].parametersValues[0].values[4].genericType", unitNameToOne._genericType());
        assertId(path + ".expressionSequence[2].parametersValues[0].values[4].parametersValues[0]", unitName);
        assertId(path + ".expressionSequence[2].parametersValues[0].values[4].parametersValues[0].genericType", unitName._genericType());
        assertId(path + ".expressionSequence[2].parametersValues[0].values[4].parametersValues[0].parametersValues[0]", unitName._parametersValues().getOnly());
        assertId(path + ".expressionSequence[2].parametersValues[0].values[4].parametersValues[0].parametersValues[0].genericType", unitName._parametersValues().getOnly()._genericType());
        assertId(path + ".expressionSequence[2].parametersValues[0].values[4].parametersValues[0].propertyName", unitName._propertyName());
    }

    @Test
    public void testFunction3()
    {
        String path = "test::model::testFunc3__Any_MANY_";
        ConcreteFunctionDefinition<?> testFunction = getCoreInstance(path);
        assertId(path, testFunction);

        Assert.assertNull(getCoreInstance("test::model").getSourceInformation());

        FunctionType functionType = (FunctionType) testFunction._classifierGenericType()._typeArguments().getOnly()._rawType();
        assertId(path + ".classifierGenericType.typeArguments[0].rawType", functionType);
        assertId(path + ".classifierGenericType.typeArguments[0].rawType.returnType", functionType._returnType());

        InstanceValue listExpr = (InstanceValue) testFunction._expressionSequence().getOnly();
        assertId(path + ".expressionSequence[0]", listExpr);
        assertId(path + ".expressionSequence[0].genericType", listExpr._genericType());

        ListIterable<?> listValues = toList(listExpr._values());

        InstanceValue pkgWrapper = (InstanceValue) listValues.get(0);
        assertId(path + ".expressionSequence[0].values[0]", pkgWrapper);
        assertId(path + ".expressionSequence[0].values[0].genericType", pkgWrapper._genericType());

        LambdaFunction<?> lambdaFunc = (LambdaFunction<?>) listValues.get(4);
        assertId(path + ".expressionSequence[0].values[4]", lambdaFunc);

        FunctionType lambdaFuncType = (FunctionType) lambdaFunc._classifierGenericType()._typeArguments().getOnly()._rawType();
        assertId(path + ".expressionSequence[0].values[4].classifierGenericType.typeArguments[0].rawType", lambdaFuncType);
        assertId(path + ".expressionSequence[0].values[4].classifierGenericType.typeArguments[0].rawType.returnType", lambdaFuncType._returnType());

        SimpleFunctionExpression childrenExpr = (SimpleFunctionExpression) lambdaFunc._expressionSequence().getOnly();
        assertId(path + ".expressionSequence[0].values[4].expressionSequence[0]", childrenExpr);
        assertId(path + ".expressionSequence[0].values[4].expressionSequence[0].genericType", childrenExpr._genericType());
        assertId(path + ".expressionSequence[0].values[4].expressionSequence[0].parametersValues[0]", childrenExpr._parametersValues().getOnly());
        assertId(path + ".expressionSequence[0].values[4].expressionSequence[0].parametersValues[0].genericType", childrenExpr._parametersValues().getOnly()._genericType());
        assertId(path + ".expressionSequence[0].values[4].expressionSequence[0].propertyName", childrenExpr._propertyName());
    }

    @Test
    public void testMeasureWithNonconvertibleUnits()
    {
        String path = "test::model::Currency";
        Measure currency = getCoreInstance(path);
        assertId(path, currency);

        assertId(path + ".classifierGenericType", currency._classifierGenericType());
        assertId(path + ".generalizations[0]", currency._generalizations().getOnly());
        assertId(path + ".generalizations[0].general", currency._generalizations().getOnly()._general());

        assertId(path + ".canonicalUnit", currency._canonicalUnit());
        assertId(path + ".canonicalUnit.classifierGenericType", currency._canonicalUnit()._classifierGenericType());
        assertId(path + ".canonicalUnit.generalizations[0]", currency._canonicalUnit()._generalizations().getOnly());
        assertId(path + ".canonicalUnit.generalizations[0].general", currency._canonicalUnit()._generalizations().getOnly()._general());

        ListIterable<? extends Unit> units = toList(currency._nonCanonicalUnits());
        assertId(path + ".nonCanonicalUnits['GBP']", units.get(0));
        assertId(path + ".nonCanonicalUnits['GBP'].classifierGenericType", units.get(0)._classifierGenericType());
        assertId(path + ".nonCanonicalUnits['GBP'].generalizations[0]", units.get(0)._generalizations().getOnly());
        assertId(path + ".nonCanonicalUnits['GBP'].generalizations[0].general", units.get(0)._generalizations().getOnly()._general());

        assertId(path + ".nonCanonicalUnits['EUR']", units.get(1));
        assertId(path + ".nonCanonicalUnits['EUR'].classifierGenericType", units.get(1)._classifierGenericType());
        assertId(path + ".nonCanonicalUnits['EUR'].generalizations[0]", units.get(1)._generalizations().getOnly());
        assertId(path + ".nonCanonicalUnits['EUR'].generalizations[0].general", units.get(1)._generalizations().getOnly()._general());
    }

    @Test
    public void testMeasureWithConveritbleUnits()
    {
        String path = "test::model::Mass";
        Measure mass = getCoreInstance(path);
        assertId(path, mass);

        assertId(path + ".classifierGenericType", mass._classifierGenericType());
        assertId(path + ".generalizations[0]", mass._generalizations().getOnly());
        assertId(path + ".generalizations[0].general", mass._generalizations().getOnly()._general());

        assertId(path + ".canonicalUnit", mass._canonicalUnit());
        assertId(path + ".canonicalUnit.classifierGenericType", mass._canonicalUnit()._classifierGenericType());
        assertId(path + ".canonicalUnit.conversionFunction", mass._canonicalUnit()._conversionFunction());
        assertId(path + ".canonicalUnit.conversionFunction.classifierGenericType", mass._canonicalUnit()._conversionFunction()._classifierGenericType());
        assertId(path + ".canonicalUnit.conversionFunction.classifierGenericType.typeArguments[0]", mass._canonicalUnit()._conversionFunction()._classifierGenericType()._typeArguments().getOnly());
        assertId(path + ".canonicalUnit.conversionFunction.classifierGenericType.typeArguments[0].rawType", mass._canonicalUnit()._conversionFunction()._classifierGenericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".canonicalUnit.conversionFunction.classifierGenericType.typeArguments[0].rawType.parameters['x']",
                ((FunctionType) mass._canonicalUnit()._conversionFunction()._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        assertId(
                path + ".canonicalUnit.conversionFunction.classifierGenericType.typeArguments[0].rawType.returnType",
                ((FunctionType) mass._canonicalUnit()._conversionFunction()._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(path + ".canonicalUnit.conversionFunction.expressionSequence[0]", mass._canonicalUnit()._conversionFunction()._expressionSequence().getOnly());
        assertId(path + ".canonicalUnit.conversionFunction.expressionSequence[0].genericType", mass._canonicalUnit()._conversionFunction()._expressionSequence().getOnly()._genericType());
        assertId(path + ".canonicalUnit.generalizations[0]", mass._canonicalUnit()._generalizations().getOnly());
        assertId(path + ".canonicalUnit.generalizations[0].general", mass._canonicalUnit()._generalizations().getOnly()._general());

        ListIterable<? extends Unit> units = toList(mass._nonCanonicalUnits());
        assertId(path + ".nonCanonicalUnits['Kilogram']", units.get(0));
        assertId(path + ".nonCanonicalUnits['Kilogram'].classifierGenericType", units.get(0)._classifierGenericType());
        assertId(path + ".nonCanonicalUnits['Kilogram'].conversionFunction", units.get(0)._conversionFunction());
        assertId(path + ".nonCanonicalUnits['Kilogram'].conversionFunction.classifierGenericType", units.get(0)._conversionFunction()._classifierGenericType());
        assertId(path + ".nonCanonicalUnits['Kilogram'].conversionFunction.classifierGenericType.typeArguments[0]", units.get(0)._conversionFunction()._classifierGenericType()._typeArguments().getOnly());
        assertId(path + ".nonCanonicalUnits['Kilogram'].conversionFunction.classifierGenericType.typeArguments[0].rawType", units.get(0)._conversionFunction()._classifierGenericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".nonCanonicalUnits['Kilogram'].conversionFunction.classifierGenericType.typeArguments[0].rawType.parameters['x']",
                ((FunctionType) units.get(0)._conversionFunction()._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        assertId(
                path + ".nonCanonicalUnits['Kilogram'].conversionFunction.classifierGenericType.typeArguments[0].rawType.returnType",
                ((FunctionType) units.get(0)._conversionFunction()._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(path + ".nonCanonicalUnits['Kilogram'].conversionFunction.expressionSequence[0]", units.get(0)._conversionFunction()._expressionSequence().getOnly());
        assertId(path + ".nonCanonicalUnits['Kilogram'].conversionFunction.expressionSequence[0].genericType", units.get(0)._conversionFunction()._expressionSequence().getOnly()._genericType());
        assertId(
                path + ".nonCanonicalUnits['Kilogram'].conversionFunction.expressionSequence[0].parametersValues[0].values[0]",
                (CoreInstance) ((InstanceValue) ((SimpleFunctionExpression) (units.get(0)._conversionFunction()._expressionSequence().getOnly()))._parametersValues().getOnly())._values().getFirst());
        assertId(
                path + ".nonCanonicalUnits['Kilogram'].conversionFunction.expressionSequence[0].parametersValues[0].values[0].genericType",
                ((VariableExpression) ((InstanceValue) ((SimpleFunctionExpression) (units.get(0)._conversionFunction()._expressionSequence().getOnly()))._parametersValues().getOnly())._values().getFirst())._genericType());
        assertId(
                path + ".nonCanonicalUnits['Kilogram'].conversionFunction.expressionSequence[0].parametersValues[0].values[1]",
                (CoreInstance) ((InstanceValue) ((SimpleFunctionExpression) (units.get(0)._conversionFunction()._expressionSequence().getOnly()))._parametersValues().getOnly())._values().getLast());
        assertId(
                path + ".nonCanonicalUnits['Kilogram'].conversionFunction.expressionSequence[0].parametersValues[0].values[1].genericType",
                ((InstanceValue) ((InstanceValue) ((SimpleFunctionExpression) (units.get(0)._conversionFunction()._expressionSequence().getOnly()))._parametersValues().getOnly())._values().getLast())._genericType());
        assertId(path + ".nonCanonicalUnits['Kilogram'].generalizations[0]", units.get(0)._generalizations().getOnly());
        assertId(path + ".nonCanonicalUnits['Kilogram'].generalizations[0].general", units.get(0)._generalizations().getOnly()._general());

        assertId(path + ".nonCanonicalUnits['Pound']", units.get(1));
        assertId(path + ".nonCanonicalUnits['Pound'].classifierGenericType", units.get(1)._classifierGenericType());
        assertId(path + ".nonCanonicalUnits['Pound'].conversionFunction", units.get(1)._conversionFunction());
        assertId(path + ".nonCanonicalUnits['Pound'].conversionFunction.classifierGenericType", units.get(1)._conversionFunction()._classifierGenericType());
        assertId(path + ".nonCanonicalUnits['Pound'].conversionFunction.classifierGenericType.typeArguments[0]", units.get(1)._conversionFunction()._classifierGenericType()._typeArguments().getOnly());
        assertId(path + ".nonCanonicalUnits['Pound'].conversionFunction.classifierGenericType.typeArguments[0].rawType", units.get(1)._conversionFunction()._classifierGenericType()._typeArguments().getOnly()._rawType());
        assertId(
                path + ".nonCanonicalUnits['Pound'].conversionFunction.classifierGenericType.typeArguments[0].rawType.parameters['x']",
                ((FunctionType) units.get(1)._conversionFunction()._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters().getOnly());
        assertId(
                path + ".nonCanonicalUnits['Pound'].conversionFunction.classifierGenericType.typeArguments[0].rawType.returnType",
                ((FunctionType) units.get(1)._conversionFunction()._classifierGenericType()._typeArguments().getOnly()._rawType())._returnType());
        assertId(path + ".nonCanonicalUnits['Pound'].conversionFunction.expressionSequence[0]", units.get(1)._conversionFunction()._expressionSequence().getOnly());
        assertId(path + ".nonCanonicalUnits['Pound'].conversionFunction.expressionSequence[0].genericType", units.get(1)._conversionFunction()._expressionSequence().getOnly()._genericType());
        assertId(
                path + ".nonCanonicalUnits['Pound'].conversionFunction.expressionSequence[0].parametersValues[0].values[0]",
                (CoreInstance) ((InstanceValue) ((SimpleFunctionExpression) (units.get(1)._conversionFunction()._expressionSequence().getOnly()))._parametersValues().getOnly())._values().getFirst());
        assertId(
                path + ".nonCanonicalUnits['Pound'].conversionFunction.expressionSequence[0].parametersValues[0].values[0].genericType",
                ((VariableExpression) ((InstanceValue) ((SimpleFunctionExpression) (units.get(1)._conversionFunction()._expressionSequence().getOnly()))._parametersValues().getOnly())._values().getFirst())._genericType());
        assertId(
                path + ".nonCanonicalUnits['Pound'].conversionFunction.expressionSequence[0].parametersValues[0].values[1]",
                (CoreInstance) ((InstanceValue) ((SimpleFunctionExpression) (units.get(1)._conversionFunction()._expressionSequence().getOnly()))._parametersValues().getOnly())._values().getLast());
        assertId(
                path + ".nonCanonicalUnits['Pound'].conversionFunction.expressionSequence[0].parametersValues[0].values[1].genericType",
                ((InstanceValue) ((InstanceValue) ((SimpleFunctionExpression) (units.get(1)._conversionFunction()._expressionSequence().getOnly()))._parametersValues().getOnly())._values().getLast())._genericType());
        assertId(path + ".nonCanonicalUnits['Pound'].generalizations[0]", units.get(1)._generalizations().getOnly());
        assertId(path + ".nonCanonicalUnits['Pound'].generalizations[0].general", units.get(1)._generalizations().getOnly()._general());
    }

    @Test
    public void testTopLevels()
    {
        repository.getTopLevels().forEach(e -> assertId(e.getName(), e));
    }

    @Test
    public void testPackageTree()
    {
        PackageTreeIterable.newRootPackageTreeIterable(processorSupport)
                .flatCollect(Package::_children)
                .forEach(e -> assertId(PackageableElement.getUserPathForPackageableElement(e), e));
    }

    @Test
    public void testAllInstancesWithReferenceIds()
    {
        MutableList<Pair<CoreInstance, Exception>> failures = Lists.mutable.empty();
        GraphNodeIterable.builder()
                .withStartingNodes(repository.getTopLevels())
                .withKeyFilter((node, key) -> !M3PropertyPaths.BACK_REFERENCE_PROPERTY_PATHS.contains(node.getRealKeyByName(key)))
                .withNodeFilter(node -> GraphWalkFilterResult.get(!AnyStubHelper.isStub(node) && ((node.getSourceInformation() != null) || _Package.isPackage(node, processorSupport)), true))
                .build()
                .forEach(instance ->
                {
                    try
                    {
                        String id = referenceIdProvider.getReferenceId(instance);
                        if (id == null)
                        {
                            failures.add(Tuples.pair(instance, null));
                        }
                    }
                    catch (Exception e)
                    {
                        failures.add(Tuples.pair(instance, e));
                    }
                });
        if (failures.notEmpty())
        {
            StringBuilder builder = new StringBuilder("There were ").append(failures.size()).append(" instances for which an id could not be provided:");
            failures.sortThis(Comparator.nullsFirst(Comparator.comparing(p -> p.getOne().getSourceInformation()))).forEach(pair ->
            {
                CoreInstance instance = pair.getOne();
                builder.append("\n\t").append(instance).append(" (");
                PackageableElement.writeUserPathForPackageableElement(builder, instance.getClassifier());
                SourceInformation sourceInfo = instance.getSourceInformation();
                if (sourceInfo != null)
                {
                    instance.getSourceInformation().appendMessage(builder.append(", "));
                }
                builder.append(')');

                Exception e = pair.getTwo();
                if (e != null)
                {
                    builder.append("\n\t\t").append("exception (").append(e.getClass().getName()).append("): ").append(e.getMessage());
                }
            });
            Assert.fail(builder.toString());
        }
    }

    @Test
    public void testAllNonPackagesWithNoSourceInfo()
    {
        GraphNodeIterable.builder()
                .withStartingNodes(repository.getTopLevels())
                .withKeyFilter((node, key) -> !M3PropertyPaths.BACK_REFERENCE_PROPERTY_PATHS.contains(node.getRealKeyByName(key)))
                .withNodeFilter(node -> GraphWalkFilterResult.get((node.getSourceInformation() == null) && !_Package.isPackage(node, processorSupport), true))
                .build()
                .forEach(instance ->
                {
                    try
                    {
                        String id = referenceIdProvider.getReferenceId(instance);
                        Assert.fail("Expected no id for " + instance + ", got: " + id);
                    }
                    catch (IllegalArgumentException e)
                    {
                        String message = e.getMessage();
                        if ((message == null) || !message.startsWith("Cannot get a reference id for instance with no source information (no containing element): "))
                        {
                            Assert.fail("Incorrect message: " + message);
                        }
                    }
                    catch (Exception e)
                    {
                        StringWriter writer = new StringWriter().append("Unexpected exception: ");
                        e.printStackTrace(new PrintWriter(writer, true));
                        Assert.fail(writer.toString());
                    }
                });
    }

    private void assertId(String expected, CoreInstance instance)
    {
        String actual = referenceIdProvider.getReferenceId(instance);
        if (!Objects.equals(expected, actual))
        {
            StringBuilder builder = new StringBuilder("Incorrect id for ").append(instance);
            instance.getSourceInformation().appendMessage(builder.append(" (")).append(')');
            Assert.assertEquals(builder.toString(), expected, actual);
        }
    }

    private void assertId(String message, String expected, CoreInstance instance)
    {
        Assert.assertEquals(message, expected, referenceIdProvider.getReferenceId(instance));
    }
}
