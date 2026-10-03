package com.learn.datacomm.graphql.resolver;

import com.learn.datacomm.graphql.model.Account;
import com.learn.datacomm.graphql.store.AccountStore;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * ==== QUERY RESOLVER ====
 * @QueryMapping menghubungkan method ini ke field di type Query pada
 * schema.graphqls (dicocokkan lewat nama method, atau lewat atribut
 * value="..." kalau namanya beda). Beda dengan REST controller yang
 * routing-nya lewat @GetMapping("/path"), di sini SATU controller bisa
 * menjawab banyak "operasi" tanpa path terpisah -- semuanya lewat POST
 * /graphql, dibedakan oleh isi field yang diminta di query string.
 *
 * ==== INI YANG MENUNJUKKAN KEUNGGULAN GRAPHQL DIBANDING REST ====
 * Resolver ini TIDAK PEDULI field apa saja yang diminta client -- dia
 * selalu mengembalikan objek Account penuh. Spring for GraphQL yang akan
 * memanggil getter sesuai field yang client minta di query (id,
 * ownerName, balance, dan/atau transactions). Kalau client cuma minta
 * "ownerName balance", getter transactions() TIDAK dipanggil sama sekali
 * -- tidak perlu endpoint REST terpisah untuk "ringkasan" vs "detail".
 */
@Controller
public class AccountQueryResolver {

    private final AccountStore accountStore;

    public AccountQueryResolver(AccountStore accountStore) {
        this.accountStore = accountStore;
    }

    @QueryMapping
    public Account account(@Argument String id) {
        System.out.println("[QUERY] account(id=" + id + ")");
        return accountStore.findById(id);
    }

    @QueryMapping
    public List<Account> accounts() {
        System.out.println("[QUERY] accounts() -- 1 pemanggilan untuk semua akun");
        return accountStore.findAll();
    }
}
