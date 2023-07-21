package io.mindspice.itemserver.services;

import io.mindspice.itemserver.Schema.Card;
import io.mindspice.itemserver.Schema.CardDomain;
import io.mindspice.itemserver.Schema.CardType;
import io.mindspice.itemserver.Settings;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;


public class CardSelect {

    public static List<Card> getCards(List<Card> cards, int amount, CardDomain domain, CardType type) {
        List<Card> selectedCards = selectCards(cards, amount, domain, type);
        while (selectedCards.size() != amount) {
            selectedCards.addAll(selectCards(cards, amount - selectedCards.size(), domain, type));
        }
        return selectedCards;
    }

    // This could be more efficient without the stream, but a few extra stream iterations over the card list
    // shouldn't be too intensive, and this only get called on pack redemptions and isn't a hot code path.
    // Streams aid the readability of the logic.
    private static List<Card> selectCards(List<Card> cards, int amount, CardDomain domain, CardType type) {
        return IntStream.range(0, amount)
                .mapToObj(s -> selector.select(
                        cards.stream()
                                .filter(c -> c.domain() == domain)
                                .filter(c -> type == null || c.type() == type)
                                .filter(c -> ThreadLocalRandom.current().nextInt(0, 100) <= Settings.get().highLvl ? c.level() > 2 : c.level() < 3)
                                .filter(c -> (ThreadLocalRandom.current().nextInt(0, 100) <= Settings.get().goldPct) == c.isGold())
                                .filter(c -> (ThreadLocalRandom.current().nextInt(0, 100) <= Settings.get().holoPct) == c.isHolo())
                                .toList()
                )).filter(Objects::nonNull).toList();
    }

    public interface RandomSelector<T> {
        T select(List<T> items);
    }


    private static final RandomSelector<Card> selector = items -> {
        if (items.isEmpty()) { return null; }
        return items.get(ThreadLocalRandom.current().nextInt(items.size()));
    };
}
