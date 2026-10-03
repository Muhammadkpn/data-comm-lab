package com.learn.datacomm.graphql.resolver;

import com.learn.datacomm.graphql.model.TransferResult;
import com.learn.datacomm.graphql.store.AccountStore;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

/**
 * ==== MUTATION RESOLVER ====
 * @MutationMapping vs @QueryMapping: perbedaannya konvensi, bukan aturan
 * teknis yang dipaksakan protokol (beda dengan REST yang membedakan
 * GET/POST/PUT/DELETE secara semantik HTTP). Yang membuatnya penting:
 * GraphQL executor menjalankan field-field di bawah "mutation { ... }"
 * secara BERURUTAN (bukan paralel seperti field di bawah "query { ... }"),
 * supaya efek samping berurutan dan predictable kalau client mengirim
 * lebih dari satu mutation dalam satu request.
 */
@Controller
public class TransferMutationResolver {

    private final AccountStore accountStore;

    public TransferMutationResolver(AccountStore accountStore) {
        this.accountStore = accountStore;
    }

    @MutationMapping
    public TransferResult transfer(@Argument String sourceAccountId,
                                    @Argument String destinationAccountId,
                                    @Argument long amount,
                                    @Argument String referenceId) {
        System.out.println("[MUTATION] transfer: " + sourceAccountId + " -> " + destinationAccountId +
                ", amount=" + amount + ", referenceId=" + referenceId);

        AccountStore.TransferOutcome outcome = accountStore.applyTransfer(
                sourceAccountId, destinationAccountId, amount, referenceId);

        if (!outcome.isSuccess()) {
            System.out.println("[MUTATION] Ditolak: " + outcome.getMessage());
            return new TransferResult(referenceId, "REJECTED", outcome.getMessage(), null, null);
        }

        System.out.println("[MUTATION] Transfer sukses");
        return new TransferResult(referenceId, "SUCCESS", outcome.getMessage(),
                outcome.getSourceAccount(), outcome.getDestinationAccount());
    }
}
