# Hibernate ORM 7 — `lateral unnest` / `FunctionJoin` bug reproducers

This repo demonstrates two bugs in Hibernate ORM 7.3.4.Final's handling of HQL queries that involve `lateral unnest(...)` or collection-valued path expressions resolved through a `FunctionJoin`. Each bug has a dedicated test class.

The reproducers depend on Hibernate ORM, the PostgreSQL JDBC driver, and JUnit 5.

## Setup

A running PostgreSQL is required. Quickstart on macOS:

```bash
brew install postgresql@17
brew services start postgresql@17
createdb unnestbugs
```

By default the tests connect to `jdbc:postgresql://localhost:5432/unnestbugs` as the current OS user with no password. Override with system properties if needed:

```bash
gradle test -Dunnestbugs.jdbcUrl=jdbc:postgresql://host:5432/db \
            -Dunnestbugs.dbUser=alice \
            -Dunnestbugs.dbPassword=secret
```

Then:

```bash
git clone <this-repo>
cd hibernate7-unnest-bug-reproducers
gradle test    # or: ./gradlew test if you set up the wrapper
```

**These are regression tests written in the inverse direction.** Each test asserts the query executes successfully. Against Hibernate 7.3.4.Final, **most tests fail** — those failures are the bugs. The one test that passes is `outerFromWithLateralUnnest_parsesAndExecutes`, included as a contrast case to confirm the grammar restriction is specific to subquery FROM clauses. When Hibernate fixes a bug, the corresponding test transitions from failing to passing.

To verify a Hibernate SNAPSHOT containing a fix: run `./gradlew publishToMavenLocal` in your local hibernate-orm checkout, then update the `hibernate-core` coordinate in `build.gradle.kts` to the SNAPSHOT version. The `mavenLocal()` repository is already configured.

Requires Java 21.

## The bugs

### 1. `SqmFunctionJoin` is not first-class in SQM-to-SQL visitors

Test class: [`SqmFunctionJoinNotFirstClassTest`](src/test/java/com/example/hibernate/unnestbugs/SqmFunctionJoinNotFirstClassTest.java)

When Hibernate added `SqmFunctionJoin` / set-returning-function support, not every SQM visitor was extended to handle it. The test class contains two `@Nested` groups, each exercising a different visitor site that exposes the same underlying gap:

- **`ResolveSqmPath`** (4 tests, H2): `SqmMappingModelHelper.resolveSqmPath` throws a bare `AssertionError` when resolving a path through a `SqmFunctionJoin`. Affected forms include sugar JOIN with a body predicate, EXISTS over an implicit collection path, IN-subquery over the same, and explicit `lateral unnest(...)` JOIN.

- **`CorrelationCast`** (2 tests, PostgreSQL dialect + stub ConnectionProvider): `ClassCastException: SqmFunctionJoin cannot be cast to SqmSingularValuedJoin` when an inner EXISTS subquery correlates to an outer EXISTS whose alias is a `SqmFunctionJoin`. Covers both the natural implicit-collection-path form and the outer-`lateral-unnest` mixed form.

Both groups likely require distinct code edits in distinct methods, but they share the same design fix: make `SqmFunctionJoin` a first-class citizen in SQM-to-SQL conversion.

### 2. HQL grammar rejects `LATERAL unnest(...)` inside subqueries

Test class: [`HqlGrammarLateralUnnestInSubqueriesTest`](src/test/java/com/example/hibernate/unnestbugs/HqlGrammarLateralUnnestInSubqueriesTest.java)

`SyntaxException` at the `(` after `unnest` for two HQL forms:
- `WHERE EXISTS (SELECT 1 FROM lateral unnest(i.tags) t ...)`
- `(SELECT count(*) FROM lateral unnest(i.tags) t ...)` in scalar SELECT subquery

For contrast, the same `LATERAL unnest(...)` in the **outer** FROM (`FROM Item i JOIN lateral unnest(i.tags) t`) parses cleanly — the third test in the class asserts this. The grammar restriction is specific to subquery FROM clauses. This is a parser-layer issue, independent of Bug 1.

## Discovery context

These bugs were surfaced during work on a custom dialect that translates HQL queries involving collection-valued paths (and explicit `lateral unnest`) into a non-SQL backend. The backend's own pipeline syntax can evaluate every HQL form below correctly — Hibernate's SQM and grammar layers are what prevent the queries from compiling. The reproducers in this repo use H2 and PostgreSQL exclusively, so the issues are reproducible against any dialect that registers `unnest()` and supports `@Struct`.

## License

Apache 2.0 (matching Hibernate ORM).
