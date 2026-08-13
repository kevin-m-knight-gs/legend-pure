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
| **F3** | **Incompatibility** | A profile may declare that certain annotations — or entire profiles — may not co-occur. |

For F1 and F2 the design space is small, and this document presents three grammar shapes each and
recommends one. For F3 the design space is large; five options for incompatibility between
*annotations* are worked through in §6.3 with a recommendation of a layered approach (§6.4), plus a
separate and orthogonal form for incompatibility between whole *profiles* in §6.7.

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

This proposal leaves both rules alone and **does not** reuse them for the annotation references it
introduces; §7.2 and §7.3 give the reasons. The *use*-site rules are the ones it takes after:

```antlr
stereotype:  qualifiedName DOT identifier                                            // <<my::Prof.public>>
taggedValue: qualifiedName DOT identifier EQUAL (MULTILINE_STRING | STRING (PLUS STRING)*)  // {my::Prof.doc = 'x'}
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
| **1-i** | **Metamodel type references** (`Class`, `Property`, `Enumeration`, `ConcreteFunctionDefinition`, `Measure`, `Mapping`, `Database`, …) | Open-ended: DSL element types work with no further change; uses `instanceOf`, which modelers already understand; reuses `ImportStub` resolution as-is | Introduces a source dependency from profiles to types (new for profiles); `Class` is `Class<T>`, so the grammar must accept a bare raw type |
| **1-ii** | **A closed `ElementKind` enumeration** (`ElementKind.Class`, `ElementKind.Property`, …) | Trivially renderable in Studio as a checkbox list; no new dependency edge; no bootstrap ordering questions | Not extensible — every new DSL element type needs a new enum value in the platform; loses subtype semantics (`Function` covering both function kinds) |

**Recommendation: 1-i.** Reference real types. The only well-formedness check on the list is that
each entry resolves to a `Type` — `appliesTo: [my::someFunction]` is an error, `appliesTo: [Any]` is
a legal way to spell "unrestricted".

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
| `Function<T>` | **No** — `extends Referenceable` | `ConcreteFunctionDefinition`, `NativeFunction` (via `PackageableFunction → PackageableElement → ModelElement`); `AbstractProperty` (via `ModelElement`); `Column` (via `AnnotatedElement` directly) | `LambdaFunction` |
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
    appliesTo: [Class, PackageableFunction];
    stereotypes:
    [
        public,
        protected,
        private,
        externalizable appliesTo [ConcreteFunctionDefinition]
    ];
}
```

`access` is the case that exercises the profile-level default *and* the annotation-level override:
three of its stereotypes apply to classes and functions, while `externalizable` applies only to
`ConcreteFunctionDefinition`.

**`AccessLevelValidator` states that rule in terms of a hierarchy that has since changed, and should
be rewritten against the current one** — a correction worth making on its own, before and
independently of anything proposed here:

- The `externalizable` branch tests `!(instance instanceof ConcreteFunctionDefinition) || instance
  instanceof AbstractProperty`. **The second half is dead.** `Property extends AbstractProperty` and
  `QualifiedProperty extends FunctionDefinition, AbstractProperty` — neither reaches
  `ConcreteFunctionDefinition`, so the first half already excludes them. The test reduces to
  `instanceof ConcreteFunctionDefinition`.
- The other three levels are tested as "a `Class` or a `Function`, but not an `AbstractProperty`".
  The intent was always classes and packageable functions; the subtraction was the only way to say
  that when `PackageableFunction` did not yet exist. It does now, so the test should be
  `instanceof Class || instanceof PackageableFunction`, which says the intended thing directly.

That rewrite is also what makes the declarations above exact rather than approximate: once the
validator says `Class || PackageableFunction`, `appliesTo: [Class, PackageableFunction]` is the same
rule moved from Java into the profile.

Neither example captures the whole of what the validator enforces: `externalizable` additionally
requires a function with a name and package, primitive parameter types and a primitive return type,
and no name conflict. Applicability lists express the element-type part; the rest stays a hard-coded
compiler rule. Worth stating explicitly in the design: **`appliesTo` replaces "wrong kind of element"
checks, not arbitrary well-formedness checks.**

### 4.6 Negative type constraints — `appliesTo: [Class, Function, !Property]`?

"A `Function` but not a property" is a real shape, and a subtraction is not always expressible as a
union of named types. Proposed semantics would be the obvious ones: an element must match **at least
one positive entry and no negative entry**.

The case that suggests it, though, argues against it on inspection. The subtraction in
`AccessLevelValidator` was never the intended rule — it was a workaround for a hierarchy that had no
name for "function that is a packageable element" — and the two spellings are not equivalent:

| Spelling | Admits |
|---|---|
| `[Class, Function, !AbstractProperty]` | `Class`, `ConcreteFunctionDefinition`, `NativeFunction`, **and `Column`** |
| `[Class, PackageableFunction]` | `Class`, `ConcreteFunctionDefinition`, `NativeFunction` |

They differ on exactly the type nobody was thinking about. `Column` — the relation DSL's column — is
declared `Column<U,V> extends AnnotatedElement, Function<Object>`: annotatable, a `Function`, not a
property, not packageable. It arrived long after the access rule was written and joined the
subtractive reading silently, which is not what anyone intended: an access level on a relation column
is meaningless.

So the general lesson is about **which direction you prefer silent drift to run**:

- **Subtraction is open.** "Every `Function` except properties" keeps meaning what it says as the
  hierarchy grows — and automatically picks up new annotatable `Function` subtypes, whether or not
  the annotation makes sense for them.
- **Positive enumeration is closed at the named types.** It picks up new *subtypes of what it names*
  (that is `instanceOf`, and it is what makes `[Function]` work at all), but never a new sibling
  branch. A new element kind that should carry the annotation has to be added deliberately.

A subtraction is the more robust spelling when the intent really is "all of K except X" and X is
stable. It is the more dangerous one when, as here, the subtraction was standing in for a positive
concept that the hierarchy had not yet grown.

#### Options

| | Option | Pros | Cons |
|---|---|---|---|
| **1-α** | **Positives only** (as specified in §4.1) | Monotone — adding an entry can only ever admit more elements; nothing to specify about precedence; renders as a checkbox list | Some intents need a type that does not exist yet in the hierarchy; if the hierarchy lacks the concept, the modeler must enumerate branches |
| **1-β** | **`!T` entries in the same list** | Compact; expresses the subtractive intent directly; additive to the grammar | Needs a spec for negative-only lists and for how a negative interacts with the profile→annotation override; non-monotone, so a modeler must reason about an element's whole ancestry, not just the branch named |
| **1-γ** | **A separate `doesNotApplyTo:` clause** | Precedence is explicit; a profile-level exclusion can be inherited by every annotation | Twice the grammar for the same power; two clauses to keep consistent |
| **1-δ** | **Reserve `!` in the grammar, reject it for now** | Keeps the syntax available without committing to semantics | A reserved-but-unusable syntax is its own kind of confusing |

**Recommendation: 1-α now.** The one concrete case that looked like it needed negation turns out to
be a stale workaround with a positive spelling that is strictly better, and the `Column` example
shows the choice between the two is a semantic decision about future subtypes rather than a syntax
convenience. There is currently no use case for negation that survives inspection. It is additive if
one appears — at which point the wrinkles below have to be settled, and they are easier to settle
against a real example than in the abstract.

If negation is adopted, three things need deciding:

1. **Negative-only lists.** Does `appliesTo: [!Property]` mean "anything but a property", or is at
   least one positive entry required? "Unrestricted minus the negatives" is the more useful reading
   and the one that makes a profile-level `appliesTo: [!Property]` worth writing.
2. **Override interaction.** §4.1 says an annotation's list replaces the profile's wholesale. With
   negation, is a negative-only annotation list still a wholesale replacement (dropping the profile's
   positives) or a refinement of the profile's list? Wholesale is consistent; refinement is what
   people will expect.
3. **Error messages.** "`my::Foo` is a `Property`, which `appliesTo` excludes" has to be
   distinguishable from "no positive entry matched", or the diagnostic sends the reader to the wrong
   half of the list.

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
| **R4** | Annotation of profile Q incompatible with annotation of profile R | Speculative — **deferred**, see §6.6 |
| **R5** | The same for tags | Speculative |
| **R6** | Mixed — a stereotype incompatible with a tag | Speculative |
| **R7** | Profile Q incompatible with profile R — *no* annotation of either may accompany an annotation of the other | Speculative, but structurally cheap; see §6.7 |

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

**R7 is the same shape one granularity up** — "at most one of the profiles in set *P* may contribute
any annotation to an element" — and that resemblance is worth noticing, because the scoping question
of §6.6 applies to it unchanged and comes out the other way: at profile granularity the pairwise form
is the one to have, and a set adds nothing a scoped reading does not immediately collapse. §6.7 works
that through. R7 is otherwise independent of everything in §6.3, and nothing below depends on it.

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
- **+** Cross-kind falls out for free (`incompatibleWith [tag:t]`), and cross-profile would too, subject to §6.6.
- **+** No new profile-level clause; the declaration sits next to the annotation it constrains.
- **+** **Anchored by construction.** The declaration hangs off an annotation the profile owns, so it
  cannot express a constraint that binds elements never referencing the declaring profile — the
  problem §6.6 has to rule out by hand for 3B and 3C.
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
    exclusive: [c, other::Prof.x];                       // R4 - deferred, see §6.6
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
- **−** **A set needs its scope stated.** Read naively, `exclusive: [b1, A.a1, A.a2]` makes `a1` and
  `a2` exclusive on their own, binding elements that never reference the declaring profile. §6.6
  scopes the set to elements using one of its owned members, which fixes it — but it is a rule to
  state, where 3A gets the same anchor from where the declaration sits.

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
- **−** Same need for a stated scope as 3B, and the bound makes the trigger matter more: with `N > 1`
  a group can be partly satisfied by external members, so "uses at least one owned member" has to be
  the trigger rather than any coarser reading (§6.6).

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
| **3A** pairwise | ✔ but O(n²) | ✔ | ✘ | deferred (§6.6); anchored by construction | ✔ | ✔ | 1 (relation) | Low |
| **3B** exclusion sets | ✔ (1 word) | ✔ | via 3C | deferred (§6.6) | ✔ | ✔ | 1 (set) | Low–medium |
| **3C** named groups | ✔ (1 word) | ✔ | ✔ | deferred (§6.6) | ✔ | ✔ | 2 (group, bound) | Medium |
| **3D** profile modifier | ✔ | ✘ | ✘ | ✘ | kind-wide only | ✘ | 0–1 | Very low |
| **3E** meta-annotations | ✔ | partly | ✘ | ✘ | ✔ | ✘ | 1 (annotatable annotations) | Medium (metamodel) |

**R7 has no column here on purpose.** It is not a point on this axis: whichever of 3A–3E is adopted,
profile-level incompatibility remains a separate declaration that neither subsumes nor is subsumed by
it. 3A, 3B and 3C could each be *stretched* to admit a whole profile as a set member, and §6.7
explains why that is the wrong way to spell it. Adopting R7 and dropping it are therefore both live
choices whichever option wins here; §6.7 argues for adopting it, on the grounds that it is the one
cross-profile capability available without the metamodel change that defers R4.

### 6.4 Recommendation — a layered package

Adopt **3B now, designed so that 3C is a strictly compatible extension later**:

- **Tier 1 (sugar, R1):** `exclusive` modifier on the `stereotypes:` / `tags:` clause.
- **Tier 2 (R2, R5, R6):** repeatable `exclusive: [ … ];` clause — at most one of the listed
  annotations, all of which its own profile defines (§6.6 defers R4).
- **Tier 3 (R3, better messages, deferred):** `groups: [ name[0..n]: [ … ] ];`. Tier 2 is exactly a
  Tier 3 group with no name and a bound of 1, so adding Tier 3 later adds syntax without changing
  any existing meaning.

and, orthogonally to all three:

- **Profile incompatibility (R7):** `incompatibleWith: [ … ];`, a list of profiles (§6.7). Not a tier
  — it does not generalise or specialise the others, and it can be adopted, deferred, or dropped
  independently of which tier lands.

Rationale: Tier 1 covers every case we can actually name today at minimum cost; Tier 2 covers the
speculative cases that are expressible within a single profile. That leaves two deferred, each for
its own reason — **R3** for want of a use case (Tier 3), and **R4** because it needs a metamodel
change as well as a rule (§6.6).

Because there is no expression-based escape hatch (§6.3), both deferrals are only acceptable
*because* they are strictly compatible extensions: if R3 turns up, `exclusive: [a, b]` keeps meaning
what it means and `groups:` is added beside it; if R4 turns up, the same clause gains external
members under the scoping rule, which leaves every all-owned set — that is, every set expressible
today — meaning exactly what it means now. This rules out picking 3A or 3D on cost grounds — under
either, supporting R3 later means a second, unrelated mechanism rather than a generalisation of the
first.

### 6.5 Semantics to pin down

1. **Counting.** Distinct annotations, not occurrences (§6.2). `<<a, a>>` contributes 1 to any set
   containing `a`.
2. **Symmetry.** Structural in 3B/3C. Under 3A the compiler must close the relation, and closure
   across profiles means profile R's declaration changes profile Q's meaning — one more reason to
   prefer sets. (Symmetry is complete only for all-owned sets; §6.6 notes where scoping makes
   membership asymmetric, which cannot arise under item 3.)
3. **No external annotations** — see §6.6. *An exclusion set may name only annotations defined by the
   declaring profile.* Compile error otherwise. This defers R4; when it is wanted, what replaces this
   rule is not a restriction but a scope — a set binds only elements using at least one member its own
   profile defines.
4. **Degenerate sets.** `|S| < 2`, or bound ≥ `|S|`: harmless no-ops; warn.
5. **Overlapping sets.** Allowed; each evaluated independently; report the first violation with
   deterministic ordering (source order) so error messages are stable.
6. **No lower bounds** — see §5.4. "At least one of S" has the same locality problem.
7. **No inheritance.** Annotations are not inherited; the milestoning hierarchy rules
   ("temporal stereotypes must be applied at all levels") are a genuinely different, hierarchy-aware
   rule and stay in `MilestoningClassValidator`.

### 6.6 External annotations in an exclusion set

Cross-profile exclusion (R4) raises two independent problems: one of meaning, which is fixed by
saying what an exclusion set's scope is, and one of incremental compilation, which needs a metamodel
change. Only the second is a reason to leave external annotations out of the first version.

#### The problem of meaning

A set-based mechanism can express something a pairwise one cannot, and it is not something we want:

```pure
Profile A { stereotypes: [a1, a2]; }
Profile B { stereotypes: [b1, b2]; exclusive: [b1, A.a1, A.a2]; }
```

`B` here declares `a1` and `a2` mutually exclusive **for elements that never mention `B` at all**.
An element carrying `<<A.a1, A.a2>>` violates the set without using a single annotation of `B`.

That is undesirable on its face — a profile should not be able to reach into another profile's
semantics — and it breaks **P3** in three separate ways:

- **Undetectable.** Validation of an element gathers constraints from the profiles of the annotations
  the element uses. An element with only `a1` and `a2` reaches `A`, which declares nothing. The
  violation is never seen.
- **Not re-validated.** `ProfileUnloaderWalk` walks the changed profile's own annotations'
  `modelElements`. Editing `B` does not re-walk elements that use no annotation of `B`, so even a
  full recompile of `B` would not revisit them.
- **Inconsistent.** Where the element happens to carry some *other* annotation of `B` — say `b2` —
  `B`'s constraints do get gathered and the same `a1`+`a2` combination is rejected. Whether the model
  compiles would depend on an unrelated annotation being present.

#### Two ways out: restrict the declaration, or scope the rule

Both start from the same arithmetic. Let `S` be an exclusion set declared in `P` with bound `N`; let
`Own` be the members `P` defines and `Ext = S \ Own` the rest. A violating element uses `N + 1`
distinct members of `S`. Every such combination includes a member of `Own` — which is what makes the
constraint reachable from the element, and re-validated when `P` changes — precisely when:

> **`|Ext| ≤ N`**

**Option 6-a restricts the declaration** so the inequality always holds, and keeps "at most `N` of
`S`" as an unconditional statement. For `exclusive:` (`N = 1`) that means at most one external
annotation: `exclusive: [b1, A.a1]` is legal — `B` saying its own `b1` is incompatible with `A`'s
`a1`, which is R4 and the thing worth having — and `exclusive: [b1, A.a1, A.a2]` is rejected.

**Option 6-c changes the statement instead.** The bound applies only to elements that use at least one
member of `Own`. Any set is then declarable, and the inequality stops being a legality test: it
becomes the test for whether the scoping is doing observable work.

**Recommended: 6-c.** The two options agree on every set 6-a permits, and that is exactly the
objection to 6-a. Where `|Ext| ≤ N`, "at most `N` of `S`" and "at most `N` of `S`, among elements
using a member of `Own`" pick out the same violations, because every violating combination already
contains a member of `Own`. So 6-a's restriction is not guarding its semantics; it is **narrowing the
declarable sets to those on which its semantics coincide with 6-c's.** It charges for a generality it
then prevents you from using.

Four consequences of taking scope as the semantics:

- **It is the intuitive reading.** A rule declared on a profile is in force where that profile is in
  use. 6-a's wording implies a rule in force always, including on elements that use nothing of `B`.
  It does not actually mean that, because the restriction sees to it — but then nothing in 6-a's
  semantics *motivates* the restriction. It is there only because the unrestricted form misbehaves.
- **It survives repositories coming and going.** The unscoped reading asks what an exclusion set in a
  repository nobody has loaded means for an element that uses only `A` — and the honest answer is that
  the model's validity would depend on which repositories happen to be present. Anchoring makes that
  unobservable; scoping makes it not arise. Under 6-c the rule travels with `B`, activates when `B`
  does, and needs no argument about the classpath.
- **It is a semantic restriction rather than a combinatorial one.** 6-c narrows a rule to the elements
  where it makes sense to apply it. 6-a narrows which rules may be written, on a ground the modeler
  cannot derive from what the feature means — the arithmetic above is not something a reader of
  `access.pure` should have to reconstruct.
- **It is strictly more expressive, at no cost in soundness.** `exclusive: [b1, A.a1, A.a2]` becomes
  writable and means "if `b1`, then neither `a1` nor `a2`" — a rule someone might genuinely want, and
  one 6-a can only spell as two clauses.

#### The trigger is per set, not per profile

The natural way to say 6-c is "`B`'s rules are in scope when `B` is in use", and that is *almost*
right. It has to be per exclusion set — at least one member of **that set's** `Own` — because the
per-profile version reintroduces the third failure above.

Take `S = [b1, A.a1, A.a2]` declared in `B`, and an element carrying `b3` (an annotation of `B` that
is not in `S`) together with `a1` and `a2`. Per-profile: `B` is in use, so `S` applies, and the
element has two members of `S` — rejected. Per-set: `S` has no owned member on the element, so it
does not apply. The per-profile reading makes `a1`+`a2` legal or illegal according to whether an
unrelated `b3` happens to be present, which is the **Inconsistent** failure verbatim. Per-set scoping
closes all three:

| Failure | Under per-set scoping |
|---|---|
| Undetectable | Not a violation by definition — an element with no owned member is outside the rule's scope |
| Not re-validated | A constrained element carries an owned member, so it is in that annotation's `modelElements` and `ProfileUnloaderWalk` reaches it |
| Inconsistent | Unrelated annotations of `B` do not bring `S` into scope |

#### What 6-c gives up

**6-a fails loudly; 6-c succeeds quietly.** A modeler who writes `exclusive: [b1, A.a1, A.a2]`
expecting `a1` and `a2` to be mutually exclusive everywhere gets a weaker rule and no diagnostic;
under 6-a they get an error telling them it is not expressible. That is the one real advantage 6-a
has, and it is worth recovering — as a **warning when `|Ext| > N`**, which is exactly the case where
scoping is observable:

```
Exclusion set in my::B applies only to elements using 'b1'; my::A.a1 and my::A.a2 are
not mutually exclusive on their own (at /model/b.pure line:6 column:5)
```

That keeps the loudness without keeping the restriction, and it puts the arithmetic in a diagnostic
where it belongs rather than in the language definition.

**Membership becomes asymmetric**, which qualifies §6.5's "symmetry is structural": symmetric among
owned members, asymmetric between owned and external. For an all-owned set — the only kind 6-b
permits, and every set in this document's examples — nothing changes at all.

That asymmetry also settles what 6-d is. For a set with one owned member, 6-c's meaning is *precisely*
6-d's directed reading, so `b1 excludes [A.a1, A.a2];` stops being a rival semantics and becomes a
question of whether the syntax should advertise the asymmetry that 6-c introduces. That is a syntax
decision to take alongside R4, not before it.

#### The second cost: annotations are not `Referenceable`

Scoping settles what an external annotation *means*, but *any* external annotation — under 3A, 3B or
3C alike, and under 6-a or 6-c alike — creates a second problem that neither touches. `B`'s exclusion
set holds a reference to `A.a1`. What re-processes `B` when `A` changes?

Nothing, today. The only back-link an annotation has is `Annotation.modelElements`, populated by
`AnnotatedElementProcessor` with the elements that *carry* it, and walked by `ProfileUnloaderWalk`.
`B` does not carry `a1`; it mentions it. So deleting `a1` from `A`, or deleting `A` outright, would
leave `B`'s set holding a stale reference with nothing to invalidate it.

The mechanism that exists for exactly this is `ReferenceUsage`:
`Referenceable.referenceUsages` records who refers to an instance, and `ReferenceableUnloaderWalk`
walks them — `referenceable._referenceUsages().forEach(r -> matcher.fullMatch(r._ownerCoreInstance(), state))`
— re-processing every referrer when the referent changes. `PackageableElement extends Referenceable`,
so a profile is covered. **`Annotation extends Any` only, so stereotypes and tags are not.**

Making them `Referenceable` is a non-negligible change:

- `m3.pure` bootstrap: `Annotation` gains a generalisation and the inherited `referenceUsages`
  property, in raw M4 syntax.
- The profile processor must create the usages, and `ProfileUnbind` must clean them up via
  `Shared.cleanUpReferenceUsage`, matching the pattern every other referring processor follows.
- `legend-engine` constructs `Root_meta_pure_metamodel_extension_Stereotype_Impl` and `…Tag_Impl`
  directly in `ProfileCompilerExtension`; those paths have to keep the new invariant.
- `AbstractCompiledStateIntegrityTest.testReferenceUsages` and its neighbours check reference-usage
  consistency across the whole compiled graph, so this has to be right rather than approximately
  right.
- Every stereotype and tag in every model gains a back-link collection, and every cross-profile
  mention allocates a `ReferenceUsage` instance.

That is a real piece of work to enable a requirement (R4) for which there is **no use case on hand**.

#### Recommendation and alternatives

**Recommend 6-b: no external annotations in exclusion sets, for now** — on the strength of the
`Referenceable` cost alone, not the question of meaning, which §6.6's first half settles. An exclusion
set may name only annotations its own profile defines. R1, R2, R3, R5 and R6 are all expressible
within one profile and are unaffected; only R4 is deferred, and it is the one requirement nobody has
needed yet. **When R4 is wanted, the semantics to adopt with it are 6-c's.**

The grammar keeps the qualified reference form `other::Prof.x` in `annotationReference` (§7.3), and
validation rejects a reference naming any profile but the declaring one, with a message that names
the restriction. That way lifting it later is a validation change, not a grammar change in two
repositories. The form is not carried solely for R4's sake either: the same rule provides the
`stereotype:` / `tag:` disambiguation a profile needs for its own overlapping names (§7.2), so it
earns its place whether or not R4 ever lands.

| | Option | Verdict |
|---|---|---|
| **6-b** | **No external annotations** — exclusion sets are same-profile only | **Recommended now**, on the `Referenceable` cost alone. One sentence to state, and it defers that work until something needs it |
| **6-c** | **Scoped semantics** — an exclusion set binds only elements using at least one member its own profile defines | **The semantics to adopt when R4 is wanted.** Sound, strictly more expressive than 6-a, and it states the scope a rule has instead of restricting which rules may be written. Costs a warning to keep the weakened reading from being silent |
| **6-a** | **The anchoring rule** (`\|Ext\| ≤ N`) | Sound, and identical to 6-c on every set it permits — which is the objection: the restriction exists to keep the unscoped semantics from ever being observable, so the generality it charges for is one it never delivers |
| **6-d** | **A directed form** — `b1 excludes [A.a1, A.a2];`, anchored on an owned annotation by construction | Not a rival to 6-c but a spelling of it: for a single owned member the two mean the same thing. The question it raises is whether the syntax should show the asymmetry 6-c introduces. Same `Referenceable` prerequisite; decide it alongside R4 |
| **6-e** | **Index constraints on their member annotations**, so `A`'s annotations carry `B`'s constraint | Fixes detection and nothing else. The propriety objection stands, and it makes an element's validity depend on the loaded profile set — the worst of the three failures above rather than a fix for it |

Note what this says about **3A**: a pairwise `incompatibleWith` declaration hangs off an annotation
the profile owns, so it gets 6-c's scoping for free — the owning annotation *is* the trigger, and the
bad case cannot be stated at all. That is a genuine structural advantage, and it is worth seeing that
6-c generalises it rather than competing with it: 6-c gives a set the same anchor 3A gets from where
the declaration sits. It does not exempt 3A from the `Referenceable` prerequisite, though, since
`incompatibleWith [other::Prof.x]` is an external reference like any other. Under 6-b, 3A's
cross-profile form is deferred on the same terms.

### 6.7 Incompatibility between profiles (R7)

A coarser granularity, and a separate feature: **profile `A` is incompatible with profile `B`** means
no element may carry an annotation of `A` and an annotation of `B` — stereotype or tag, in any
combination. A profile declares the profiles it is incompatible with:

```pure
Profile my::internal
{
    stereotypes: [derived, generated];
    tags: [source];
    incompatibleWith: [my::published];
}
```

#### Why this is cheap, and why that is not a coincidence

R4 — cross-profile incompatibility at *annotation* granularity — is deferred for two independent
reasons (§6.6): a problem of meaning, fixed by scoping the rule to elements that use the declaring
profile, and a problem of incremental compilation, which needs `Annotation extends Referenceable`.
**Neither arises here**, and both fail to arise for the same underlying reason: the only thing named
is a profile.

- **Anchored by construction, with no scope to state.** A violating element carries an annotation of
  `A` *and* an annotation of `B`. Whichever profile the declaration lives in, the element references
  that profile, so validation of the element reaches the declaration. The scoped and unscoped readings
  therefore coincide on every declaration that can be written — §6.6's `|Ext| ≤ N` holds identically,
  with `N = 1` and one external profile per pair — so unlike an exclusion set, this feature needs no
  statement of scope at all. Contrast `exclusive: [b1, A.a1, A.a2]`, whose entire problem was that a
  violating element need never mention `B`.
- **Incremental compilation already works.** `ProfileUnloaderWalk` re-walks the changed profile's
  annotations' `modelElements`. Every element that can violate carries an annotation of the declaring
  profile, so it is in that set; editing or removing the declaration re-validates exactly the
  affected elements and nothing else.
- **The reference edge is already supported.** `A` holds a reference to `B`, and
  `Profile extends PackageableElement extends Referenceable`, so `ReferenceUsage` and
  `ReferenceableUnloaderWalk` handle renaming or deleting `B` with no new machinery — the same
  machinery F1's `applicableTypes` needs anyway, since every type nameable in an `appliesTo` list is
  likewise a `PackageableElement`. This is precisely what R4 cannot have: `Annotation extends Any`.
  **Profile-granular references escape the `Referenceable` prerequisite entirely, because profiles
  are packageable elements and annotations are not.**

#### It is not a stand-in for R4, and R4 would not be a stand-in for it

The two are easy to conflate and are not interchangeable in either direction.

- R7 cannot express "`b1` conflicts with `a1`, but the rest of the two profiles mix freely". That is
  R4.
- R4 cannot practically express R7. "No annotation of `A` with any annotation of `B`" is
  `|A| × |B|` pairwise declarations that must be restated every time either profile gains an
  annotation — and each of those declarations is an external annotation reference, so the whole
  construction is behind the deferral in §6.6 regardless.

So R7 is the one cross-profile capability available *now*, at a cost the deferred one cannot match.
That, rather than any use case on hand, is the argument for it.

#### Options

| | Option | Pros | Cons |
|---|---|---|---|
| **7-a** | **`incompatibleWith: [ … ];` — a list of profiles** *(recommended)* | Anchored by construction, because every pair the clause generates contains the declaring profile; one clause, one line, whatever the sizes of the two profiles; symmetric with no closure step (below) | Blunt: all-or-nothing across both profiles, with no way to exempt an annotation |
| **7-b** | **An exclusion set over profiles** — `exclusive profiles: [A, B, C];` | Uniform with 3B/3C one granularity up; reads as "at most one of these vocabularies", which 7-a states one pair at a time | **Redundant once scoped.** Read unscoped it is the §6.6 failure — declared in `A`, the pair `{B, C}` binds elements that never mention `A`. Read scoped (§6.6, 6-c) it is sound but collapses: binding only elements that use `A`, `exclusive profiles: [A, B, C]` says exactly `A incompatibleWith [B, C]`. And declared in a profile that is not a member, it is vacuous. So the set form is either wrong or 7-a with more syntax |
| **7-c** | **Annotation-to-profile** — `b1 incompatibleWith [my::A];` | Strictly more expressive: one stereotype of `B` excludes all of `A` without committing the rest of `B`. Still anchored (`b1` is owned) and still needs no `Referenceable` change, since `my::A` is the only thing referenced | A third granularity to teach, with no use case; and it is 6-d's shape, whose spelling §6.6 defers alongside R4 |
| **7-d** | **Drop R7 — use annotation-level pairs when R4 lands** | No new syntax now | See above: quadratic, restated on every profile edit, and gated behind the R4 deferral. Not a real alternative |

**Recommendation: 7-a.** It is the shape described in the brief, it is the only one of the four that
is both sound and available today, and 7-c remains a strictly compatible extension if
"this stereotype excludes all of that profile" ever turns up as a real want.

Observe that the verdicts here come out the **opposite way** to §6.3's. Between annotations the set
form (3B) is recommended and the pairwise form (3A) is the awkward one; between profiles the pairwise
form (7-a) wins and the set form (7-b) has nothing to offer. What differs is what scoping buys. An
exclusion set is scoped by *its own* members, so a set with external members still says something a
pairwise form cannot say as compactly. A profile-level set is declared *by a member of itself*, so
scoping it leaves precisely the pairs between the declaring profile and the others — which is the
pairwise list. The generalisation that pays off one granularity down is a no-op here.

#### Semantics to pin down

1. **Presence, not count.** One annotation of `A` is enough to trigger the check; repetitions are
   irrelevant, consistent with §6.5.1.
2. **Stereotypes and tags pooled.** "Any annotation of `A`" spans both kinds. This is the one place in
   F3 where the two kinds are counted together rather than by whatever set happens to name them.
3. **One declaration suffices; symmetry needs no closure.** Under 3A the compiler has to close the
   relation so that `a excludes b` also means `b excludes a`. Nothing needs closing here: validation
   of an element gathers declarations from *every* profile the element references, so `A`'s
   declaration is found whether or not `B` restates it. Declaring both directions is harmless
   duplication — worth a warning only if we want the redundancy flagged (probably not).
4. **Not transitive.** `A ⊥ B` and `B ⊥ C` say nothing about `A` and `C`. Closing transitively would
   also break **P3**, since the inferred pair could bind an element that references neither `B` nor
   whichever profile declared the second pair.
5. **Self-reference is degenerate — reject it.** `incompatibleWith: [my::internal]` inside
   `my::internal` reads either as a contradiction (no element may carry any of its annotations) or,
   if the two members are required to be distinct annotations, as "at most one annotation of this
   profile, stereotypes and tags pooled" — which is a genuinely useful rule that Tier 1 cannot state,
   since `exclusive stereotypes:` and `exclusive tags:` are two independent sets. Reject the
   self-reference and point the message at the explicit `exclusive: [s1, s2, t1, t2]` spelling, which
   says it unambiguously. (Q13.)
6. **Independent of everything else.** No interaction with `appliesTo`, occurrence limits, or
   exclusion sets; each is evaluated and reported separately.
7. **Profiles are elements too.** `Profile` is an `AnnotatedElement` (§2.1), so a profile that itself
   carries annotations of both `A` and `B` violates like any other element. Uniform, and no special
   case needed in the checker.
8. **Degenerate declarations** — naming a profile twice, or naming one with no annotations — are
   no-ops; warn, per §6.5.4.

#### When it is the right tool

Profile incompatibility says the two *vocabularies* are alternatives: an element is classified by one
or the other, not both. It is deliberately blunt, and it is **dynamic** in the same way as the
`exclusive stereotypes:` shorthand (§6.3) — an annotation added to either profile later silently
joins the constraint. That is usually the point, since the constraint is about the vocabularies
rather than their current contents; but it is the same surprise, and deserves the same note in the
documentation. If only some annotations conflict, the answer is R4, not this.

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
    exclusive: [audited, reviewer];                     // R6, mixed stereotype and tag
}

Profile my::published
{
    stereotypes: [stable];
    tags: [approvedBy];
}

Profile my::internal
{
    stereotypes: [derived, generated];
    tags: [source];
    incompatibleWith: [my::published];    // R7 - no annotation of either alongside one of the other,
}                                         //      and one declaration binds both directions
```

### 7.2 A profile whose stereotype and tag names overlap

A profile may define a stereotype and a tag with the same name, today and under this proposal:
`ProfileValidator` checks uniqueness within `p_stereotypes` and within `p_tags` **separately**, so
`signOff` below is two distinct annotations. Nothing here should change that.

At every existing use site the kind is fixed by position rather than by spelling — `<<…>>` is a
stereotype (`stereotype: qualifiedName DOT identifier`) and `{… = '…'}` is a tagged value
(`taggedValue: qualifiedName DOT identifier EQUAL …`) — so the overlap has never needed
disambiguation:

```pure
Class <<my::Review.signOff>> {my::Review.signOff = 'kmk'} my::Trade
{
    id : String[1];
}
```

The same holds inside the profile's own declaration clauses, where `stereotypes:` and `tags:` each
fix the kind of everything they list, modifiers included:

```pure
Profile my::Review
{
    appliesTo: [Class];
    stereotypes: [reviewed, signOff appliesTo [Class, Property]];
    tags: [signOff[0..1], reviewer];
}
```

**The one new context where the overlap bites is an `exclusive:` set**, whose members are bare names
resolved against the declaring profile. `signOff` alone is ambiguous there and is an error; a
`stereotype:` or `tag:` prefix resolves it, and both kinds can appear in the same set:

```pure
Profile my::Review
{
    stereotypes: [reviewed, signOff];
    tags: [signOff[0..1], reviewer];

    exclusive: [reviewed, tag:signOff];          // stereotype 'reviewed' vs the TAG 'signOff'
    exclusive: [stereotype:signOff, reviewer];   // the STEREOTYPE 'signOff' vs tag 'reviewer'
}
```

Under those two sets, the `my::Trade` declaration above still compiles: it carries the stereotype
`signOff` and the tag `signOff`, which fall in *different* sets, one member each. Adding
`<<my::Review.reviewed>>` to it would violate the first set, and adding
`{my::Review.reviewer = 'x'}` would violate the second.

Three decisions are folded into that spelling, and §7.3 gives the grammar:

- **The profile is not named.** An annotation of the declaring profile is referred to by its own name.
  Requiring `my::Review%signOff` inside `my::Review` would be redundant, and it would make the common
  case pay for the deferred cross-profile one.
- **The kind is named by a word, not a sigil.** `%` means "tag" only in `ImportStub` id-paths and in
  expression position; nothing about it is guessable. `stereotype:` and `tag:` are, and this construct
  is rare enough — it appears only when a profile has an overlapping name — that guessability is worth
  more than keystrokes. `st:` is a reasonable synonym to add later if the long form grates; adding one
  costs a line of validation under §7.3's approach and no grammar change.
- **A qualified reference uses the dot form**, `other::Prof.signOff`, matching what modelers type at
  use sites — not `other::Prof@signOff`, which they meet only in expression position. So the prefix and
  the qualification compose into one spelling, `stereotype:other::Prof.signOff`, rather than the
  same-profile and cross-profile cases needing unrelated syntaxes when R4 lands (§6.6).

The prefix is **permitted always and required only where the name is ambiguous**, so it can be used
defensively and emitted unconditionally by tooling. The ambiguity error has to name the fix — §11.

### 7.3 Proposed ANTLR (legend-pure; mirror in legend-engine)

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
              | incompatibleDefinition
;

appliesToDefinition: APPLIES_TO COLON qualifiedNameList END_LINE
;

stereotypeDefinitions: EXCLUSIVE? STEREOTYPES COLON
                       BRACKET_OPEN annotationDefinition (COMMA annotationDefinition)* BRACKET_CLOSE END_LINE
;

tagDefinitions:        EXCLUSIVE? TAGS COLON
                       BRACKET_OPEN annotationDefinition (COMMA annotationDefinition)* BRACKET_CLOSE END_LINE
;

annotationDefinition:  identifier multiplicity? (APPLIES_TO qualifiedNameList)?
;

exclusiveDefinition:   EXCLUSIVE COLON
                       BRACKET_OPEN annotationReference (COMMA annotationReference)* BRACKET_CLOSE END_LINE
;

incompatibleDefinition: INCOMPATIBLE_WITH COLON qualifiedNameList END_LINE
;

annotationReference:   annotationKind? annotationName
;

annotationKind:        identifier COLON      // 'stereotype' | 'tag' - see below, NOT lexer keywords
;

annotationName:        qualifiedName (DOT identifier)?
;

qualifiedNameList:     BRACKET_OPEN qualifiedName (COMMA qualifiedName)* BRACKET_CLOSE
;
```

New lexer tokens: `APPLIES_TO: 'appliesTo';`, `EXCLUSIVE: 'exclusive';` and
`INCOMPATIBLE_WITH: 'incompatibleWith';`.

Notes and gotchas:

- **New keywords must be added to the `identifier` rule** ([`M3CoreParser.g4:3`](../../legend-pure-core/legend-pure-m3-core/src/main/antlr4/org/finos/legend/pure/m3/serialization/grammar/m3parser/antlr/core/M3CoreParser.g4)),
  which already lists `CLASS | FUNCTION | PROFILE | … | STEREOTYPES | TAGS | …` for exactly this
  reason. Without it, any existing model with a property or class named `exclusive`, `appliesTo` or
  `incompatibleWith` stops compiling. Same in the engine grammar. **This is the one way this proposal
  could break existing models, and it is avoidable.**
- **`annotationKind` is deliberately not a pair of lexer tokens.** `stereotype` and `tag` are matched
  as ordinary identifiers and their text is checked in the visitor, which rejects anything else with
  `"unknown annotation kind 'x'; expected 'stereotype' or 'tag'"`. Three reasons:
  1. **No reserved-word hazard.** `tag` is a thoroughly plausible property name in existing models
     (`stereotype` less so, but not impossibly). As lexer tokens both would have to be added to the
     `identifier` rule in both grammars or those models stop compiling — the hazard flagged in the
     bullet above, incurred for no benefit.
  2. **Better diagnostics.** A misspelling produces a message naming the legal kinds, rather than a
     parser error pointing at a bracket.
  3. **The spelling stays cheap to revise.** Accepting `st:` as a synonym later is one line in a
     validator instead of a coordinated change to two ANTLR grammars. (Q14.)

  The cost is that the grammar file no longer enumerates the legal prefixes, so the rule needs the
  comment shown above.
- `annotationName` covers all four cases with one rule: `signOff` (this profile), `other::Prof.signOff`
  (another profile), and either with a kind prefix. A reference naming any profile but the declaring
  one parses and is then rejected by validation (§6.6), so lifting that restriction when R4 is wanted
  is a validation change rather than a grammar change.
- **The dot spelling, not `@` / `%`.** `annotationName` uses `qualifiedName DOT identifier`, matching
  the `stereotype` and `taggedValue` use-site rules (`M3CoreParser.g4:231, 237`) rather than
  `stereotypeReference` / `tagReference` (219-223). Those two rules are untouched and stay as they
  are for expression position. The argument is that an exclusion set talks about annotations *as
  applied to elements*, so it should read like a use site; a knock-on benefit is that a kind prefix
  and a qualified name compose (`stereotype:other::Prof.signOff`) where `@` / `%` and a prefix would
  collide.
- An unprefixed name that the declaring profile defines as **both** a stereotype and a tag is
  **ambiguous → error** (§7.2). Resolving stereotypes first and tags second is the obvious
  alternative and is the wrong call: it makes the tag unreachable by its own name and hides a genuine
  authoring mistake behind a silent preference. Nothing today prevents a profile from defining a
  same-named stereotype and tag — `ProfileValidator` checks the two lists separately — and this
  proposal should not start.
- One token of lookahead separates all three member shapes: `identifier COLON` (a kind prefix),
  `identifier PATH_SEPARATOR` (a package path), and a bare `identifier`.
- `appliesToDefinition` and `incompatibleDefinition` share `qualifiedNameList`: both are lists of
  `ImportStub`s, distinguished only by what validation requires them to resolve to — a `Type` for
  `appliesTo` (§4.3), a `Profile` for `incompatibleWith`. Keeping one rule keeps the two error
  messages parallel.
- Changing `stereotypeDefinitions? tagDefinitions?` to `profileElement*` makes the two clauses
  order-independent and repeatable — which **aligns legend-pure with legend-engine**, whose grammar
  already allows this. Strict superset, so backward compatible. Decide whether repeated clauses of
  the same kind merge (engine's current behaviour) or error (Q6); merging is the compatible choice.
  Note the rule generalises: `exclusive:` is repeatable by design (§6.4), and `profileElement*` makes
  `appliesTo:` and `incompatibleWith:` repeatable too, where merging means union. That is a defensible
  reading for all four, but it should be a decision rather than a side effect of the rule shape.
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
    incompatibleProfiles  : Profile[*];              // NEW - F3, R7
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

- **Reference resolution is nearly free.** `M3ToJavaGenerator` (lines 97-108) maps property raw types
  to stub types: `Type`, `Class`, `Stereotype`, `Tag` are all already `ImportStub`. Declaring
  `applicableTypes : Type[*]` automatically generates `_applicableTypesCoreInstance()` and the
  `ImportStub` plumbing, exactly as `ElementWithStereotypes.stereotypes` works today. Same for
  `AnnotationConstraint.stereotypes` / `.tags`.
- **`Profile` is *not* in that table**, so `incompatibleProfiles : Profile[*]` needs one line added —
  `StubDef.build("Profile", "ImportStub")`. That is the whole generator cost of R7. Note this is a
  cost of the *property*, not of profiles being referenceable: `Profile extends PackageableElement
  extends Referenceable` already, which is the point made in §6.7.
- If a single `annotations : Annotation[*]` property is preferred over the split
  `stereotypes`/`tags` pair, add one line — `StubDef.build("Annotation", "ImportStub")` — to that
  same table. The split version needs no generator change at all, and matches the `@`/`%` reference
  forms; the unified version reads better. (Q7.)
- **Naming.** Note `getSubstituteType` (line 2611) special-cases `p_stereotypes` / `p_tags`; new
  property names must not collide with inherited ones (`Profile` inherits `stereotypes` and
  `taggedValues` from `AnnotatedElement`, which is why the `p_` prefix exists). `applicableTypes`,
  `annotationConstraints` and `incompatibleProfiles` are collision-free.
- **The bootstrap cost is real.** These properties must be hand-written into
  `platform/pure/grammar/m3.pure` in raw M4 instance syntax — roughly 6 verbose lines per property,
  copy-adapted from the neighbouring definitions.

---

## 9. Compiler implementation

### 9.1 legend-pure

| Area | File | Change |
|---|---|---|
| Grammar | `M3CoreLexer.g4`, `M3CoreParser.g4` | §7.3, including the `identifier` rule |
| Bootstrap | `platform/pure/grammar/m3.pure` | New properties + `AnnotationConstraint` class |
| Generator | `M3ToJavaGenerator` (97-108) | One line: `StubDef.build("Profile", "ImportStub")`, for `incompatibleProfiles` (§8) |
| Parse | `AntlrContextToM3CoreInstance.profile/buildStereoTypes/buildTags` (3432-3483) | Build the new values; create `ImportStub`s for type, profile and annotation references |
| Post-process | **new** `ProfileProcessor` | Resolve the profile's stubs (there is no processor for `Profile` today), and register the `ReferenceUsage`s for the type and profile references |
| Unbind | **new** `ProfileUnbind` | Reset those stubs on source change and clean up their reference usages via `Shared.cleanUpReferenceUsage`, alongside `ElementWithStereotypesUnbind` |
| Validate | `ProfileValidator` | Well-formedness of declarations: applicable-type entries resolve to types (§4.3 — nothing more); `incompatibleWith` entries resolve to profiles and none is the declaring profile (§6.7); no lower bounds; `maxOccurrences > 0`; no external annotations in exclusion sets (§6.6); unknown annotation kind and ambiguous unprefixed annotation references (§7.2); degenerate-set warnings |
| Validate | **new** `AnnotationUsageValidator` | The four usage rules — applicability, occurrence, exclusion sets, profile incompatibility — registered in `M3AntlrParser.getValidators()` |
| Validate | `AccessLevelValidator` (62-88, 91-203) | Independently of this proposal, rewrite the element-type tests against the current hierarchy (§4.5): `instanceof ConcreteFunctionDefinition` for `externalizable`, `instanceof Class \|\| instanceof PackageableFunction` for the rest. Then, once `access.pure` carries the declarations, delete the `default:` branch (superseded by `exclusive`) and those element-type tests (superseded by `appliesTo`), keeping the rest of `validateExplicitAccessLevel` |
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

Profile incompatibility (§6.7) rides along in the first registration, since it pools stereotypes and
tags: gather the distinct profiles the element draws annotations from, then check each declared pair
against that set.

**Incremental compilation** needs no new machinery. `ProfileUnloaderWalk` already re-walks every
model element of every annotation of a changed profile, which is exactly the set of elements whose
validity can change — for exclusion sets because they are confined to one profile (§6.6), and for
profile incompatibility because a violating element necessarily carries an annotation of the
declaring profile (§6.7). The one thing R7 does add is a profile→profile reference, and that is
carried by the existing `ReferenceUsage` mechanism because `Profile` is a `PackageableElement`; the
`ProfileProcessor` / `ProfileUnbind` pair above has to maintain it, exactly as it must for
`applicableTypes`.

### 9.2 legend-engine

| Area | File | Change |
|---|---|---|
| Grammar | `DomainParserGrammar.g4:102-112`, `DomainLexerGrammar.g4` | Mirror §7.3 |
| Parse | `DomainParseTreeWalker` | Populate the new protocol fields |
| Protocol | `m3/extension/Profile.java`, `ProfileStereotype.java`, `ProfileTag.java` | New optional fields — including `incompatibleProfiles` as a list of paths on `Profile`, which today holds only `stereotypes` and `tags`. **The stereotype and tag entries are already objects, not bare strings**, so per-annotation additions are JSON-compatible in both directions |
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
  §7.3, which the `identifier` rule fix removes.
- **Binary/PAR serialization** is property-driven and generic, so new properties flow through
  without per-feature work. PAR files are version-locked to the compiler that reads them, so there is
  no mixed-version concern.
- **Protocol JSON** gains optional fields on existing objects — old JSON reads fine (absent = today's
  behaviour), new JSON read by an old engine loses the fields (which is exactly the Studio risk
  above, and the reason to land engine support before advertising the feature).
- **Platform profile tightening should be a separate, announced change.** The machinery is
  behaviour-preserving; declaring `doc[0..1]` on `meta::pure::profiles::doc` is not — any model that
  currently attaches two `doc.doc` values stops compiling. Suggested sequencing:
  0. Rewrite `AccessLevelValidator`'s element-type tests against the current hierarchy (§4.5). This
     does not depend on anything else here and should land first, so that step 2 moves a rule that
     is already correct rather than migrating a stale one.
  1. Land metamodel + grammar + validators (no platform profile changes). Nothing breaks.
  2. Declare `appliesTo` and `exclusive` on `access` and `temporal`; delete the corresponding
     hard-coded validator branches. Behaviour changes only in that *more* things are rejected — plus
     the deliberate relaxation that a repeated identical access stereotype is no longer an error.
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

Profiles my::internal and my::published are incompatible; my::Foo has stereotype 'derived' of
my::internal (line:4 column:12) and tag 'approvedBy' of my::published (line:4 column:44)
(declared at /model/internal.pure line:9 column:5)

'signOff' is both a stereotype and a tag of my::Review; use stereotype:signOff or tag:signOff
```

With Tier 3 named groups the third becomes `... 2 stereotypes from group 'visibility' of profile ...`,
which is the concrete argument for eventually adopting 3C.

The fourth is the profile-level form (§6.7). Note it names one annotation from each side rather than
all of them: the pair is what is illegal, and quoting the first offending annotation from each
profile is enough to locate the conflict without dumping both annotation lists. The last is the
name-overlap diagnostic from §7.2. It has to state the fix rather than merely report the ambiguity:
the reader has almost certainly never needed a kind prefix before, so the message is where they learn
the syntax exists.

---

## 12. Test plan

| Layer | Location | Coverage |
|---|---|---|
| Profile well-formedness | `m3/tests/validation/TestProfileValidation.java` | Applicable-type entry that is not a type; `incompatibleWith` entry that is not a profile; self-incompatibility rejected; lower bound rejected; `maxOccurrences` ≤ 0; exclusion set naming another profile's annotation rejected; ambiguous bare annotation reference; degenerate set warning |
| Grammar | `m3/tests/elements/profile/TestProfile.java` | Every clause, all orders, repeated clauses, plain profiles unchanged; new keywords still usable as identifiers; **a profile defining a same-named stereotype and tag** — declaration clauses, `stereotype:`/`tag:` prefixes in exclusion sets, both used on one element, and `stereotype` and `tag` still usable as ordinary identifiers elsewhere in the same model (§7.2, §7.3) |
| Usage | new `TestAnnotationApplicability` / `TestAnnotationOccurrence` / `TestAnnotationExclusivity` / `TestProfileIncompatibility` | Positive/negative per feature; profile-level vs annotation-level override; **subtype acceptance through a supertype that is not itself annotatable** — `appliesTo: [Function]` accepting both function kinds, `appliesTo: [Type]` accepting both a class and an enumeration (§4.3); repetition not counted for F3 but counted for F2; mixed sets. For R7: stereotype+stereotype, tag+tag and stereotype+tag across the pair; one declaration binding both directions with no reverse declaration; non-transitivity across three profiles; a *profile* carrying annotations of both (§6.7, item 7) |
| Existing behaviour | `m3/tests/validation/TestAccess.java` | Update the multi-access-stereotype expectations when step 2 of §10 lands |
| Incremental | `m3/tests/incremental/profile/` | Edit/delete a referenced type; edit a profile's constraints and confirm dependent elements are re-validated; delete or rename a profile named in another profile's `incompatibleWith`, and confirm the referring profile is re-processed rather than left holding a stale stub |
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
| **Q6** | Repeated clauses of the same kind in one profile — merge or error? | Merge, matching legend-engine's existing behaviour for `stereotypes:` / `tags:`. Applies equally to the new clauses, where merge means union (§7.3); `exclusive:` is repeatable by design and each occurrence stays a separate set. |
| **Q7** | `AnnotationConstraint.stereotypes`/`tags` split, or a unified `annotations : Annotation[*]`? | Unified reads better and costs one line in `M3ToJavaGenerator`; the split needs no generator change. Weak preference for unified. |
| **Q8** | Keyword spellings: `appliesTo` vs `applicableTo`; `exclusive` vs `mutuallyExclusive` vs `atMostOne`; `incompatibleWith` vs `excludesProfiles`. | `appliesTo` / `exclusive` / `incompatibleWith` — shortest that still read as English. Note `incompatibleWith` is deliberately *not* `exclusive`-flavoured: it is a different granularity and should not look like a variant of the same clause. |
| **Q9** | Should there be a middle, clause-level `appliesTo` (`stereotypes appliesTo [Class]: [...]`) between profile-level and annotation-level? | No — two levels are enough; a third is easy to add later. |
| **Q10** | Should `appliesTo` also constrain where a *profile-level* stereotype may be used, i.e. does the profile's own list apply to tags as well as stereotypes? | Yes, both — as stated in the brief. |
| **Q11** | Negative type constraints (`appliesTo: [Class, Function, !Property]`)? | Positives only for now (§4.6). The case that suggested it was a stale workaround for a missing type, not a genuine subtraction, and the `Column` example shows the two readings differ on which future subtypes get admitted silently. Additive later if a real case appears. |
| **Q12** | Cross-profile exclusion (R4) — defer it, or pay for it now? | Defer (§6.6), but on the `Referenceable` cost alone: the question of meaning is settled, and there is no use case on hand. When it is wanted, adopt 6-c's scoped semantics — an exclusion set binds only elements using at least one member its own profile defines — with a warning where the scoping is observable, and decide 6-d's directed spelling then. |
| **Q13** | Profile incompatibility (R7) — include it, and if so how should a self-reference read? | Include it as 7-a (§6.7): it is the only cross-profile capability that is sound *and* free of the `Referenceable` prerequisite, so its cost is one property, one `StubDef` line and one clause. Reject self-reference; if "at most one annotation of this profile, stereotypes and tags pooled" is wanted, it is worth its own spelling rather than a reflexive reading nobody would guess. |
| **Q14** | Accept `st:` as a synonym for `stereotype:`? | Not initially (§7.2). The prefix appears only where a profile has an overlapping name, so guessability beats brevity; and because `annotationKind` is validated rather than lexed (§7.3), adding the synonym later is a line in a validator, so nothing is lost by waiting to see whether the long form actually grates. |
