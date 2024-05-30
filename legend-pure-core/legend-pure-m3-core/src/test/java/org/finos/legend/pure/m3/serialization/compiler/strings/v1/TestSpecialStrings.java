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

package org.finos.legend.pure.m3.serialization.compiler.strings.v1;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.junit.Assert;
import org.junit.Test;

// DO NOT MAKE CHANGES IN THIS TEST
// If changes are needed, make them in BaseStringIndex
public class TestSpecialStrings
{
    @Test
    public void testSpecialStringIds()
    {
        Assert.assertEquals(-128, BaseStringIndex.getSpecialStringId(null));
        Assert.assertEquals(-127, BaseStringIndex.getSpecialStringId(""));
        Assert.assertEquals(-126, BaseStringIndex.getSpecialStringId("::"));
        Assert.assertEquals(-125, BaseStringIndex.getSpecialStringId("/"));
        Assert.assertEquals(-124, BaseStringIndex.getSpecialStringId("."));
        Assert.assertEquals(-123, BaseStringIndex.getSpecialStringId("Boolean"));
        Assert.assertEquals(-122, BaseStringIndex.getSpecialStringId("Byte"));
        Assert.assertEquals(-121, BaseStringIndex.getSpecialStringId("Date"));
        Assert.assertEquals(-120, BaseStringIndex.getSpecialStringId("DateTime"));
        Assert.assertEquals(-119, BaseStringIndex.getSpecialStringId("Decimal"));
        Assert.assertEquals(-118, BaseStringIndex.getSpecialStringId("Float"));
        Assert.assertEquals(-117, BaseStringIndex.getSpecialStringId("Integer"));
        Assert.assertEquals(-116, BaseStringIndex.getSpecialStringId("LatestDate"));
        Assert.assertEquals(-115, BaseStringIndex.getSpecialStringId("Number"));
        Assert.assertEquals(-114, BaseStringIndex.getSpecialStringId("StrictDate"));
        Assert.assertEquals(-113, BaseStringIndex.getSpecialStringId("StrictTime"));
        Assert.assertEquals(-112, BaseStringIndex.getSpecialStringId("String"));
        Assert.assertEquals(-111, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::type::Any"));
        Assert.assertEquals(-110, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::relationship::Association"));
        Assert.assertEquals(-109, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::type::Class"));
        Assert.assertEquals(-108, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::function::ConcreteFunctionDefinition"));
        Assert.assertEquals(-107, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::type::Enumeration"));
        Assert.assertEquals(-106, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::import::ImportGroup"));
        Assert.assertEquals(-105, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::function::LambdaFunction"));
        Assert.assertEquals(-104, BaseStringIndex.getSpecialStringId("meta::pure::functions::collection::List"));
        Assert.assertEquals(-103, BaseStringIndex.getSpecialStringId("meta::pure::functions::collection::Map"));
        Assert.assertEquals(-102, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::function::NativeFunction"));
        Assert.assertEquals(-101, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::type::Nil"));
        Assert.assertEquals(-100, BaseStringIndex.getSpecialStringId("Package"));
        Assert.assertEquals(-99, BaseStringIndex.getSpecialStringId("meta::pure::functions::collection::Pair"));
        Assert.assertEquals(-98, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::type::PrimitiveType"));
        Assert.assertEquals(-97, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::extension::Profile"));
        Assert.assertEquals(-96, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::function::property::Property"));
        Assert.assertEquals(-95, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::function::property::QualifiedProperty"));
        Assert.assertEquals(-94, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::ReferenceUsage"));
        Assert.assertEquals(-93, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::relation::Relation"));
        Assert.assertEquals(-92, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::relation::RelationType"));
        Assert.assertEquals(-91, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::treepath::RootRouteNode"));
        Assert.assertEquals(-90, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::extension::Stereotype"));
        Assert.assertEquals(-89, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::extension::Tag"));
        Assert.assertEquals(-88, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::extension::TaggedValue"));
        Assert.assertEquals(-87, BaseStringIndex.getSpecialStringId("Root"));
        Assert.assertEquals(-86, BaseStringIndex.getSpecialStringId("collection"));
        Assert.assertEquals(-85, BaseStringIndex.getSpecialStringId("function"));
        Assert.assertEquals(-84, BaseStringIndex.getSpecialStringId("functions"));
        Assert.assertEquals(-83, BaseStringIndex.getSpecialStringId("meta"));
        Assert.assertEquals(-82, BaseStringIndex.getSpecialStringId("metamodel"));
        Assert.assertEquals(-81, BaseStringIndex.getSpecialStringId("property"));
        Assert.assertEquals(-80, BaseStringIndex.getSpecialStringId("pure"));
        Assert.assertEquals(-79, BaseStringIndex.getSpecialStringId("relationship"));
        Assert.assertEquals(-78, BaseStringIndex.getSpecialStringId("string"));
        Assert.assertEquals(-77, BaseStringIndex.getSpecialStringId("type"));
        Assert.assertEquals(-76, BaseStringIndex.getSpecialStringId("children"));
        Assert.assertEquals(-75, BaseStringIndex.getSpecialStringId("classifierGenericType"));
        Assert.assertEquals(-74, BaseStringIndex.getSpecialStringId("constraints"));
        Assert.assertEquals(-73, BaseStringIndex.getSpecialStringId("expressionSequence"));
        Assert.assertEquals(-72, BaseStringIndex.getSpecialStringId("func"));
        Assert.assertEquals(-71, BaseStringIndex.getSpecialStringId("genericType"));
        Assert.assertEquals(-70, BaseStringIndex.getSpecialStringId("multiplicity"));
        Assert.assertEquals(-69, BaseStringIndex.getSpecialStringId("multiplicityArguments"));
        Assert.assertEquals(-68, BaseStringIndex.getSpecialStringId("multiplicityParameters"));
        Assert.assertEquals(-67, BaseStringIndex.getSpecialStringId("owner"));
        Assert.assertEquals(-66, BaseStringIndex.getSpecialStringId("postConstraints"));
        Assert.assertEquals(-65, BaseStringIndex.getSpecialStringId("preConstraints"));
        Assert.assertEquals(-64, BaseStringIndex.getSpecialStringId("properties"));
        Assert.assertEquals(-63, BaseStringIndex.getSpecialStringId("propertiesFromAssociations"));
        Assert.assertEquals(-62, BaseStringIndex.getSpecialStringId("qualifiedProperties"));
        Assert.assertEquals(-61, BaseStringIndex.getSpecialStringId("qualifiedPropertiesFromAssociations"));
        Assert.assertEquals(-60, BaseStringIndex.getSpecialStringId("rawType"));
        Assert.assertEquals(-59, BaseStringIndex.getSpecialStringId("resolvedEnum"));
        Assert.assertEquals(-58, BaseStringIndex.getSpecialStringId("resolvedNode"));
        Assert.assertEquals(-57, BaseStringIndex.getSpecialStringId("resolvedProperty"));
        Assert.assertEquals(-56, BaseStringIndex.getSpecialStringId("returnMultiplicity"));
        Assert.assertEquals(-55, BaseStringIndex.getSpecialStringId("returnType"));
        Assert.assertEquals(-54, BaseStringIndex.getSpecialStringId("stereotypes"));
        Assert.assertEquals(-53, BaseStringIndex.getSpecialStringId("tag"));
        Assert.assertEquals(-52, BaseStringIndex.getSpecialStringId("taggedValues"));
        Assert.assertEquals(-51, BaseStringIndex.getSpecialStringId("typeArguments"));
        Assert.assertEquals(-50, BaseStringIndex.getSpecialStringId("typeParameters"));
        Assert.assertEquals(-49, BaseStringIndex.getSpecialStringId("values"));
        Assert.assertEquals(-48, BaseStringIndex.getSpecialStringId("Pure"));
        Assert.assertEquals(-47, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::multiplicity::OneMany"));
        Assert.assertEquals(-46, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::multiplicity::PureOne"));
        Assert.assertEquals(-45, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::multiplicity::PureZero"));
        Assert.assertEquals(-44, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::multiplicity::ZeroMany"));
        Assert.assertEquals(-43, BaseStringIndex.getSpecialStringId("meta::pure::metamodel::multiplicity::ZeroOne"));

        Assert.assertEquals(0, BaseStringIndex.getSpecialStringId("not a special string"));
        Assert.assertEquals(0, BaseStringIndex.getSpecialStringId("the quick brown fox"));
        Assert.assertEquals(0, BaseStringIndex.getSpecialStringId("jumped over the lazy dog"));
    }

    @Test
    public void testSpecialStringsById()
    {
        Assert.assertNull(BaseStringIndex.getSpecialString(-128));
        Assert.assertEquals("", BaseStringIndex.getSpecialString(-127));
        Assert.assertEquals("::", BaseStringIndex.getSpecialString(-126));
        Assert.assertEquals("/", BaseStringIndex.getSpecialString(-125));
        Assert.assertEquals(".", BaseStringIndex.getSpecialString(-124));
        Assert.assertEquals("Boolean", BaseStringIndex.getSpecialString(-123));
        Assert.assertEquals("Byte", BaseStringIndex.getSpecialString(-122));
        Assert.assertEquals("Date", BaseStringIndex.getSpecialString(-121));
        Assert.assertEquals("DateTime", BaseStringIndex.getSpecialString(-120));
        Assert.assertEquals("Decimal", BaseStringIndex.getSpecialString(-119));
        Assert.assertEquals("Float", BaseStringIndex.getSpecialString(-118));
        Assert.assertEquals("Integer", BaseStringIndex.getSpecialString(-117));
        Assert.assertEquals("LatestDate", BaseStringIndex.getSpecialString(-116));
        Assert.assertEquals("Number", BaseStringIndex.getSpecialString(-115));
        Assert.assertEquals("StrictDate", BaseStringIndex.getSpecialString(-114));
        Assert.assertEquals("StrictTime", BaseStringIndex.getSpecialString(-113));
        Assert.assertEquals("String", BaseStringIndex.getSpecialString(-112));
        Assert.assertEquals("meta::pure::metamodel::type::Any", BaseStringIndex.getSpecialString(-111));
        Assert.assertEquals("meta::pure::metamodel::relationship::Association", BaseStringIndex.getSpecialString(-110));
        Assert.assertEquals("meta::pure::metamodel::type::Class", BaseStringIndex.getSpecialString(-109));
        Assert.assertEquals("meta::pure::metamodel::function::ConcreteFunctionDefinition", BaseStringIndex.getSpecialString(-108));
        Assert.assertEquals("meta::pure::metamodel::type::Enumeration", BaseStringIndex.getSpecialString(-107));
        Assert.assertEquals("meta::pure::metamodel::import::ImportGroup", BaseStringIndex.getSpecialString(-106));
        Assert.assertEquals("meta::pure::metamodel::function::LambdaFunction", BaseStringIndex.getSpecialString(-105));
        Assert.assertEquals("meta::pure::functions::collection::List", BaseStringIndex.getSpecialString(-104));
        Assert.assertEquals("meta::pure::functions::collection::Map", BaseStringIndex.getSpecialString(-103));
        Assert.assertEquals("meta::pure::metamodel::function::NativeFunction", BaseStringIndex.getSpecialString(-102));
        Assert.assertEquals("meta::pure::metamodel::type::Nil", BaseStringIndex.getSpecialString(-101));
        Assert.assertEquals("Package", BaseStringIndex.getSpecialString(-100));
        Assert.assertEquals("meta::pure::functions::collection::Pair", BaseStringIndex.getSpecialString(-99));
        Assert.assertEquals("meta::pure::metamodel::type::PrimitiveType", BaseStringIndex.getSpecialString(-98));
        Assert.assertEquals("meta::pure::metamodel::extension::Profile", BaseStringIndex.getSpecialString(-97));
        Assert.assertEquals("meta::pure::metamodel::function::property::Property", BaseStringIndex.getSpecialString(-96));
        Assert.assertEquals("meta::pure::metamodel::function::property::QualifiedProperty", BaseStringIndex.getSpecialString(-95));
        Assert.assertEquals("meta::pure::metamodel::ReferenceUsage", BaseStringIndex.getSpecialString(-94));
        Assert.assertEquals("meta::pure::metamodel::relation::Relation", BaseStringIndex.getSpecialString(-93));
        Assert.assertEquals("meta::pure::metamodel::relation::RelationType", BaseStringIndex.getSpecialString(-92));
        Assert.assertEquals("meta::pure::metamodel::treepath::RootRouteNode", BaseStringIndex.getSpecialString(-91));
        Assert.assertEquals("meta::pure::metamodel::extension::Stereotype", BaseStringIndex.getSpecialString(-90));
        Assert.assertEquals("meta::pure::metamodel::extension::Tag", BaseStringIndex.getSpecialString(-89));
        Assert.assertEquals("meta::pure::metamodel::extension::TaggedValue", BaseStringIndex.getSpecialString(-88));
        Assert.assertEquals("Root", BaseStringIndex.getSpecialString(-87));
        Assert.assertEquals("collection", BaseStringIndex.getSpecialString(-86));
        Assert.assertEquals("function", BaseStringIndex.getSpecialString(-85));
        Assert.assertEquals("functions", BaseStringIndex.getSpecialString(-84));
        Assert.assertEquals("meta", BaseStringIndex.getSpecialString(-83));
        Assert.assertEquals("metamodel", BaseStringIndex.getSpecialString(-82));
        Assert.assertEquals("property", BaseStringIndex.getSpecialString(-81));
        Assert.assertEquals("pure", BaseStringIndex.getSpecialString(-80));
        Assert.assertEquals("relationship", BaseStringIndex.getSpecialString(-79));
        Assert.assertEquals("string", BaseStringIndex.getSpecialString(-78));
        Assert.assertEquals("type", BaseStringIndex.getSpecialString(-77));
        Assert.assertEquals("children", BaseStringIndex.getSpecialString(-76));
        Assert.assertEquals("classifierGenericType", BaseStringIndex.getSpecialString(-75));
        Assert.assertEquals("constraints", BaseStringIndex.getSpecialString(-74));
        Assert.assertEquals("expressionSequence", BaseStringIndex.getSpecialString(-73));
        Assert.assertEquals("func", BaseStringIndex.getSpecialString(-72));
        Assert.assertEquals("genericType", BaseStringIndex.getSpecialString(-71));
        Assert.assertEquals("multiplicity", BaseStringIndex.getSpecialString(-70));
        Assert.assertEquals("multiplicityArguments", BaseStringIndex.getSpecialString(-69));
        Assert.assertEquals("multiplicityParameters", BaseStringIndex.getSpecialString(-68));
        Assert.assertEquals("owner", BaseStringIndex.getSpecialString(-67));
        Assert.assertEquals("postConstraints", BaseStringIndex.getSpecialString(-66));
        Assert.assertEquals("preConstraints", BaseStringIndex.getSpecialString(-65));
        Assert.assertEquals("properties", BaseStringIndex.getSpecialString(-64));
        Assert.assertEquals("propertiesFromAssociations", BaseStringIndex.getSpecialString(-63));
        Assert.assertEquals("qualifiedProperties", BaseStringIndex.getSpecialString(-62));
        Assert.assertEquals("qualifiedPropertiesFromAssociations", BaseStringIndex.getSpecialString(-61));
        Assert.assertEquals("rawType", BaseStringIndex.getSpecialString(-60));
        Assert.assertEquals("resolvedEnum", BaseStringIndex.getSpecialString(-59));
        Assert.assertEquals("resolvedNode", BaseStringIndex.getSpecialString(-58));
        Assert.assertEquals("resolvedProperty", BaseStringIndex.getSpecialString(-57));
        Assert.assertEquals("returnMultiplicity", BaseStringIndex.getSpecialString(-56));
        Assert.assertEquals("returnType", BaseStringIndex.getSpecialString(-55));
        Assert.assertEquals("stereotypes", BaseStringIndex.getSpecialString(-54));
        Assert.assertEquals("tag", BaseStringIndex.getSpecialString(-53));
        Assert.assertEquals("taggedValues", BaseStringIndex.getSpecialString(-52));
        Assert.assertEquals("typeArguments", BaseStringIndex.getSpecialString(-51));
        Assert.assertEquals("typeParameters", BaseStringIndex.getSpecialString(-50));
        Assert.assertEquals("values", BaseStringIndex.getSpecialString(-49));
        Assert.assertEquals("Pure", BaseStringIndex.getSpecialString(-48));
        Assert.assertEquals("meta::pure::metamodel::multiplicity::OneMany", BaseStringIndex.getSpecialString(-47));
        Assert.assertEquals("meta::pure::metamodel::multiplicity::PureOne", BaseStringIndex.getSpecialString(-46));
        Assert.assertEquals("meta::pure::metamodel::multiplicity::PureZero", BaseStringIndex.getSpecialString(-45));
        Assert.assertEquals("meta::pure::metamodel::multiplicity::ZeroMany", BaseStringIndex.getSpecialString(-44));
        Assert.assertEquals("meta::pure::metamodel::multiplicity::ZeroOne", BaseStringIndex.getSpecialString(-43));

        Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> BaseStringIndex.getSpecialString(0));
        Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> BaseStringIndex.getSpecialString(1));
        Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> BaseStringIndex.getSpecialString(10));
        Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> BaseStringIndex.getSpecialString(-40));
        Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> BaseStringIndex.getSpecialString(-41));
        Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> BaseStringIndex.getSpecialString(-129));
        Assert.assertThrows(ArrayIndexOutOfBoundsException.class, () -> BaseStringIndex.getSpecialString(-130));
    }

    @Test
    public void testIsSpecialStringId()
    {
        for (int i = -128; i <= -43; i++)
        {
            Assert.assertTrue(Integer.toString(i), BaseStringIndex.isSpecialStringId(i));
        }

        Assert.assertFalse(BaseStringIndex.isSpecialStringId(0));
        Assert.assertFalse(BaseStringIndex.isSpecialStringId(1));
        Assert.assertFalse(BaseStringIndex.isSpecialStringId(-40));
        Assert.assertFalse(BaseStringIndex.isSpecialStringId(-41));
        Assert.assertFalse(BaseStringIndex.isSpecialStringId(-129));
        Assert.assertFalse(BaseStringIndex.isSpecialStringId(-130));
    }

    @Test
    public void testForEachSpecialString()
    {
        MutableList<String> expected = Lists.mutable.with(
                null,
                "",
                "::",
                "/",
                ".",
                "Boolean",
                "Byte",
                "Date",
                "DateTime",
                "Decimal",
                "Float",
                "Integer",
                "LatestDate",
                "Number",
                "StrictDate",
                "StrictTime",
                "String",
                "meta::pure::metamodel::type::Any",
                "meta::pure::metamodel::relationship::Association",
                "meta::pure::metamodel::type::Class",
                "meta::pure::metamodel::function::ConcreteFunctionDefinition",
                "meta::pure::metamodel::type::Enumeration",
                "meta::pure::metamodel::import::ImportGroup",
                "meta::pure::metamodel::function::LambdaFunction",
                "meta::pure::functions::collection::List",
                "meta::pure::functions::collection::Map",
                "meta::pure::metamodel::function::NativeFunction",
                "meta::pure::metamodel::type::Nil",
                "Package",
                "meta::pure::functions::collection::Pair",
                "meta::pure::metamodel::type::PrimitiveType",
                "meta::pure::metamodel::extension::Profile",
                "meta::pure::metamodel::function::property::Property",
                "meta::pure::metamodel::function::property::QualifiedProperty",
                "meta::pure::metamodel::ReferenceUsage",
                "meta::pure::metamodel::relation::Relation",
                "meta::pure::metamodel::relation::RelationType",
                "meta::pure::metamodel::treepath::RootRouteNode",
                "meta::pure::metamodel::extension::Stereotype",
                "meta::pure::metamodel::extension::Tag",
                "meta::pure::metamodel::extension::TaggedValue",
                "Root",
                "collection",
                "function",
                "functions",
                "meta",
                "metamodel",
                "property",
                "pure",
                "relationship",
                "string",
                "type",
                "children",
                "classifierGenericType",
                "constraints",
                "expressionSequence",
                "func",
                "genericType",
                "multiplicity",
                "multiplicityArguments",
                "multiplicityParameters",
                "owner",
                "postConstraints",
                "preConstraints",
                "properties",
                "propertiesFromAssociations",
                "qualifiedProperties",
                "qualifiedPropertiesFromAssociations",
                "rawType",
                "resolvedEnum",
                "resolvedNode",
                "resolvedProperty",
                "returnMultiplicity",
                "returnType",
                "stereotypes",
                "tag",
                "taggedValues",
                "typeArguments",
                "typeParameters",
                "values",
                "Pure",
                "meta::pure::metamodel::multiplicity::OneMany",
                "meta::pure::metamodel::multiplicity::PureOne",
                "meta::pure::metamodel::multiplicity::PureZero",
                "meta::pure::metamodel::multiplicity::ZeroMany",
                "meta::pure::metamodel::multiplicity::ZeroOne"
                );

        MutableList<String> actual = Lists.mutable.empty();
        BaseStringIndex.forEachSpecialString(actual::add);
        Assert.assertEquals(expected, actual);
        Assert.assertTrue(actual.size() <= Byte.MAX_VALUE);
        Assert.assertEquals("duplicates", Sets.mutable.withAll(actual).size(), actual.size());
    }
}
