package io.mindspice.itemserver.services;

import io.mindspice.databaseservice.client.api.OkraNFTAPI;
import io.mindspice.databaseservice.client.schema.Card;
import io.mindspice.databaseservice.client.schema.CardDomain;
import io.mindspice.itemserver.Settings;
import io.mindspice.itemserver.schema.PackPurchase;

import io.mindspice.jxch.transact.service.mint.MintItem;
import io.mindspice.jxch.transact.service.mint.MintService;
import io.mindspice.jxch.transact.logging.TLogLevel;
import io.mindspice.jxch.transact.logging.TLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntPredicate;


public class PackMint implements Runnable {
    private final List<PackPurchase> packPurchases;
    private final MintService mintService;
    private final List<Card> cardList;
    private final OkraNFTAPI nftAPI;
    private final Random rand;
    private final TLogger logger;
    private double[] lvlList = new double[]{2.2, 2.3, 2.4, 2.5, 2.6};
    IntPredicate chance = threshold -> ThreadLocalRandom.current().nextInt(100) < threshold;

    public PackMint(List<Card> cardList, List<PackPurchase> packPurchases, MintService mintService, OkraNFTAPI nftAPI
            , TLogger logger) {
        this.packPurchases = packPurchases;
        this.mintService = mintService;
        this.nftAPI = nftAPI;
        this.cardList = cardList;
        this.logger = logger;
        rand = new Random();
    }

    private  double getRandomLvl() {
        return lvlList[ThreadLocalRandom.current().nextInt(lvlList.length)];
    }

    @Override
    public void run() {
        List<MintItem> mintItems = new ArrayList<>(packPurchases.stream().mapToInt(p -> p.packType().cardAmount).sum());
        try (var virtualExec = Executors.newVirtualThreadPerTaskExecutor();) {
            for (var pack : packPurchases) {
                try {
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
                            cards.addAll(CardSelect.getCards(cardList, chance.test(50) ? 2 : 1, CardDomain.POWER, null));
                            cards.addAll(
                                    CardSelect.getCards(cardList, cards.size() == 3 ? (chance.test(50) ? 4 : 3) : 3, CardDomain.ABILITY, null)
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
                                cards.addAll(CardSelect.getCardsWithLimit(cardList, 3, CardDomain.POWER, null, getRandomLvl()));
                                cards.addAll(CardSelect.getCardsWithLimit(cardList, 6, CardDomain.ACTION, pawn.type(), getRandomLvl()));
                                cards.addAll(CardSelect.getCardsWithLimit(cardList, 6, CardDomain.ABILITY, null, getRandomLvl()));
                            }
                            cards.addAll(pawnCards);
                        }
                    }
                    CountDownLatch latch = new CountDownLatch(cards.size());
                    List<MintItem> packMints = Collections.synchronizedList(new ArrayList<>(cards.size()));
                    var failed = new AtomicBoolean(false);
                    for (Card card : cards) {
                        virtualExec.submit(() -> {
                            try {
                                var edt = nftAPI.getAndIncEdt(Settings.get().currCollection, card.uid()).data().orElseThrow();
                                var updatedMeta = card.metaData().cloneSetEdt(edt);
                                MintItem item = new MintItem(pack.address(), updatedMeta, pack.uuid());
                                packMints.add(item);
                            } catch (Exception e) {
                                logger.log(this.getClass(), TLogLevel.ERROR, "Virtual thread from card calculation for card: " + card.uid(), e);
                                failed.set(true);
                            } finally {
                                latch.countDown();
                            }
                        });
                    }
                    latch.await();
                    mintItems.addAll(packMints);
                    if (failed.get()) {
                        logger.log(this.getClass(), TLogLevel.FAILED, "Error encountered while generating card pack: " + pack +
                                " | Pack not minted: virtual thread threw exception");
                    }
                } catch (Exception e) {
                    logger.log(this.getClass(), TLogLevel.FAILED, "Error encountered while generating card pack: " + pack
                            + " | Pack not minted: pack calculations threw exception", e);
                }
            }
            mintService.submit(mintItems);
        }
    }
}
