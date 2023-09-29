package io.mindspice.itemserver;

import io.mindspice.databaseservice.client.schema.Card;
import io.mindspice.databaseservice.client.schema.CardDomain;
import io.mindspice.itemserver.services.CardSelect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntPredicate;


@SpringBootTest
public class CardSelectTest {
    @Autowired
    List<Card> cardList;
    IntPredicate chance = threshold -> ThreadLocalRandom.current().nextInt(100) < threshold;


    @Test
    void boosterTest() {
        List<Card> cards = new ArrayList<>();
        List<Card> pawnCards = CardSelect.getCards(cardList, 3, CardDomain.PAWN, null);

        for (Card pawn : pawnCards) {
            cards.addAll(CardSelect.getCards(cardList, 2, CardDomain.WEAPON, pawn.type()));
            cards.addAll(CardSelect.getCards(cardList, 1, CardDomain.TALISMAN, null));
            cards.addAll(CardSelect.getCards(cardList, 3, CardDomain.POWER, null));
            cards.addAll(CardSelect.getCards(cardList, 6, CardDomain.ACTION, pawn.type()));
            cards.addAll(CardSelect.getCards(cardList, 6, CardDomain.ABILITY, null));
        }
        cards.addAll(pawnCards);
        System.out.println(cards.size());
    }
}
