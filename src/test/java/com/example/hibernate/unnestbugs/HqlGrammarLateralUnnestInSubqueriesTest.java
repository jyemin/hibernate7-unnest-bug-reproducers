package com.example.hibernate.unnestbugs;

import static org.assertj.core.api.Assertions.assertThatNoException;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Bug 3 (enhancement): HQL grammar rejects {@code LATERAL unnest(...)} inside EXISTS and scalar
 * SELECT subqueries with {@code SyntaxException}, while accepting it as a JOIN target in the
 * outer FROM clause. The SQL:1999 standard permits {@code LATERAL} in any
 * {@code <table reference>} position.
 *
 * <p>Each test asserts the query parses and executes <strong>without exception</strong>. With
 * the grammar restriction present, the two subquery-form tests fail with
 * {@code org.hibernate.query.SyntaxException} during HQL parsing. The third (outer-FROM) test
 * passes today and is included for contrast — to confirm that {@code LATERAL unnest(...)} works
 * fine in the outer FROM. Once the grammar is extended to accept {@code LATERAL} function-table
 * references in subquery FROM clauses, the two failing tests will start passing.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HqlGrammarLateralUnnestInSubqueriesTest {

    SessionFactory sf;

    @Entity(name = "Item")
    public static class Item {
        @Id
        public int id;
        public int[] tags;
    }

    @BeforeAll
    void setUp() {
        sf = SupportInfra.buildSessionFactory(Item.class);
    }

    @AfterAll
    void tearDown() {
        if (sf != null) sf.close();
    }

    @Test
    void existsSubqueryWithLateralUnnest_parsesAndExecutes() {
        assertThatNoException()
                .isThrownBy(() -> sf.inSession(s -> s.createSelectionQuery(
                                "from Item i where exists (select 1 from lateral unnest(i.tags) t)", Item.class)
                        .getResultList()));
    }

    @Test
    void scalarSelectSubqueryWithLateralUnnest_parsesAndExecutes() {
        assertThatNoException()
                .isThrownBy(() -> sf.inSession(s -> s.createSelectionQuery(
                                "select i.id, (select count(*) from lateral unnest(i.tags) t) from Item i",
                                Object[].class)
                        .getResultList()));
    }

    @Test
    void outerFromWithLateralUnnest_parsesAndExecutes() {
        // For contrast: the same `LATERAL unnest(i.tags) t` parses cleanly when it's the
        // join target of the OUTER FROM clause. This test passes today; included to confirm
        // the grammar restriction is specific to subquery FROM clauses.
        assertThatNoException()
                .isThrownBy(() -> sf.inSession(s -> s.createSelectionQuery(
                                "from Item i join lateral unnest(i.tags) t", Item.class)
                        .getResultList()));
    }
}
