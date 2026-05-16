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

### 1. Basic-array body predicates fail with `AssertionError`

Test class: [`BasicArrayBodyPredicateTest`](src/test/java/com/example/hibernate/unnestbugs/BasicArrayBodyPredicateTest.java)

HQL that references a basic-typed array element in a body predicate (sugar JOIN, explicit `lateral unnest(...)` JOIN, EXISTS over implicit collection path, or IN-subquery over the same) throws `AssertionError` from `SqmMappingModelHelper.resolveSqmPath`. Investigation revealed a *second* bug stacked behind it: a brittle cast in `BaseSqmToSqlAstConverter.visitHqlNumericLiteral` that fires once the first is addressed. Both must be fixed for any affected HQL form to work.

### 2. Nested unnest via correlated subquery is blocked

Test class: [`NestedUnnestCorrelationTest`](src/test/java/com/example/hibernate/unnestbugs/NestedUnnestCorrelationTest.java)

Nested EXISTS over an array-of-structs-each-containing-an-array fails with `ClassCastException: SqmFunctionJoin cannot be cast to SqmSingularValuedJoin` from `SqmSubQuery.correlate(Join)`. Investigation showed the cast is the outermost of three stacked issues; the deepest layer (recursive unnest through `AnonymousTupleType`) is a missing feature rather than a bug.

### 3. HQL grammar rejects `LATERAL unnest(...)` inside subqueries

Test class: [`HqlGrammarLateralUnnestInSubqueriesTest`](src/test/java/com/example/hibernate/unnestbugs/HqlGrammarLateralUnnestInSubqueriesTest.java)

`SyntaxException` at the `(` after `unnest` for two HQL forms:
- `WHERE EXISTS (SELECT 1 FROM lateral unnest(i.tags) t ...)`
- `(SELECT count(*) FROM lateral unnest(i.tags) t ...)` in scalar SELECT subquery

For contrast, the same `LATERAL unnest(...)` in the **outer** FROM (`FROM Item i JOIN lateral unnest(i.tags) t`) parses cleanly — the third test in the class asserts this. The grammar restriction is specific to subquery FROM clauses. Parser-layer issue, independent of the other bugs.

### Bonus: a latent NPE in `SqmFunctionJoin.getParent()`

No reproducer test (it's reachable only by direct method invocation, not by any HQL). See [`sqm-function-join-get-parent-latent-npe.md`](https://github.com/jyemin/mongo-hibernate/blob/mqlv2/docs/upstream-feedback/hibernate-bugs/sqm-function-join-get-parent-latent-npe.md) — found while investigating bug 2.

## Discovery context

These bugs were surfaced during work on a custom dialect that translates HQL queries involving collection-valued paths (and explicit `lateral unnest`) into a non-SQL backend. The backend's own pipeline syntax can evaluate every HQL form below correctly — Hibernate's SQM and grammar layers are what prevent the queries from compiling. The reproducers in this repo run against PostgreSQL, but the bugs are at the HQL/SQM layer and are reproducible against any dialect that registers `unnest()` and supports `@Struct`.

## License

Apache 2.0 (matching Hibernate ORM).
