package com.learn.datacomm.graphql;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@AutoConfigureGraphQlTester
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class GraphqlDemoApplicationTest {

    @Autowired
    private GraphQlTester tester;

    @Test
    void clientHanyaMenerimaFieldYangDiminta() {
        tester.document("{ account(id: \"ACC-001\") { ownerName } }")
                .execute()
                .path("account.ownerName").entity(String.class).isEqualTo("Andi Wijaya")
                .path("account.balance").pathDoesNotExist();
    }

    @Test
    void nestedTransactionsLewatDataLoaderDenganLimit() {
        tester.document("{ accounts { id transactions(limit: 2) { id } } }")
                .execute()
                .path("accounts").entityList(Object.class).hasSize(4)
                .path("accounts[0].transactions").entityList(Object.class).hasSize(2);
    }

    @Test
    void mutationTransferMengembalikanSaldoTerbaru() {
        tester.document("mutation { transfer(sourceAccountId: \"ACC-001\", destinationAccountId: \"ACC-002\", "
                        + "amount: 1000000, referenceId: \"REF-GQL-T\") { status sourceAccount { balance } "
                        + "destinationAccount { balance } } }")
                .execute()
                .path("transfer.status").entity(String.class).isEqualTo("SUCCESS")
                .path("transfer.sourceAccount.balance").entity(Integer.class).isEqualTo(4_000_000)
                .path("transfer.destinationAccount.balance").entity(Integer.class).isEqualTo(4_250_000);
    }

    @Test
    void mutationRekeningSamaDitolak() {
        String status = tester.document("mutation { transfer(sourceAccountId: \"ACC-001\", "
                        + "destinationAccountId: \"ACC-001\", amount: 1, referenceId: \"R\") { status } }")
                .execute()
                .path("transfer.status").entity(String.class).get();
        assertEquals("REJECTED", status);
    }
}
