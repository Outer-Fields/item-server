package io.mindspice.itemserver.schema;

public enum PackType {
    BOOSTER(12),
    STARTER(39);

    public int cardAmount;

    PackType(int cardAmount) {
        this.cardAmount = cardAmount;
    }

}
