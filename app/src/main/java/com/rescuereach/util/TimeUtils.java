package com.rescuereach.util;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for time operations and formatting
 */
public class TimeUtils {

    /**
     * Get a formatted string describing the time elapsed since the given date
     * @param date The date to compare with current time
     * @param context The context for string resources
     * @return A string like "2 minutes ago", "just now", etc.
     */
    public static String getTimeAgo(Date date, Context context) {
        if (date == null) {
            return "unknown time";
        }

        long timeDifference = System.currentTimeMillis() - date.getTime();
        long seconds = TimeUnit.MILLISECONDS.toSeconds(timeDifference);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeDifference);
        long hours = TimeUnit.MILLISECONDS.toHours(timeDifference);
        long days = TimeUnit.MILLISECONDS.toDays(timeDifference);

        if (seconds < 60) {
            return "just now";
        } else if (minutes < 60) {
            return minutes == 1 ? "1 minute ago" : minutes + " minutes ago";
        } else if (hours < 24) {
            return hours == 1 ? "1 hour ago" : hours + " hours ago";
        } else if (days < 7) {
            return days == 1 ? "yesterday" : days + " days ago";
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.getDefault());
            return sdf.format(date);
        }
    }

    /**
     * Format a duration in milliseconds to a human-readable string
     * @param durationMs Duration in milliseconds
     * @return Formatted string like "2h 15m" or "45s"
     */
    public static String formatDuration(long durationMs) {
        long seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60;
        long hours = TimeUnit.MILLISECONDS.toHours(durationMs);

        if (hours > 0) {
            return String.format(Locale.US, "%dh %dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format(Locale.US, "%dm %ds", minutes, seconds);
        } else {
            return String.format(Locale.US, "%ds", seconds);
        }
    }

    /**
     * Format a date to a specified pattern
     * @param date The date to format
     * @param pattern The pattern (like "yyyy-MM-dd HH:mm:ss")
     * @return The formatted date string
     */
    public static String formatDate(Date date, String pattern) {
        if (date == null) {
            return "";
        }

        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
        return sdf.format(date);
    }

    /**
     * Get a formatted timestamp in ISO format
     * @param date The date
     * @return ISO formatted date string
     */
    public static String getISOTimestamp(Date date) {
        if (date == null) {
            date = new Date();
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        return sdf.format(date);
    }
}