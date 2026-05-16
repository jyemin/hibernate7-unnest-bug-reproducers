package com.example.hibernate.unnestbugs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Bug 1: {@link org.hibernate.query.sqm.internal.SqmMappingModelHelper}{@code .resolveSqmPath}
 * throws a bare {@link AssertionError} when resolving a path through a {@code FunctionJoin} /
 * {@code SqmFunctionJoin} (i.e., an alias bound to {@code lateral unnest(...)}).
 *
 * <p>Demonstrated via four HQL forms that all trip the same failure, against H2 + Hibernate
 * 7.3.4.Final:
 *
 * <ol>
 *   <li>Body predicate on a {@code lateral unnest} JOIN alias.
 *   <li>Body predicate on a basic-plural-attribute JOIN sugar alias.
 *   <li>EXISTS-subquery body predicate referencing an unnest alias.
 *   <li>IN-subquery whose projected expression is on an unnest alias (forces type inference
 *       to resolve through the function-join path).
 * </ol>
 *
 * @see <a href="../../../../../../../../mongo-hibernate/docs/upstream-feedback/hibernate-bugs/sqm-resolve-function-join-path.md">bug report</a>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqmResolveFunctionJoinPathTest {

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
    void scalarJoin_lateralUnnest_withBodyPredicate_throwsAssertionError() {
        // FROM Item i JOIN LATERAL unnest(i.tags) t WHERE t > 5
        var thrown = catchThrowable(() -> sf.inSession(s -> s.createSelectionQuery(
                        "from Item i join lateral unnest(i.tags) t where t > 5", Item.class)
                .getResultList()));
        assertThat(thrown).isNotNull();
        assertThat(rootCauseOf(thrown))
                .as("expected AssertionError from SqmMappingModelHelper.resolveSqmPath; got: %s",
                        rootCauseOf(thrown))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void scalarJoin_sugarForm_withBodyPredicate_throwsAssertionError() {
        // FROM Item i JOIN i.tags t WHERE t > 5  (Hibernate 7's "basic plural attribute" join sugar)
        var thrown = catchThrowable(() -> sf.inSession(
                s -> s.createSelectionQuery("from Item i join i.tags t where t > 5", Item.class)
                        .getResultList()));
        assertThat(thrown).isNotNull();
        assertThat(rootCauseOf(thrown)).isInstanceOf(AssertionError.class);
    }

    @Test
    void scalarExists_implicitCollectionPath_throwsAssertionError() {
        // FROM Item i WHERE EXISTS (SELECT 1 FROM i.tags t WHERE t > 5)
        var thrown = catchThrowable(() -> sf.inSession(s -> s.createSelectionQuery(
                        "from Item i where exists (select 1 from i.tags t where t > 5)", Item.class)
                .getResultList()));
        assertThat(thrown).isNotNull();
        assertThat(rootCauseOf(thrown)).isInstanceOf(AssertionError.class);
    }

    @Test
    void inSubquery_overUnnest_throwsAssertionError() {
        // FROM Item i WHERE 5 IN (SELECT t FROM i.tags t)
        var thrown = catchThrowable(() -> sf.inSession(s -> s.createSelectionQuery(
                        "from Item i where 5 in (select t from i.tags t)", Item.class)
                .getResultList()));
        assertThat(thrown).isNotNull();
        assertThat(rootCauseOf(thrown)).isInstanceOf(AssertionError.class);
    }

    private static Throwable rootCauseOf(Throwable t) {
        var c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c;
    }
}
