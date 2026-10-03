package dev.by1337.baf.util;

/** Formats a duration in seconds using the same two-unit layout as BAuctionV2. */
public final class DurationFormatter {
    private DurationFormatter() {}

    public static String formatSeconds(long seconds) {
        seconds = Math.max(0, seconds);
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long months = days / 30;
        long years = months / 12;
        seconds %= 60;
        minutes %= 60;
        hours %= 24;
        days %= 30;
        months %= 12;
        if (years != 0) return format("years", years, "months", months);
        if (months != 0) return format("months", months, "days", days);
        if (days != 0) return format("days", days, "hours", hours);
        if (hours != 0) return format("hours", hours, "minutes", minutes);
        if (minutes != 0) return format("minutes", minutes, "seconds", seconds);
        return "<lang:bairdropflow.time.format.seconds:" + seconds + ">";
    }

    private static String format(String unit, long value, String nextUnit, long nextValue) {
        return "<lang:bairdropflow.time.format." + unit + (nextValue == 0 ? ":" + value : "." + nextUnit + ":" + value + ":" + nextValue) + ">";
    }
}
