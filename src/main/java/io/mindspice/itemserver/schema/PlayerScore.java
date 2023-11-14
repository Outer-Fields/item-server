package io.mindspice.itemserver.schema;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;


public class PlayerScore {
    @JsonProperty("name") public final String playerName;
    @JsonProperty("won") public int wins = 0;
    @JsonProperty("lost") public int losses = 0;

    public PlayerScore(String playerName) {
        this.playerName = playerName;
    }

    @JsonProperty("win_ratio")
    public double getWinRatio() {
        if (wins == 0 && losses == 0) { return 0.0; }
        return BigDecimal.valueOf((double) wins / (wins + losses)).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }

    public void addResult(boolean isWin) {
        if (isWin) { wins++; } else { losses++; }
    }

    public int getWins() {
        return wins;
    }

    public double getSortMetric() {
        double winRatioWeight = 0.8;
        double numberOfGamesWeight = 0.2;
        int totalGames = wins + losses;
        return (getWinRatio() * winRatioWeight) + (totalGames * numberOfGamesWeight);
    }

    public String toString() {
        return "Player: " + playerName + "Wins: " + wins + " | Losses: " + losses + " | Win Ratio: " + getWinRatio();
    }
}
