# Proposal: Enriching the Profile Model

**Status:** Draft / for discussion
**Scope:** `legend-pure` (M3 metamodel, grammar, compiler) with coordinated changes in `legend-engine` (grammar, protocol, compiler) and `legend-studio`
**Author:** —
**Date:** 2026-08-11

---

## 1. Summary

Profiles today are little more than a namespace for a list of stereotype names and a list of tag
names. Any stereotype can be applied to any element, any number of times, in any combination. Every
rule beyond that is hard-coded in the compiler for one specific platform profile.

This document proposes three additions to the `Profile` / `Stereotype` / `Tag` model, all enforced
by the compiler and all opt-in — a profile that declares none of them behaves exactly as it does
today:

| # | Feature | One-line statement |
|---|---------|--------------------|
| **F1** | **Applicability** | An annotation may declare the element types it can be applied to. |
| **F2** | **Occurrence limits** | A tag may declare how many times it can appear on one element. |
| **F3** | **Incompatibility** | A profile may declare that certain annotations may not co-occur. |

For F1 and F2 the design space is small, and this document presents three grammar shapes each and
recommends one. For F3 the design space is large; five options are worked through in §6 with a
recommendation of a layered approach (§6.4).

Every option here is **declarative data on the profile, with semantics fixed and implemented by the
compiler**.
That is forced: enforcement is in the compiler, and the compiler cannot evaluate Pure code (see
principle P2). A general "constraint expression" mechanism is therefore not on the table, now or
later — which means the declarative vocabulary chosen has to be sufficient on its own.

A useful framing: **all three features generalise rules the compiler already hard-codes.**

- `AccessLevelValidator` ([`AccessLevelValidator.java:62-88`](../../legend-pure-core/legend-pure-m3-core/src/main/java/org/finos/legend/pure/m3/compiler/validation/validator/AccessLevelValidator.java))
  enforces "at most one `meta::pure::profiles::access` stereotype" — F3.
- The same validator's `validateExplicitAccessLevel` enforces "only classes and functions may have an
  access level" — F1.
- `MilestoningClassValidator` ([`MilestoningClassValidator.java:59`](../../legend-pure-core/legend-pure-m3-core/src/main/java/org/finos/legend/pure/m3/compiler/validation/validator/MilestoningClassValidator.java))
  enforces "a Type may only have one Temporal Stereotype" — F3 again, plus F1 (temporal stereotypes
  are meaningless anywhere but on a `Class`, but nothing rejects them elsewhere today).

If the features below had existed, two of those three validators would have been declarations in
`access.pure` and `milestoning.pure` instead of hard-coded compiler rules.

---

## 2. Current state

### 2.1 Metamodel

Bootstrap definitions live in [`platform/pure/grammar/m3.pure`](../../legend-pure-core/legend-pure-m3-core/src/main/resources/platform/pure/grammar/m3.pure)
(`Profile` at line 2574, `Annotation` 2606, `Stereotype` 2650, `Tag` 2663, `TaggedValue` 2676,
`AnnotatedElement` 672). In ordinary Pure syntax they amount to:

```pure
Class meta::pure::metamodel::extension::Profile extends PackageableElement
{
    p_stereotypes : Stereotype[*];
    p_tags        : Tag[*];
}

Class meta::pure::metamodel::extension::Annotation
{
    profile       : Profile[1];
    value         : String[1];        // the annotation's name
    modelElements : AnnotatedElement[*];
}

Class meta::pure::metamodel::extension::Stereotype extends Annotation {}
Class meta::pure::metamodel::extension::Tag extends Annotation {}

Class meta::pure::metamodel::extension::TaggedValue
{
    tag   : Tag[1];
    value : String[1];
}
```

Relevant hierarchy facts (confirmed in the generated interfaces under `target/generated-sources`):

- `AnnotatedElement extends ElementWithStereotypes, ElementWithTaggedValues`
- `ModelElement extends AnnotatedElement`, and `PackageableElement extends ModelElement`
- `Profile extends PackageableElement` — **a profile is itself an annotated element**, and the
  platform already exploits this: `Profile <<PCT.testQualifierProfile>> meta::pure::test::pct::PCTCoreQualifier`
  in [`pct_core.pure:23`](../../legend-pure-core/legend-pure-m3-core/src/main/resources/platform/pure/essential/tests/pct_core.pure),
  read back by `PCTTools.isPCTQualifierProfile`.
- `Annotation extends Any` only — stereotypes and tags are *not* annotated elements, so you cannot
  today put a stereotype, a tagged value, or even documentation on a stereotype.
- Not every annotated thing is a `PackageableElement`: tree-path route nodes and relation column
  specs implement `ElementWithStereotypes` directly.

### 2.2 Grammar

[`M3CoreParser.g4:257-268`](../../legend-pure-core/legend-pure-m3-core/src/main/antlr4/org/finos/legend/pure/m3/serialization/grammar/m3parser/antlr/core/M3CoreParser.g4):

```antlr
profile: documentation? PROFILE stereotypes? taggedValues? qualifiedName
         CURLY_BRACKET_OPEN
            stereotypeDefinitions?
            tagDefinitions?
         CURLY_BRACKET_CLOSE
;
stereotypeDefinitions: (STEREOTYPES COLON BRACKET_OPEN identifier (COMMA identifier)* BRACKET_CLOSE END_LINE);
tagDefinitions:        (TAGS COLON BRACKET_OPEN identifier (COMMA identifier)* BRACKET_CLOSE END_LINE);
```

Two references to annotations already exist as first-class grammar and are unambiguous — they are
used in expression position and map directly onto `ImportStub` id-paths
(`ImportStub.STEREOTYPE_STUB_DELIM = '@'`, `TAG_STUB_DELIM = '%'`):

```antlr
stereotypeReference: qualifiedName AT identifier      // my::Prof@public
tagReference:        qualifiedName PERCENT identifier // my::Prof%doc
```

`legend-engine` has its own copy of this grammar
([`DomainParserGrammar.g4:102-112`](../../../legend-engine/legend-engine-core/legend-engine-core-base/legend-engine-core-language-pure/legend-engine-language-pure-grammar/src/main/antlr4/org/finos/legend/engine/language/pure/grammar/from/antlr4/domain/DomainParserGrammar.g4))
which differs slightly: it allows the two clauses in any order and repeated. Any grammar change has
to be made in both places.

### 2.3 Compilation and validation today

| Stage | Code | What it does for profiles |
|-------|------|---------------------------|
| Parse | `AntlrContextToM3CoreInstance.profile()` (lines 3432-3483) | Builds `ProfileInstance`, `StereotypeInstance`, `TagInstance` |
| Parse | `AntlrContextToM3CoreInstance.stereotype()` (line 3245) | Annotation *uses* become `ImportStub`s (`path@name` / `path%name`) |
| Post-process | `AnnotatedElementProcessor` | Resolves the stubs and back-links `Annotation.modelElements` |
| Validate | `ProfileValidator` | Names unique within `p_stereotypes` and within `p_tags` |
| Validate | `ElementWithStereotypesValidator`, `ElementWithTaggedValueValidator`, `TaggedValueValidator` | Force stub resolution |
| Validate | `AccessLevelValidator`, `MilestoningClassValidator` | The hard-coded rules described in §1 |
| Unbind | `ProfileUnloaderWalk` | On profile change, re-walks **every model element** of every one of its annotations |

Validators are registered in `M3AntlrParser.getValidators()` (lines 563-588). `Matcher` keys runners
by type in a **multimap** and dispatches down the generalisation resolution order, so several
runners may be registered against the same type (`ElementWithStereotypesValidator` and
`AccessLevelValidator` already both register against `M3Paths.ElementWithStereotypes`).

### 2.4 There are two compilers

This is the single most important implementation constraint. `legend-engine` does **not** run
legend-pure's `m3.compiler.validation` package at all — no reference to it exists in
`legend-engine-language-pure-compiler`. It builds the Pure graph itself from protocol objects in
[`ProfileCompilerExtension.java`](../../../legend-engine/legend-engine-core/legend-engine-core-base/legend-engine-core-language-pure/legend-engine-language-pure-compiler/src/main/java/org/finos/legend/engine/language/pure/compiler/toPureGraph/ProfileCompilerExtension.java):

```java
return targetProfile._p_stereotypes(ListIterate.collect(profile.stereotypes, st -> newStereotype(...)))
                    ._p_tags(ListIterate.collect(profile.tags, t -> newTag(...)));
```

So "enforced by the compiler" means enforced **twice** unless the check is written as a
graph-level routine both compilers can call. §9.3 proposes exactly that.

---

## 3. Design principles

These principles are used throughout to choose between options; they are worth agreeing on before
the grammar bikeshedding.

**P1 — Opt-in.** Absence of a declaration means today's behaviour (unrestricted). No existing model
changes meaning.

**P2 — Declarative, not programmatic. This is a constraint, not a preference.** Enforcement happens
in the compiler, and **the compiler cannot execute code**. The compiler is a layer *below* the
execution engines — in the current implementation `legend-pure-m3-core` depends on `m4` and on
parsing and collections libraries, and both engines sit above it and depend on it; nothing about
that ordering is specific to the compiler being written in Java today. Class constraints show the
boundary exactly: the constraint validator type-checks that a constraint expression is `Boolean[1]`
and stops there; evaluation happens at runtime in an engine. So every rule proposed here must be
expressible as **data on the profile**, with the semantics of that data fixed and implemented by the
compiler. Anything requiring a user-supplied expression to be evaluated is out of scope permanently,
not deferred.

**P3 — Locality.** *Every constraint must be discoverable from the annotations actually present on
the element being validated.* This is not an aesthetic preference; it is what makes incremental
compilation sound. Validation of element `E` gathers constraints only from the profiles of the
annotations `E` uses; and when a profile source changes, `ProfileUnloaderWalk` already re-walks
`Annotation.modelElements` for that profile's annotations, so exactly the affected elements are
re-validated. Any feature that breaks locality (see §5.4 and §6.5 on *lower* bounds) needs a
whole-model validation pass, which is a different and much more expensive architecture.

**P4 — One implementation of the semantics.** Shared between the legend-pure compiler and the
legend-engine compiler (§9.3).

**P5 — Errors name both ends.** The message should identify the element, the annotation, the
declaring profile, and carry source information for the *use*, mentioning the source information of
the *declaration* — the style `ProfileValidator` already uses:
`"There is already a stereotype named 'abc' defined in test::BadProfile (at /test/profileTest.pure line:3 column:20)"`.

**P6 — Additive grammar.** Existing profile declarations parse unchanged, and the plain form stays
as terse as it is today.

---

## 4. Feature 1 — Applicability (`appliesTo`)

### 4.1 Semantics

Each annotation has an **effective applicable-type list**:

1. the annotation's own list, if it declares one; otherwise
2. the declaring profile's list, if it declares one; otherwise
3. unrestricted.

An element carrying the annotation must be an instance of at least one type in that list
(`Instance.instanceOf`, so generalisation is honoured: `appliesTo: [Function]` accepts both
`ConcreteFunctionDefinition` and `NativeFunction`). Otherwise: compilation error.

Note what that test does *not* say: nothing is required of the listed type itself, only of the
element. `Function` is a legitimate entry even though a `Function` cannot itself carry an annotation
— see §4.3.

### 4.2 What is a "type" here — two readings

| | Option | Pros | Cons |
|---|---|---|---|
| **1-i** | **Metamodel type references** (`Class`, `Property`, `Enumeration`, `ConcreteFunctionDefinition`, `Measure`, `Mapping`, `Database`, …) | Open-ended: DSL element types work with no further change; uses `instanceOf`, which modelers already understand; reuses `ImportStub` resolution as-is | Introduces a source dependency from profiles to types (new for profiles); `Class` is `Class<T>`, so the grammar must accept a bare raw type; invites the M1/M3 confusion below |
| **1-ii** | **A closed `ElementKind` enumeration** (`ElementKind.Class`, `ElementKind.Property`, …) | Trivially renderable in Studio as a checkbox list; no new dependency edge; no bootstrap ordering questions | Not extensible — every new DSL element type needs a new enum value in the platform; loses subtype semantics (`Function` covering both function kinds) |

**Recommendation: 1-i.** Reference real types. The only well-formedness check on the list is that
each entry resolves to a `Type` — `appliesTo: [my::someFunction]` is an error, `appliesTo: [Any]` is
a legal way to spell "unrestricted".

Two notes to put in the user documentation:

- **The list names metamodel types, not domain types.** `appliesTo: [Class]` means "class
  *definitions*", not "instances of some class". This will be misread at least once.
- **`Enumeration` is not a `Class`** in the M3 hierarchy, so `appliesTo: [Class]` does not cover
  enumerations. That is the correct behaviour but is worth stating.

Bootstrap ordering is fine: `m3.pure` is loaded before `access.pure` / `milestoning.pure`, so
platform profiles can reference M3 types.

### 4.3 Why the list is *not* restricted to annotatable types

It is tempting to add one more check — that every listed type is a subtype of
`ElementWithStereotypes` (for stereotypes) or `ElementWithTaggedValues` (for tags) — on the grounds
that anything else makes the annotation unusable. **That check is wrong**, and it is worth recording
why, because it will be proposed again.

Pure has multiple inheritance, so `T` not being annotated-element-derived says nothing about its
subtypes. This is not a corner case; it is the shape of the M3 hierarchy at exactly the points a
modeler will want to name:

| Type | Is an `AnnotatedElement`? | Annotatable subtypes | Non-annotatable subtypes |
|---|---|---|---|
| `Function<T>` | **No** — `extends Referenceable` | `ConcreteFunctionDefinition`, `NativeFunction` (via `PackageableFunction → PackageableElement → ModelElement`) | `LambdaFunction` |
| `Type` | **No** — `extends Any` | `Class`, `Enumeration`, `PrimitiveType`, `Measure` (via `PackageableElement`) | `Unit` |
| `DataType` | **No** — `extends Type` | `Enumeration`, `PrimitiveType`, `Measure` | `Unit` |

So the check would reject `appliesTo: [Function]` and `appliesTo: [Type]` — two of the most natural
things anyone would write, and the first is this document's own worked example. `AbstractProperty`
makes the same point from the other direction: it reaches `AnnotatedElement` through `ModelElement`,
not through `Function`, so which of a type's several supertypes carries annotatability is not
something a modeler should have to know.

The general argument is stronger than the counterexamples, and it is **P3** again: *the subtype set
is open*. A repository compiled later can declare `U extends T, AnnotatedElement`, so "no annotatable
subtype of `T` exists" is not a stable property of the profile's own compilation unit. Computing it
over the currently-known graph would make a profile's validity depend on which repositories happen
to have been compiled, which is exactly what the locality principle exists to prevent — and would
misfire as a warning for the same reason.

The cost of dropping the check is a declaration that can never match. That is self-punishing and
locally diagnosed: every attempt to use the annotation fails with a message naming the element's
actual type against the declared list. No global reasoning required.

### 4.4 Grammar options

All three examples express the same thing: the profile applies to classes and properties by default,
but stereotype `internal` applies only to functions.

**Option 1A — inline modifier phrase** *(recommended)*

```pure
Profile my::Prof
{
    appliesTo: [Class, Property];
    stereotypes: [audited, internal appliesTo [ConcreteFunctionDefinition]];
    tags: [owner];
}
```

**Option 1B — per-annotation body block**

```pure
Profile my::Prof
{
    appliesTo: [Class, Property];
    stereotypes:
    [
        audited,
        internal
        {
            appliesTo: [ConcreteFunctionDefinition];
        }
    ];
    tags: [owner];
}
```

**Option 1C — separate override clauses**

```pure
Profile my::Prof
{
    stereotypes: [audited, internal];
    tags: [owner];

    appliesTo: [Class, Property];
    appliesTo internal: [ConcreteFunctionDefinition];
}
```

| | Verbosity (common case) | Extensibility | Diff/merge behaviour | Studio round-trip |
|---|---|---|---|---|
| **1A** | Lowest — one phrase inline | Good up to ~2 modifiers per annotation; a third gets long | Annotation list lines change when a modifier changes | Simple: modifiers hang off the stereotype object |
| **1B** | Highest — braces even for one property | Best — every future per-annotation feature slots in | Cleanest: each annotation owns its own lines | Simple |
| **1C** | Middle, but repeats every annotation name | Good — new clause kinds are new statements | Best: annotation list never changes | Needs name→override matching, easy to get out of sync |

**Recommendation: 1A**, because with F2 expressed as a multiplicity (§5.3) an annotation needs at
most two inline modifiers and stays on one line. If we expect a steady stream of future
per-annotation attributes, 1B is the more honest choice; 1A can be migrated into 1B later
(1A becomes sugar for a single-property body) without breaking models.

### 4.5 Worked examples

```pure
Profile meta::pure::profiles::temporal
{
    appliesTo: [Class];
    stereotypes: [bitemporal, businesstemporal, processingtemporal];
}

Profile meta::pure::profiles::access
{
    appliesTo: [Class, Function];
    stereotypes: [public, protected, private, externalizable];
}
```

The second is not quite the whole rule that `AccessLevelValidator` enforces —
`externalizable` additionally requires a *non-property concrete* function with a package, primitive
parameter types and a primitive return type. Applicability lists express the element-type part; the
rest stays a hard-coded compiler rule. Worth stating explicitly in the design: **`appliesTo`
replaces "wrong kind of element" checks, not arbitrary well-formedness checks.**

---

## 5. Feature 2 — Occurrence limits

### 5.1 Semantics

For a tag `T` with a declared maximum `n`, the number of `TaggedValue`s on one element whose `tag`
is `T` must be ≤ `n`. No declaration means no limit. Counting is per element and does not traverse
into owned elements (a class's properties have their own tagged values, counted separately) and
there is no inheritance of annotations, so no supertype interaction.

### 5.2 Options

| | Option | Expresses | Pros | Cons |
|---|---|---|---|---|
| **2A** | `Boolean` — `allowsMultiple` / `single` | "one" vs "many" | Simplest metamodel and UI (a checkbox) | Cannot say "at most 2"; a later move to N is a metamodel change |
| **2B** | `Integer maxOccurrences` | "at most N" | Direct, obvious | New attribute concept; needs its own validation (`> 0`); no natural syntax hook |
| **2C** | `Multiplicity` — `doc[0..1]` | "at most N" *and* "at least N" | Reuses Pure's own concept and syntax verbatim; zero new keywords; reads instantly to any Pure modeler; naturally extends to a lower bound later | Grammar admits `[1]` / `[1..*]`, whose semantics are a separate feature (§5.4); `Multiplicity` in the metamodel is heavier than an `Integer` |

**Recommendation: 2C for the syntax, 2B for the storage.** Parse `[0..1]`, `[0..3]`, `[*]` with the
existing `multiplicity` rule ([`M3CoreParser.g4:498-503`](../../legend-pure-core/legend-pure-m3-core/src/main/antlr4/org/finos/legend/pure/m3/serialization/grammar/m3parser/antlr/core/M3CoreParser.g4)),
store the upper bound as `Annotation.maxOccurrences : Integer[0..1]`. This gets the compact,
familiar syntax without committing the metamodel to a `Multiplicity` reference (which would also
admit multiplicity *parameters*, `multiplicityArgument: identifier`, which are meaningless here).

```pure
Profile meta::pure::profiles::doc
{
    stereotypes: [deprecated];
    tags: [doc[0..1], todo];
}
```

### 5.3 Should stereotypes get occurrence limits too?

Recommended: **accept the same syntax on stereotypes** for uniformity, defaulting to unlimited.

Separately worth deciding (Q3): repeating a stereotype on an element is meaningless today and
nothing rejects it (`<<access.public, access.public>>` parses, and only `AccessLevelValidator`'s
count trips on it). A global "duplicate stereotype" error would be a behaviour change, but a small
and defensible one; making it a warning first is the safer route.

### 5.4 Lower bounds are a different feature — and break locality

`doc[1]` would mean "every element must carry exactly one `doc` tag". That is a *required
annotation*, and it violates principle **P3**: it cannot be checked by looking at the annotations an
element has, because the violating element has none. Enforcing it means iterating every element in
the model (against which profile's rules? all of them?) on every compile, and re-validating the
whole model whenever a profile changes.

**Recommendation:** accept the multiplicity syntax but **reject a non-zero lower bound** in this
phase, with an explicit error (`"Tag 'doc' of profile meta::pure::profiles::doc declares a minimum
occurrence; only upper bounds are supported"`). The syntax is then reserved, and required-annotation
support can be designed later with the whole-model pass it actually needs.

---

## 6. Feature 3 — Incompatibility

### 6.1 What has to be expressible

| Id | Requirement | Have a use case? |
|----|-------------|------------------|
| **R1** | All stereotypes of a profile mutually exclusive | Yes — `meta::pure::profiles::access`, `meta::pure::profiles::temporal` |
| **R2** | A proper subset of a profile's stereotypes mutually exclusive | Expected |
| **R3** | At most *N* of a set | Speculative |
| **R4** | Annotation of profile Q incompatible with annotation of profile R | Speculative |
| **R5** | The same for tags | Speculative |
| **R6** | Mixed — a stereotype incompatible with a tag | Speculative |

Plus two semantic requirements stated in the brief:

- **Symmetry** — if A excludes B then B excludes A.
- **Repetition is not a violation** — `<<access.public, access.public>>` should not count as two
  access stereotypes. (Today it does. This is a deliberate relaxation; §5.3 covers repeats.)

### 6.2 The unifying observation

Every requirement above is the same shape:

> **at most *N* of the annotations in set *S* may appear on one element**

Pairwise incompatibility "A excludes B" is `N = 1, S = {A, B}`. "All stereotypes of the profile are
mutually exclusive" is `N = 1, S = the profile's stereotypes`. Symmetry is automatic, because a set
has no direction. R3 is just `N > 1`.

Note the difference in counting from F2 and keep it in the spec: **F2 counts occurrences of one
annotation; F3 counts distinct annotations in a set.** That is exactly what makes repetition
harmless for F3 while still being controllable via F2.

### 6.3 Options

#### Option 3A — pairwise `incompatibleWith` on each annotation

```pure
Profile meta::pure::profiles::access
{
    stereotypes:
    [
        public       incompatibleWith [protected, private, externalizable],
        protected    incompatibleWith [private, externalizable],
        private      incompatibleWith [externalizable],
        externalizable
    ];
}
```

- **+** Mirrors the natural-language statement "if A then not B" one-to-one.
- **+** Cross-profile and cross-kind fall out for free (`incompatibleWith [other::Prof@x, other::Prof%t]`).
- **+** No new profile-level clause; the declaration sits next to the annotation it constrains.
- **−** Quadratic in the common case. Four stereotypes need six pairs; the reader has to
  mentally close the relation to see it is a clique.
- **−** Silent under-constraint: forget one pair and you get a *partial* clique with no diagnostic.
- **−** Cannot express R3 at all.
- **−** To know what constrains stereotype `private` you must read every other annotation's list.

#### Option 3B — exclusion sets at profile level *(recommended core)*

```pure
Profile meta::pure::profiles::access
{
    exclusive stereotypes: [public, protected, private, externalizable];  // R1 shorthand
}

Profile my::Prof
{
    stereotypes: [a, b, c, d];
    tags: [t1, t2];
    exclusive: [a, b];                                   // R2
    exclusive: [c, other::Prof@x];                       // R4
    exclusive: [t1, t2];                                 // R5
    exclusive: [d, t1];                                  // R6
}
```

- **+** The dominant case (R1) is one word.
- **+** Symmetry is structural, not derived.
- **+** One place to read; one place to fix.
- **+** Extends to R3 by adding a bound (Option 3C).
- **−** Two syntactic forms (modifier + clause) for one concept.
- **−** The `exclusive stereotypes:` shorthand is *dynamic*: a stereotype added to the profile later
  silently joins the exclusive set. Usually what you want (it is what `access` wants); occasionally
  a surprise. The remedy is to switch that profile to the explicit set form; worth documenting.

#### Option 3C — named groups with a bound

```pure
Profile my::Prof
{
    stereotypes: [ready, inProgress, blocked, urgent, deprecated];
    groups:
    [
        status[0..1]: [ready, inProgress, blocked],
        priority[0..2]: [urgent, deprecated]
    ];
}
```

- **+** Covers R1–R6 including R3 with one uniform concept.
- **+** **Best error messages**: `"... has 2 stereotypes from group 'status' ..."` beats
  `"... has 2 mutually exclusive stereotypes ..."`.
- **+** **Best tooling story**: a named group with an upper bound of 1 is precisely a radio group /
  single-select dropdown in Studio. Pairwise incompatibility cannot be rendered as anything.
- **+** Unifies with F2 — a tag limit is a group of one, counted by occurrence.
- **−** A new modeling concept ("group") to teach, on top of profile / stereotype / tag.
- **−** Reuses multiplicity syntax, so it inherits the lower-bound question (§5.4) more visibly.
- **−** More grammar and metamodel than the known use cases require.

#### Option 3D — profile-level modifier only

```pure
Profile <<meta::pure::profiles::annotations.exclusiveStereotypes>> meta::pure::profiles::access
{
    stereotypes: [public, protected, private, externalizable];
}
```
or with a keyword: `Profile exclusive meta::pure::profiles::access { ... }`.

- **+** Absolutely minimal; covers R1, which is most of the value.
- **+** In the stereotype spelling shown, **zero grammar change** — see 3E.
- **−** Covers nothing else. R2–R6 need a second mechanism anyway, and then there are two.

#### Option 3E — meta-annotations on annotations

Make `Annotation` an `AnnotatedElement` and use a platform profile to mark annotations:

```pure
Profile my::Prof
{
    stereotypes: [<<annotations.exclusive>> a, <<annotations.exclusive>> b, c];
}
```

with the rule "all annotations of a profile marked `exclusive` are mutually exclusive".

- **+** Uses the annotation mechanism to describe annotations, which is elegant, and there is
  precedent at the profile level already (`Profile <<PCT.testQualifierProfile>> …`).
- **+** Would independently unlock **documentation on stereotypes and tags**, which is not possible
  today and is arguably a bigger win than anything in this document.
- **−** Only one exclusive set per profile; no R2 with two independent sets, no R3, no R4/R6.
- **−** Semantics live in a validator keyed off magic names rather than in the grammar; discoverability is poor.
- **−** Making `Annotation` annotatable is a real metamodel change (M3 bootstrap, protocol, Studio)
  and creates a chicken-and-egg question for the platform profile that describes annotations.

**Even if 3E is rejected as the mechanism, "annotations can be annotated / documented" is worth
tracking as a separate proposal.**

#### Not an option — general constraint expressions

A profile-level predicate, e.g.
`constraints: [ $element.stereotypes->filter(s | $s.profile == my::Prof)->size() <= 1 ];`, would
express every requirement above and more. **It is not implementable**, for the reason given in
**P2**: enforcement happens in the compiler, and the compiler cannot evaluate Pure code. Class
constraints are the precedent — the constraint validator only type-checks that the expression is
`Boolean[1]`; the expression itself is evaluated at *runtime* by an execution engine. This is a
property of the layering, not of any particular compiler implementation: it does not become
available in a future release, or in a compiler written in some other language, without inverting
the relationship between the compiler and the engines that run on top of it.

This is a hard boundary on the whole feature set, not just on F3: **the declarative vocabulary
chosen below is the entire vocabulary.** There is no escape hatch behind it, which is the main
argument for picking a mechanism that extends cleanly (§6.4) rather than the cheapest one that
covers today's cases.

#### Coverage summary

| | R1 all | R2 subset | R3 at most N | R4 cross-profile | R5 tags | R6 mixed | New concepts | Impl cost |
|---|---|---|---|---|---|---|---|---|
| **3A** pairwise | ✔ but O(n²) | ✔ | ✘ | ✔ | ✔ | ✔ | 1 (relation) | Low |
| **3B** exclusion sets | ✔ (1 word) | ✔ | via 3C | ✔ | ✔ | ✔ | 1 (set) | Low–medium |
| **3C** named groups | ✔ (1 word) | ✔ | ✔ | ✔ | ✔ | ✔ | 2 (group, bound) | Medium |
| **3D** profile modifier | ✔ | ✘ | ✘ | ✘ | kind-wide only | ✘ | 0–1 | Very low |
| **3E** meta-annotations | ✔ | partly | ✘ | ✘ | ✔ | ✘ | 1 (annotatable annotations) | Medium (metamodel) |

### 6.4 Recommendation — a layered package

Adopt **3B now, designed so that 3C is a strictly compatible extension later**:

- **Tier 1 (sugar, R1):** `exclusive` modifier on the `stereotypes:` / `tags:` clause.
- **Tier 2 (R2, R4, R5, R6):** repeatable `exclusive: [ … ];` clause — at most one of the listed
  annotations.
- **Tier 3 (R3, better messages, deferred):** `groups: [ name[0..n]: [ … ] ];`. Tier 2 is exactly a
  Tier 3 group with no name and a bound of 1, so adding Tier 3 later adds syntax without changing
  any existing meaning.

Rationale: Tier 1 covers every case we can actually name today at minimum cost; Tier 2 covers every
case listed as speculative except R3; Tier 3 is where the sole missing requirement and the best
tooling/error story live, and there is no use case yet to pay for it.

Because there is no expression-based escape hatch (§6.3), the deferral of Tier 3 is only acceptable
*because* it is a strictly compatible extension: if R3 ever turns up, `exclusive: [a, b]` keeps
meaning what it means and `groups:` is added beside it. This rules out picking 3A or 3D on cost
grounds — under either, supporting R3 later means a second, unrelated mechanism rather than a
generalisation of the first.

### 6.5 Semantics to pin down

1. **Counting.** Distinct annotations, not occurrences (§6.2). `<<a, a>>` contributes 1 to any set
   containing `a`.
2. **Symmetry.** Structural in 3B/3C. Under 3A the compiler must close the relation, and closure
   across profiles means profile R's declaration changes profile Q's meaning — one more reason to
   prefer sets.
3. **The ownership rule.** *Every exclusion set must reference at least one annotation defined by
   the declaring profile.* This is what preserves **P3**: an element can only violate "at most 1 of
   S" by using ≥ 2 members of S, so the constraint is reachable from the profile of *any* member the
   element uses — provided the declaring profile owns a member. A set declared in a profile owning
   none of its members would be invisible to element validation and, worse, would not trigger
   re-validation when edited, since `ProfileUnloaderWalk` walks the declaring profile's own
   annotations' `modelElements`. Make it a compile error on the profile.
4. **Degenerate sets.** `|S| < 2`, or bound ≥ `|S|`: harmless no-ops; warn.
5. **Overlapping sets.** Allowed; each evaluated independently; report the first violation with
   deterministic ordering (source order) so error messages are stable.
6. **No lower bounds** — see §5.4. "At least one of S" has the same locality problem.
7. **No inheritance.** Annotations are not inherited; the milestoning hierarchy rules
   ("temporal stereotypes must be applied at all levels") are a genuinely different, hierarchy-aware
   rule and stay in `MilestoningClassValidator`.

---

## 7. The recommended grammar, in full

### 7.1 Examples

```pure
Profile meta::pure::profiles::access
{
    appliesTo: [Class, Function];
    exclusive stereotypes: [public, protected, private, externalizable];
}

Profile meta::pure::profiles::temporal
{
    appliesTo: [Class];
    exclusive stereotypes: [bitemporal, businesstemporal, processingtemporal];
}

Profile meta::pure::profiles::doc
{
    stereotypes: [deprecated];
    tags: [doc[0..1], todo];
}

Profile my::Prof
{
    appliesTo: [Class, Property];
    stereotypes:
    [
        audited,
        internal appliesTo [ConcreteFunctionDefinition],
        draft,
        published
    ];
    tags: [owner[0..1], reviewer];
    exclusive: [draft, published];
    exclusive: [audited, other::Governance@unaudited];
}
```

### 7.2 Proposed ANTLR (legend-pure; mirror in legend-engine)

```antlr
profile: documentation? PROFILE stereotypes? taggedValues? qualifiedName
         CURLY_BRACKET_OPEN
            profileElement*
         CURLY_BRACKET_CLOSE
;

profileElement: appliesToDefinition
              | stereotypeDefinitions
              | tagDefinitions
              | exclusiveDefinition
;

appliesToDefinition: APPLIES_TO COLON typeList END_LINE
;

stereotypeDefinitions: EXCLUSIVE? STEREOTYPES COLON
                       BRACKET_OPEN annotationDefinition (COMMA annotationDefinition)* BRACKET_CLOSE END_LINE
;

tagDefinitions:        EXCLUSIVE? TAGS COLON
                       BRACKET_OPEN annotationDefinition (COMMA annotationDefinition)* BRACKET_CLOSE END_LINE
;

annotationDefinition:  identifier multiplicity? (APPLIES_TO typeList)?
;

exclusiveDefinition:   EXCLUSIVE COLON
                       BRACKET_OPEN annotationReference (COMMA annotationReference)* BRACKET_CLOSE END_LINE
;

annotationReference:   identifier | stereotypeReference | tagReference
;

typeList:              BRACKET_OPEN qualifiedName (COMMA qualifiedName)* BRACKET_CLOSE
;
```

New lexer tokens: `APPLIES_TO: 'appliesTo';` and `EXCLUSIVE: 'exclusive';`.

Notes and gotchas:

- **New keywords must be added to the `identifier` rule** ([`M3CoreParser.g4:3`](../../legend-pure-core/legend-pure-m3-core/src/main/antlr4/org/finos/legend/pure/m3/serialization/grammar/m3parser/antlr/core/M3CoreParser.g4)),
  which already lists `CLASS | FUNCTION | PROFILE | … | STEREOTYPES | TAGS | …` for exactly this
  reason. Without it, any existing model with a property or class named `exclusive` or `appliesTo`
  stops compiling. Same in the engine grammar. **This is the one way this proposal could break
  existing models, and it is avoidable.**
- `annotationReference` as a bare `identifier` resolves within the declaring profile: stereotypes
  first, then tags. If the profile defines both a stereotype and a tag with that name, it is
  **ambiguous → error**, fixable with the explicit `my::Prof@name` / `my::Prof%name` form. (Nothing
  today prevents a profile from having a same-named stereotype and tag, and this proposal should not
  start.)
- Changing `stereotypeDefinitions? tagDefinitions?` to `profileElement*` makes the two clauses
  order-independent and repeatable — which **aligns legend-pure with legend-engine**, whose grammar
  already allows this. Strict superset, so backward compatible. Decide whether repeated clauses of
  the same kind merge (engine's current behaviour) or error (Q6); merging is the compatible choice.
- `EXCLUSIVE?` before `STEREOTYPES` and `EXCLUSIVE COLON` for the standalone clause are
  distinguishable with one token of lookahead.
- The `multiplicity` rule admits multiplicity *parameters* (`multiplicityArgument: identifier`);
  reject those in `annotationDefinition` at build time with a clear message.

---

## 8. Metamodel changes

```pure
Class meta::pure::metamodel::extension::Profile extends PackageableElement
{
    p_stereotypes         : Stereotype[*];
    p_tags                : Tag[*];
    applicableTypes       : Type[*];                 // NEW - F1
    annotationConstraints : AnnotationConstraint[*]; // NEW - F3
}

Class meta::pure::metamodel::extension::Annotation
{
    profile         : Profile[1];
    value           : String[1];
    modelElements   : AnnotatedElement[*];
    applicableTypes : Type[*];        // NEW - F1
    maxOccurrences  : Integer[0..1];  // NEW - F2
}

// NEW - F3. Tier 3 (§6.4) later adds: name : String[0..1]; and maxCount > 1.
Class meta::pure::metamodel::extension::AnnotationConstraint
{
    stereotypes : Stereotype[*];
    tags        : Tag[*];
    maxCount    : Integer[1];
}
```

Implementation facts that make this cheaper than it looks:

- **Reference resolution is free.** `M3ToJavaGenerator` (lines 97-108) maps property raw types to
  stub types: `Type`, `Class`, `Stereotype`, `Tag` are all already `ImportStub`. Declaring
  `applicableTypes : Type[*]` automatically generates `_applicableTypesCoreInstance()` and the
  `ImportStub` plumbing, exactly as `ElementWithStereotypes.stereotypes` works today. Same for
  `AnnotationConstraint.stereotypes` / `.tags`.
- If a single `annotations : Annotation[*]` property is preferred over the split
  `stereotypes`/`tags` pair, add one line — `StubDef.build("Annotation", "ImportStub")` — to that
  same table. The split version needs no generator change at all, and matches the `@`/`%` reference
  forms; the unified version reads better. (Q7.)
- **Naming.** Note `getSubstituteType` (line 2611) special-cases `p_stereotypes` / `p_tags`; new
  property names must not collide with inherited ones (`Profile` inherits `stereotypes` and
  `taggedValues` from `AnnotatedElement`, which is why the `p_` prefix exists). `applicableTypes` and
  `annotationConstraints` are collision-free.
- **The bootstrap cost is real.** These properties must be hand-written into
  `platform/pure/grammar/m3.pure` in raw M4 instance syntax — roughly 6 verbose lines per property,
  copy-adapted from the neighbouring definitions.

---

## 9. Compiler implementation

### 9.1 legend-pure

| Area | File | Change |
|---|---|---|
| Grammar | `M3CoreLexer.g4`, `M3CoreParser.g4` | §7.2, including the `identifier` rule |
| Bootstrap | `platform/pure/grammar/m3.pure` | New properties + `AnnotationConstraint` class |
| Parse | `AntlrContextToM3CoreInstance.profile/buildStereoTypes/buildTags` (3432-3483) | Build the new values; create `ImportStub`s for type and annotation references |
| Post-process | **new** `ProfileProcessor` | Resolve the profile's stubs (there is no processor for `Profile` today) |
| Unbind | **new** `ProfileUnbind` | Reset those stubs on source change, alongside `ElementWithStereotypesUnbind` |
| Validate | `ProfileValidator` | Well-formedness of declarations: applicable-type entries resolve to types (§4.3 — nothing more); no lower bounds; `maxOccurrences > 0`; ownership rule (§6.5.3); degenerate-set warnings |
| Validate | **new** `AnnotationUsageValidator` | The three usage rules, registered in `M3AntlrParser.getValidators()` |
| Validate | `AccessLevelValidator` (62-88) | Delete the `default:` branch once `access.pure` declares exclusivity; keep `validateExplicitAccessLevel` |
| Platform | `access.pure`, `milestoning.pure`, `documentation.pure` | See §10 — separately from the machinery |

**Dispatch nuance.** Stereotype rules must fire for things that are `ElementWithStereotypes` but not
`AnnotatedElement` (tree-path route nodes, relation column specs), and F3 sets can mix stereotypes
and tags — which only `AnnotatedElement` has both of. `Matcher` walks the whole generalisation
resolution order and runs every registered runner, so an `AnnotatedElement` instance matches runners
registered against `ElementWithStereotypes` *and* `ElementWithTaggedValues`. The clean arrangement is
one validator class registered twice with a mode:

- against `M3Paths.ElementWithStereotypes`: validate stereotypes, plus tagged values and mixed sets
  when the instance is also an `ElementWithTaggedValues`;
- against `M3Paths.ElementWithTaggedValues`: validate tagged values only when the instance is *not*
  an `ElementWithStereotypes` (otherwise the first registration already covered it).

**Incremental compilation** needs no new machinery: `ProfileUnloaderWalk` already re-walks every
model element of every annotation of a changed profile, which — given the ownership rule — is
exactly the set of elements whose validity can change.

### 9.2 legend-engine

| Area | File | Change |
|---|---|---|
| Grammar | `DomainParserGrammar.g4:102-112`, `DomainLexerGrammar.g4` | Mirror §7.2 |
| Parse | `DomainParseTreeWalker` | Populate the new protocol fields |
| Protocol | `m3/extension/Profile.java`, `ProfileStereotype.java`, `ProfileTag.java` | New optional fields. **These are already objects, not bare strings**, so this is additive and JSON-compatible in both directions |
| Compose | `DEPRECATED_PureGrammarComposerCore` | Emit the new clauses; round-trip test |
| Compile | `ProfileCompilerExtension.profileFirstPass` | Set the new properties on the Pure graph objects |
| Validate | new pass invoked from the compiler | Call the shared checker (§9.3) |

**Studio.** Studio's protocol models are separate TypeScript classes with explicit serialization
schemas; fields they do not know about are typically dropped on round-trip. Until Studio is updated,
**a user editing a profile in Studio could silently strip its new declarations.** This has to be
sequenced deliberately — it is the highest-risk item in the whole proposal and it is not in either
repository in this workspace.

### 9.3 Shared semantics (P4)

Write the checks as a graph-level utility in legend-pure that takes an annotated element (or its
stereotypes + tagged values) plus `ProcessorSupport`, and returns violations as data — for example
`org.finos.legend.pure.m3.compiler.validation.AnnotationConstraints`. legend-pure's validator turns
violations into `PureCompilationException`; legend-engine's compiler turns the same violations into
`EngineException` with its own `SourceInformation`. Legend-engine already depends on legend-pure's
M3 classes (`ProfileCompilerExtension` imports them directly), so this needs no new dependency —
only a public, `default`-safe API surface, per the API-stability rule in `CLAUDE.md`.

---

## 10. Compatibility and migration

- **Existing models are unaffected** (P1), with the single exception of the reserved-word hazard in
  §7.2, which the `identifier` rule fix removes.
- **Binary/PAR serialization** is property-driven and generic, so new properties flow through
  without per-feature work. PAR files are version-locked to the compiler that reads them, so there is
  no mixed-version concern.
- **Protocol JSON** gains optional fields on existing objects — old JSON reads fine (absent = today's
  behaviour), new JSON read by an old engine loses the fields (which is exactly the Studio risk
  above, and the reason to land engine support before advertising the feature).
- **Platform profile tightening should be a separate, announced change.** The machinery is
  behaviour-preserving; declaring `doc[0..1]` on `meta::pure::profiles::doc` is not — any model that
  currently attaches two `doc.doc` values stops compiling. Suggested sequencing:
  1. Land metamodel + grammar + validators (no platform profile changes). Nothing breaks.
  2. Declare `appliesTo` and `exclusive` on `access` and `temporal`; delete the corresponding
     hard-coded validator branches. Behaviour changes only in that *more* things are rejected — plus the deliberate
     relaxation that a repeated identical access stereotype is no longer an error.
  3. Consider `doc[0..1]`, announced separately, after surveying real models.

---

## 11. Error messages

House style (`PureCompilationException`, source info on the *use*, declaration location in the text):

```
Stereotype 'businesstemporal' of profile meta::pure::profiles::temporal may only be applied to
meta::pure::metamodel::type::Class; my::Fn__String_1_ is a
meta::pure::metamodel::function::ConcreteFunctionDefinition
(profile at /platform/pure/grammar/milestoning.pure line:18 column:9)

Tag 'doc' of profile meta::pure::profiles::doc may be used at most once on an element; my::Foo has 3
(profile at /platform/pure/documentation.pure line:15 column:9)

my::Foo has 2 mutually exclusive stereotypes of profile meta::pure::profiles::access: 'public' (line:4
column:12) and 'private' (line:4 column:20); at most 1 is allowed
```

With Tier 3 named groups the third becomes `... 2 stereotypes from group 'visibility' of profile ...`,
which is the concrete argument for eventually adopting 3C.

---

## 12. Test plan

| Layer | Location | Coverage |
|---|---|---|
| Profile well-formedness | `m3/tests/validation/TestProfileValidation.java` | Applicable-type entry that is not a type; lower bound rejected; `maxOccurrences` ≤ 0; ownership rule; ambiguous bare annotation reference; degenerate set warning |
| Grammar | `m3/tests/elements/profile/TestProfile.java` | Every clause, all orders, repeated clauses, plain profiles unchanged; new keywords still usable as identifiers |
| Usage | new `TestAnnotationApplicability` / `TestAnnotationOccurrence` / `TestAnnotationExclusivity` | Positive/negative per feature; profile-level vs annotation-level override; **subtype acceptance through a supertype that is not itself annotatable** — `appliesTo: [Function]` accepting both function kinds, `appliesTo: [Type]` accepting `Class` and `Enumeration` (§4.3); repetition not counted for F3 but counted for F2; cross-profile and mixed sets |
| Existing behaviour | `m3/tests/validation/TestAccess.java` | Update the multi-access-stereotype expectations when step 2 of §10 lands |
| Incremental | `m3/tests/incremental/profile/` | Edit/delete a referenced type; edit a profile's constraints and confirm dependent elements are re-validated; delete a cross-referenced profile |
| Engine | grammar round-trip + compiler tests | Parse → protocol → compose → parse fidelity; compiler rejects the same models legend-pure rejects |

---

## 13. Open questions

| # | Question | Recommendation |
|---|---|---|
| **Q1** | Does an annotation-level `appliesTo` **override** the profile-level list (as stated in the brief) or **intersect** with it? | Override, per the brief. Optionally lint when the annotation list is not a subset, since that is usually a mistake. |
| **Q2** | Lower bounds / required annotations? | Out of scope (§5.4); reserve the syntax, reject with a clear message. |
| **Q3** | Occurrence limits on stereotypes, and/or a global "duplicate stereotype" diagnostic? | Allow the syntax on stereotypes; make bare duplicates a warning, not an error, initially. |
| **Q4** | Degenerate declarations (bound ≥ set size, single-member exclusion set) — error, warning, or silent? | Warning. Note that the other "can never match" case, an applicable-type list nothing could satisfy, is deliberately *not* diagnosed — see §4.3. |
| **Q5** | Tier 3 named groups now, or later? | Later, but only as the compatible extension described in §6.4 — with no expression fallback, R3 has no other route. Doing it now is worth it if a Studio single-select UI is wanted in the same release. |
| **Q6** | Repeated `stereotypes:` clauses in one profile — merge or error? | Merge, matching legend-engine's existing behaviour. |
| **Q7** | `AnnotationConstraint.stereotypes`/`tags` split, or a unified `annotations : Annotation[*]`? | Unified reads better and costs one line in `M3ToJavaGenerator`; the split needs no generator change. Weak preference for unified. |
| **Q8** | Keyword spellings: `appliesTo` vs `applicableTo`; `exclusive` vs `mutuallyExclusive` vs `atMostOne`. | `appliesTo` / `exclusive` — shortest that still read as English. |
| **Q9** | Should there be a middle, clause-level `appliesTo` (`stereotypes appliesTo [Class]: [...]`) between profile-level and annotation-level? | No — two levels are enough; a third is easy to add later. |
| **Q10** | Should `appliesTo` also constrain where a *profile-level* stereotype may be used, i.e. does the profile's own list apply to tags as well as stereotypes? | Yes, both — as stated in the brief. |
