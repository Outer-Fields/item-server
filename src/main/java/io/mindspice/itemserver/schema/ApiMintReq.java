package io.mindspice.itemserver.schema;

import java.util.List;


public record ApiMintReq(
        String collection,
        List<ApiMint> mint_list
) {
}
