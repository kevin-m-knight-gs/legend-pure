# Pure M3 Compiled Graph Instances

There are four types of instances in the Pure M3 compiled graph:

* entities
* component instances
* packages
* primitive values

## Entities

Entities are named instances that are either top level in the model (such as Package, Root, or the primitive types) or
are in the package tree. Every entity is a PackageableElement with a unique package path terminating with the package
Root.

Every entity must have a non-empty name. An entity's name must be unique among all children of the entity's package (or
among top level instances, if the entity is one of those).

Every entity has source information, which identifies the file and location within the file where the entity is defined.
No two entities may have overlapping source information.

Examples of entities are classes, profiles, enumerations, and associations.

## Component Instances

A component instance does not have its own distinct individual existence, but instead exists as a component or part of
an entity. Each component instance is a component of exactly one entity, called its "containing" or "owning" entity.

The link between the component instance and its containing entity may be direct or indirect. For example, a property of
a class will be linked directly to the class, but the generic type of the property will be only indirectly linked.
Nonetheless, both the property and the generic type are component instances of the class.

While each component instance is owned by exactly one entity, many instances (both internal and external to the entity)
may have references to it. For example, a property of a class may appear in expressions in many different functions.

Component instances may or may not have source information. If a component instance is referenced externally to its
containing entity, it must have source information. If the component instance has a well defined location in source
code, it should have source information which reflects this. Otherwise, source information is optional. If a component
instance does have source information, it must be subsumed by the source information of its containing entity. Distinct
component instances may have overlapping source information, but in that case one must subsume the other.

Examples of component instances are properties, stereotypes, enum values, function expressions, and generic types.

## Packages

Some packages are entities in their own right (such as Root), but most are not.

Entity packages exist in their own right, whether they have children or not. They are subject to all the requirements of
entities in general.

Non-entity packages exist only when some entity is a child (directly or indirectly) of the package. They do not have
source information. However, they are subject to the same name requirements as entities.

## Primitive Values

Primitive values are instances of primitive types (String, Integer, Boolean, etc). Primitive values are never entities.
They have no source information and no substantive properties.
