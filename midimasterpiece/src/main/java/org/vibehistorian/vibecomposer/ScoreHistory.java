package org.vibehistorian.vibecomposer;

import jm.music.data.Score;

import java.util.ArrayList;
import java.util.List;

/** Holds the scores available to the application's score history views. */
public final class ScoreHistory {
    public static final int LIMIT = 10;

    private static List<Score> scores = new ArrayList<>();

    private ScoreHistory() {
    }

    public static List<Score> getScores() {
        return scores;
    }

    public static void add(Score score) {
        scores.add(0, score);
        if (scores.size() > LIMIT) {
            scores = scores.subList(0, LIMIT);
        }
    }
}
