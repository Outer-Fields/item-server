package io.mindspice.itemserver.services;

import io.mindspice.databaseservice.client.schema.Card;
import io.mindspice.databaseservice.client.schema.CardDomain;
import io.mindspice.itemserver.schema.PackPurchase;
import io.mindspice.jxch.transact.jobs.mint.MintItem;
import io.mindspice.jxch.transact.jobs.mint.MintService;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntPredicate;


public class PackMint implements Runnable {
    private final List<PackPurchase> packPurchases;
    private final MintService mintService;
    private final List<Card> cardList;
    private final Random rand;
    IntPredicate chance = threshold -> ThreadLocalRandom.current().nextInt(100) < threshold;

    public PackMint(List<Card> cardList, List<PackPurchase> packPurchases, MintService mintService) {
        this.packPurchases = packPurchases;
        this.mintService = mintService;
        this.cardList = cardList;
        rand = new Random();
    }

    @Override
    public void run() {
        List<MintItem> mintItems = new ArrayList<>();
        for (var pack : packPurchases) {
            List<Card> cards = new ArrayList<>();
            switch (pack.packType()) {
                case BOOSTER -> {
                    if (chance.test(25)) {
                        cards.addAll(CardSelect.getCards(cardList, 1, CardDomain.PAWN, null));
                    }
                    if (chance.test(50)) {
                        cards.addAll(CardSelect.getCards(cardList, 1, CardDomain.TALISMAN, null));
                    }
                    cards.addAll(
                            CardSelect.getCards(cardList, chance.test(50) ? 2 : 1, CardDomain.WEAPON, null)
                    );
                    cards.addAll(CardSelect.getCards(cardList,  chance.test(50) ? 2 : 1, CardDomain.POWER, null));
                    cards.addAll(
                            CardSelect.getCards(cardList, cards.size() == 3 ? (chance.test(50)? 4 : 3) : 3, CardDomain.ABILITY, null)
                    );
                    cards.addAll(
                            CardSelect.getCards(cardList, 12 - cards.size(), CardDomain.ACTION, null)
                    );
                }

                case STARTER -> {
                    List<Card> pawnCards = CardSelect.getCards(cardList, 3, CardDomain.PAWN, null);

                    for (Card pawn : pawnCards) {
                        cards.addAll(CardSelect.getCards(cardList, 2, CardDomain.WEAPON, pawn.type()));
                        cards.addAll(CardSelect.getCards(cardList, 1, CardDomain.TALISMAN, null));
                        cards.addAll(CardSelect.getCards(cardList, 3, CardDomain.POWER, null));
                        cards.addAll(CardSelect.getCards(cardList, 6, CardDomain.ACTION, pawn.type()));
                        cards.addAll(CardSelect.getCards(cardList, 6, CardDomain.ABILITY, null));
                    }
                    cards.addAll(pawnCards);
                }
            }
            for (Card card : cards) {
                MintItem item = new MintItem(pack.address(), card.metaData(), pack.uuid());
                mintItems.add(item);
            }
        }
        mintService.submit(mintItems);
    }
}
