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
 * HQL that references a basic-typed array element in a body predicate fails with
 * {@code java.lang.AssertionError} during SQM-to-SQL conversion.
 *
 * <p>Affected forms (all desugar to the same {@code SqmFunctionJoin} AST for a
 * {@code lateral unnest(...)} of a basic plural attribute):
 *
 * <ol>
 *   <li>Sugar JOIN with body predicate: {@code from Item i join i.tags t where t > 5}
 *   <li>Implicit collection path inside EXISTS: {@code where exists (select 1 from i.tags t where t > 5)}
 *   <li>IN-subquery over implicit collection path: {@code where 5 in (select t from i.tags t)}
 *   <li>Explicit {@code lateral unnest(...)} JOIN: {@code from Item i join lateral unnest(i.tags) t where t > 5}
 * </ol>
 *
 * <p>Each test asserts the query executes without exception. Against Hibernate 7.3.4.Final all
 * four fail; when the bug is fixed the tests transition from failing to passing.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BasicArrayBodyPredicateTest {

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
    void scalarJoin_sugarForm_withBodyPredicate_succeeds() {
        // Natural HQL: FROM Item i JOIN i.tags t WHERE t > 5
        assertThatNoException()
                .isThrownBy(() -> sf.inSession(s -> s.createSelectionQuery(
                                "from Item i join i.tags t where t > 5", Item.class)
                        .getResultList()));
    }

    @Test
    void scalarExists_implicitCollectionPath_succeeds() {
        // Natural HQL: FROM Item i WHERE EXISTS (SELECT 1 FROM i.tags t WHERE t > 5)
        assertThatNoException()
                .isThrownBy(() -> sf.inSession(s -> s.createSelectionQuery(
                                "from Item i where exists (select 1 from i.tags t where t > 5)", Item.class)
                        .getResultList()));
    }

    @Test
    void inSubquery_overImplicitCollectionPath_succeeds() {
        // Natural HQL: FROM Item i WHERE 5 IN (SELECT t FROM i.tags t)
        assertThatNoException()
                .isThrownBy(() -> sf.inSession(s -> s.createSelectionQuery(
                                "from Item i where 5 in (select t from i.tags t)", Item.class)
                        .getResultList()));
    }

    @Test
    void scalarJoin_lateralUnnest_withBodyPredicate_succeeds() {
        // Explicit `lateral unnest` form (same desugaring as the sugar JOIN):
        //   FROM Item i JOIN LATERAL unnest(i.tags) t WHERE t > 5
        assertThatNoException()
                .isThrownBy(() -> sf.inSession(s -> s.createSelectionQuery(
                                "from Item i join lateral unnest(i.tags) t where t > 5", Item.class)
                        .getResultList()));
    }
}
