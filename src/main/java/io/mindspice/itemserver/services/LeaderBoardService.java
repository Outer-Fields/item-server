package io.mindspice.itemserver.services;

import io.mindspice.databaseservice.client.api.OkraGameAPI;
import io.mindspice.databaseservice.client.schema.MatchResult;
import io.mindspice.itemserver.schema.PlayerScore;
import io.mindspice.itemserver.util.CustomLogger;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;


public class LeaderBoardService {
    private final OkraGameAPI gameAPI;
    public final CustomLogger logger;

    private volatile List<PlayerScore> dailyScores;
    private volatile List<PlayerScore> weeklyScores;
    private volatile List<PlayerScore> monthlyScores;

    public LeaderBoardService(OkraGameAPI gameAPI, CustomLogger logger) {
        this.gameAPI = gameAPI;
        this.logger = logger;
    }

    public Runnable updateScores() {
        return () -> {
            java.time.LocalDate day = java.time.LocalDate.now();
            List<MatchResult> dailyResults = gameAPI.getMatchResults(
                    day.atStartOfDay(ZoneId.systemDefault()).minusDays(1).toInstant().toEpochMilli(), //this is right, but workish
                    day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            ).data().orElseThrow();
            dailyScores = calcAndSortResults(dailyResults);

            java.time.LocalDate week = java.time.LocalDate.now().with(DayOfWeek.MONDAY);
            List<MatchResult> weeklyResults = gameAPI.getMatchResults(
                    week.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    week.plusWeeks(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            ).data().orElseThrow();
            weeklyScores = calcAndSortResults(weeklyResults);

            java.time.LocalDate month = LocalDate.now().withDayOfMonth(1);
            List<MatchResult> monthlyResult = gameAPI.getMatchResults(
                    month.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    month.plusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            ).data().orElseThrow();
            monthlyScores = calcAndSortResults(monthlyResult);
        };
    }

    private List<PlayerScore> calcAndSortResults(List<MatchResult> results) {
        HashMap<String, PlayerScore> interimMap = new HashMap<>();
        results.forEach(result -> {
                    interimMap.computeIfAbsent(result.player1Name(),
                            k -> new PlayerScore(result.player1Name())).addResult(result.player1Won()
                    );
                    interimMap.computeIfAbsent(result.player2Name(),
                            k -> new PlayerScore(result.player2Name())).addResult(result.player2Won()
                    );
                }
        );
        interimMap.remove("BramBot");
        return interimMap.values().stream()
                .sorted(Comparator.comparing(PlayerScore::getSortMetric).reversed())
                .limit(35)
                .toList();
    }

    public List<PlayerScore> getDailyScores() { return dailyScores; }

    public List<PlayerScore> getWeeklyScores() { return weeklyScores; }

    public List<PlayerScore> getMonthlyScores() { return monthlyScores; }
}
