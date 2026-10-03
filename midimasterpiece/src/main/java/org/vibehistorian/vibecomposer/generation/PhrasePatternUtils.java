package org.vibehistorian.vibecomposer.generation;

import java.util.ArrayList;
import java.util.List;

/** Shared slicing rules for instrument patterns split across chord spans. */
final class PhrasePatternUtils {
    private PhrasePatternUtils() {
    }

    static <T> List<T> partOfListClean(int part, int partCount, List<T> list) {
        double preciseDivision = list.size() / (double) partCount;
        int start = (int) Math.round(preciseDivision * part);
        int end = (int) Math.round(preciseDivision * (part + 1));
        return list.subList(start >= 0 ? start : 0, end < list.size() ? end : list.size());
    }

    static <T> List<T> partOfList(int part, int partCount, List<T> list) {
        if (partCount == 1) {
            return list;
        }
        double size = Math.ceil(list.size() / ((double) partCount));
        List<T> returnList = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (i >= part * size && i <= (part + 1) * size) {
                returnList.add(list.get(i));
            }
        }
        return returnList;
    }

    static int countStartingValueInList(int stretchedByNote, List<Integer> nextPattern) {
        int counter = 0;
        while (counter < nextPattern.size()) {
            if (nextPattern.get(counter) == stretchedByNote || nextPattern.get(counter) == -1) {
                counter++;
            } else {
                break;
            }
        }
        return counter;
    }
}
