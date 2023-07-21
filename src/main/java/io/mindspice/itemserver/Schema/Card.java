package io.mindspice.itemserver.Schema;

import io.mindspice.jxch.rpc.schemas.wallet.nft.MetaData;


public record Card(
        String uid,
        int level,
        CardDomain domain,
        CardType type,
        boolean isGold,
        boolean isHolo,
        MetaData metaData
) {
}
