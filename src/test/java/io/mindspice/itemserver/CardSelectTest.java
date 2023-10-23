package io.mindspice.itemserver;

import io.mindspice.databaseservice.client.DBServiceClient;
import io.mindspice.databaseservice.client.api.OkraNFTAPI;
import io.mindspice.databaseservice.client.schema.Card;
import io.mindspice.databaseservice.client.schema.CardDomain;
import io.mindspice.itemserver.services.CardSelect;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntPredicate;


public class CardSelectTest {
    IntPredicate chance = threshold -> ThreadLocalRandom.current().nextInt(100) < threshold;
    DBServiceClient dbClient = new DBServiceClient("http://192.168.10.30:8080", "user", "password");
    OkraNFTAPI nftApi = new OkraNFTAPI(dbClient);
    List<Card> cardList;



    private double[] lvlList = new double[]{2.2, 2.3, 2.4, 2.5, 2.6};

    public CardSelectTest() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        var listReqt = nftApi.getCardCollection("origins_collection");
        cardList = listReqt.data().get();
    }

    private  double getRandomLvl() {
        return lvlList[ThreadLocalRandom.current().nextInt(lvlList.length)];
    }

    @Test
    void boosterTest() {
        List<Card> cards = new ArrayList<>();
        List<Card> pawnCards = CardSelect.getCards(cardList, 3, CardDomain.PAWN, null);


        for (Card pawn : pawnCards) {
            cards.addAll(CardSelect.getCards(cardList, 2, CardDomain.WEAPON, pawn.type()));
            cards.addAll(CardSelect.getCards(cardList, 1, CardDomain.TALISMAN, null));
            var power = CardSelect.getCardsWithLimit(cardList, 3, CardDomain.POWER, null, getRandomLvl());
            var action = CardSelect.getCardsWithLimit(cardList, 6, CardDomain.ACTION, pawn.type(), getRandomLvl());
            var ability = CardSelect.getCardsWithLimit(cardList, 6, CardDomain.ABILITY, null, getRandomLvl());
            System.out.println(power.stream().mapToDouble(Card::level).sum() / 3);
            System.out.println(action.stream().mapToDouble(Card::level).sum() / 6);
            System.out.println(ability.stream().mapToDouble(Card::level).sum() / 6);
        }


    }

}
