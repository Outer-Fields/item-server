package io.mindspice.itemserver.schema;

public record PackPurchase(
        String address,
        PackType packType,
        String uuid,
        int height,
        String coinId
) {
}
