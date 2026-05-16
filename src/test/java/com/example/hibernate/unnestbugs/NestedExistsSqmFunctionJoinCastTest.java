package com.example.hibernate.unnestbugs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.Struct;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Bug 2: Hibernate's SQM conversion throws
 * {@link ClassCastException}{@code : SqmFunctionJoin cannot be cast to SqmSingularValuedJoin}
 * when an EXISTS subquery is nested inside another EXISTS subquery and the inner subquery's
 * FROM references a collection-valued path on the outer EXISTS's alias (which is itself bound
 * to a {@code lateral unnest} set-returning function).
 *
 * @see <a href="../../../../../../../../mongo-hibernate/docs/upstream-feedback/hibernate-bugs/nested-exists-sqm-function-join-cast.md">bug report</a>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NestedExistsSqmFunctionJoinCastTest {

    SessionFactory sf;

    @Embeddable
    @Struct(name = "Tax")
    public static class Tax {
        public String code;
        public double rate;
    }

    @Embeddable
    @Struct(name = "LineItem")
    public static class LineItem {
        public String sku;
        public Tax[] taxes;
    }

    @Entity(name = "OrderEntity")
    public static class OrderEntity {
        @Id
        public int id;
        public LineItem[] lineItems;
    }

    @BeforeAll
    void setUp() {
        // Uses PostgreSQLDialect (H2 lacks @Struct support). JDBC metadata access disabled —
        // no live PostgreSQL server required. The bug fires at SQM-to-SQL conversion, well
        // before any actual SQL execution.
        sf = SupportInfra.buildPostgresSessionFactory(OrderEntity.class);
    }

    @AfterAll
    void tearDown() {
        if (sf != null) sf.close();
    }

    @Test
    void nestedExistsOverNestedArray_throwsClassCastException() {
        // FROM OrderEntity o WHERE EXISTS (
        //   SELECT 1 FROM o.lineItems a WHERE EXISTS (
        //     SELECT 1 FROM a.taxes b WHERE b.code = 'VAT'))
        var thrown = catchThrowable(() -> sf.inSession(s -> s.createSelectionQuery(
                        "from OrderEntity o where exists (select 1 from o.lineItems a "
                                + "where exists (select 1 from a.taxes b where b.code = 'VAT'))",
                        OrderEntity.class)
                .getResultList()));
        assertThat(thrown).isNotNull();
        // The cast lives in Hibernate's SQM-to-SQL conversion code path for nested-subquery
        // correlation; the wrapping at the top-level may vary, so unwrap to root cause.
        assertThat(rootCauseOf(thrown))
                .as("expected ClassCastException for SqmFunctionJoin -> SqmSingularValuedJoin; got: %s",
                        rootCauseOf(thrown))
                .isInstanceOf(ClassCastException.class)
                .hasMessageContaining("SqmFunctionJoin")
                .hasMessageContaining("SqmSingularValuedJoin");
    }

    private static Throwable rootCauseOf(Throwable t) {
        var c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c;
    }
}
