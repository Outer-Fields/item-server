package io.mindspice.itemserver.schema;

public record NFTUpdate(
        String launcherId,
        String coinId,
        int height,
        boolean isAccount
) { }

