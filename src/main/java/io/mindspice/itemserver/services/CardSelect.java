package io.mindspice.itemserver.services;

import io.mindspice.databaseservice.client.schema.Card;
import io.mindspice.databaseservice.client.schema.CardDomain;
import io.mindspice.databaseservice.client.schema.CardType;

import io.mindspice.itemserver.Settings;
import io.mindspice.jxch.transact.logging.TLogLevel;
import io.mindspice.jxch.transact.util.Pair;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class CardSelect {
    private static HashMap<Pair<Double, Integer>, List<List<Integer>>> randomCombCache = new HashMap<>();

    public static List<Card> getCards(List<Card> cards, int amount, CardDomain domain, CardType type) {
        List<Card> selectedCards = selectCards(cards, amount, domain, type);
        int i= 0;
        while (selectedCards.size() < amount) {
            selectedCards.addAll(selectCards(cards, amount - selectedCards.size(), domain, type));
            i++;
            if (i > 200) { throw new IllegalStateException("Endless loop in card gen, shouldn't happen"); }
        }
        return selectedCards;
    }

    public static List<Card> getCardsWithLimit(List<Card> cards, int amount, CardDomain domain, CardType type, double lvlLimit) {
        List<Card> selectionList = new ArrayList<>(amount);
        int i = 0;
        int j = 0;
        var lvlList = getRandomCardCombination(amount, lvlLimit);
        while (selectionList.size() != amount) {
            Card selected = selectCardLvl(cards, domain, type, lvlList.get(i));
            if (selected != null) {
                selectionList.add(selected);
                i++;
            }
            j++;
            if (j > 200) { throw new IllegalStateException("Endless loop in card gen, shouldn't happen"); }
        }
        return selectionList;
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
                                //.filter(c -> (ThreadLocalRandom.current().nextInt(0, 100) <= Settings.get().holoPct) == c.isHolo()) //no holo atm
                                .toList()
                )).filter(Objects::nonNull).collect(Collectors.toCollection(ArrayList::new));
    }

    private static Card selectCardLvl(List<Card> cards, CardDomain domain, CardType type, int lvl) {
        return selector.select(cards.stream()
                .filter(c -> c.domain() == domain)
                .filter(c -> type == null || c.type() == type)
                .filter((c -> c.level() == lvl))
                .filter(c -> (ThreadLocalRandom.current().nextInt(0, 100) <= Settings.get().goldPct) == c.isGold())
                //.filter(c -> (ThreadLocalRandom.current().nextInt(0, 100) <= Settings.get().holoPct) == c.isHolo()) //no holo atm
                .toList());
    }

    public interface RandomSelector<T> {
        T select(List<T> items);
    }


    private static final RandomSelector<Card> selector = items -> {
        if (items.isEmpty()) { return null; }
        return items.get(ThreadLocalRandom.current().nextInt(items.size()));
    };

    private static List<List<Integer>> generateCombination(int n, int m, int start, List<Integer> current, List<List<Integer>> result) {
        if (current.size() == n && m == 0) {
            result.add(new ArrayList<>(current));
            return result;
        }
        for (int i = start; i <= 4; i++) {
            if (m - i >= 0 && current.size() < n) {
                current.add(i);
                generateCombination(n, m - i, i, current, result);
                current.remove(current.size() - 1); // backtrack
            }
        }
        return result;
    }

    private static List<Integer> getRandomCardCombination(int amount, double lvlLimit) {
        final int sum = (int) (amount * lvlLimit);
        final var keyPair = new Pair<>(lvlLimit, amount);
        var combList = randomCombCache.computeIfAbsent(
                new Pair<>(lvlLimit, amount),
                key -> generateCombination(amount, sum, 1, new ArrayList<>(), new ArrayList<>())
        );
        randomCombCache.get(keyPair);
        return combList.get(ThreadLocalRandom.current().nextInt(combList.size()));

    }

}
