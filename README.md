# Hibernate ORM 7 — `lateral unnest` / `FunctionJoin` bug reproducers

This repo demonstrates three bugs in Hibernate ORM 7.3.4.Final's handling of HQL queries that involve `lateral unnest(...)` or collection-valued path expressions resolved through a `FunctionJoin`. Each bug has a dedicated test class with one or more JUnit reproducers.

The reproducers depend only on Hibernate ORM, the H2 driver, the PostgreSQL JDBC driver (for type registration — no live PG required), and JUnit 5. **No MongoDB**.

## Setup

```bash
git clone <this-repo>
cd hibernate7-unnest-bug-reproducers
gradle test    # or: ./gradlew test if you set up the wrapper
```

All eight tests should pass — each one ASSERTS that the bug fires, so a green build means the bug still exists in Hibernate 7.3.4.Final. If Hibernate fixes any of them, the corresponding test will fail loudly.

Requires Java 21.

## The bugs

### 1. `SqmMappingModelHelper.resolveSqmPath` AssertionError on `FunctionJoin` paths

Test class: [`SqmResolveFunctionJoinPathTest`](src/test/java/com/example/hibernate/unnestbugs/SqmResolveFunctionJoinPathTest.java)

Bare `java.lang.AssertionError` (no message) from `SqmMappingModelHelper.resolveSqmPath` when HQL references a column on an alias bound to a `lateral unnest(...)` set-returning function or a basic-plural-attribute sugar join.

Four reproducers, all sharing the same root cause:
- Body predicate on `JOIN LATERAL unnest(o.scalarArray) a WHERE a > ?`
- Body predicate on sugar-form `JOIN o.scalarArray a WHERE a > ?`
- `WHERE EXISTS (SELECT 1 FROM o.scalarArray a WHERE a > ?)`
- `WHERE x IN (SELECT t FROM o.scalarArray t)` — type inference at `visitInSubQueryPredicate` calls into the same helper

### 2. `ClassCastException: SqmFunctionJoin cannot be cast to SqmSingularValuedJoin` for nested EXISTS

Test class: [`NestedExistsSqmFunctionJoinCastTest`](src/test/java/com/example/hibernate/unnestbugs/NestedExistsSqmFunctionJoinCastTest.java)

EXISTS subquery nested inside another EXISTS subquery, where the inner subquery's FROM references a collection-valued path on the outer's `lateral unnest` alias. Requires `@Struct` support, so uses `PostgreSQLDialect` with `hibernate.boot.allow_jdbc_metadata_access=false` and a stub `ConnectionProvider` — no live PostgreSQL server needed (the bug fires before SQL execution).

### 3. HQL grammar rejects `LATERAL unnest(...)` inside subqueries

Test class: [`HqlGrammarLateralUnnestInSubqueriesTest`](src/test/java/com/example/hibernate/unnestbugs/HqlGrammarLateralUnnestInSubqueriesTest.java)

`SyntaxException` at the `(` after `unnest` for two HQL forms:
- `WHERE EXISTS (SELECT 1 FROM lateral unnest(i.tags) t ...)`
- `(SELECT count(*) FROM lateral unnest(i.tags) t ...)` in scalar SELECT subquery

For contrast, the same `LATERAL unnest(...)` in the **outer** FROM (`FROM Item i JOIN lateral unnest(i.tags) t`) parses cleanly — the third test in the class asserts this. The grammar restriction is specific to subquery FROM clauses.

## Discovery context

These bugs were surfaced during the MongoDB Hibernate extension's effort to translate HQL `$elemMatch`-style queries (over arrays of embedded documents) into MongoDB MQLv2 `any(...)` and `unwind` operations. The MQLv2 server-side equivalents of every HQL form above execute correctly — Hibernate's SQM/grammar layers are what prevent the queries from compiling.

The detailed Phase 0-4 design and findings live in the MongoDB Hibernate extension's `mqlv2` branch:
- Design doc: `docs/superpowers/specs/2026-05-15-mqlv2-elemmatch-via-unnest-design.md`
- Bug-report markdown (one per bug, mirroring this repo's test classes): `docs/upstream-feedback/hibernate-bugs/`

## License

Apache 2.0 (matching Hibernate ORM and MongoDB Hibernate extension).
