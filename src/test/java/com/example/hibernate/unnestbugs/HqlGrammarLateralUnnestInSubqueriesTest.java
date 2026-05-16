package com.example.hibernate.unnestbugs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowable;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.hibernate.query.SyntaxException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Bug 3 (enhancement): HQL grammar rejects {@code LATERAL unnest(...)} inside EXISTS and scalar
 * SELECT subqueries with {@link SyntaxException}, while accepting it as a JOIN target in the
 * outer FROM clause. The SQL:1999 standard permits {@code LATERAL} in any
 * {@code <table reference>} position.
 *
 * @see <a href="../../../../../../../../mongo-hibernate/docs/upstream-feedback/hibernate-bugs/hql-grammar-lateral-unnest-in-subqueries.md">bug report</a>
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
    void existsSubqueryWithLateralUnnest_doesNotParse() {
        var thrown = catchThrowable(() -> sf.inSession(s -> s.createSelectionQuery(
                        "from Item i where exists (select 1 from lateral unnest(i.tags) t)",
                        Item.class)
                .getResultList()));
        assertThat(thrown).isNotNull();
        assertThat(rootCauseOf(thrown))
                .as("expected SyntaxException at `(` after `unnest` in subquery; got: %s",
                        rootCauseOf(thrown))
                .isInstanceOf(SyntaxException.class);
    }

    @Test
    void scalarSelectSubqueryWithLateralUnnest_doesNotParse() {
        var thrown = catchThrowable(() -> sf.inSession(s -> s.createSelectionQuery(
                        "select i.id, (select count(*) from lateral unnest(i.tags) t) from Item i",
                        Object[].class)
                .getResultList()));
        assertThat(thrown).isNotNull();
        assertThat(rootCauseOf(thrown)).isInstanceOf(SyntaxException.class);
    }

    @Test
    void outerFromWithLateralUnnest_parses() {
        // For contrast: the same `LATERAL unnest(i.tags) t` parses cleanly when it's the
        // join target of the OUTER FROM clause. The grammar restriction is therefore specific
        // to subquery FROM clauses.
        assertThatNoException()
                .isThrownBy(() -> sf.inSession(s -> s.createSelectionQuery(
                                "from Item i join lateral unnest(i.tags) t", Item.class)
                        .getResultList()));
    }

    private static Throwable rootCauseOf(Throwable t) {
        var c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c;
    }
}
