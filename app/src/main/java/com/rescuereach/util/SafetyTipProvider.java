package com.rescuereach.util;

import android.content.Context;

import com.rescuereach.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Provides safety tips to display in the app
 */
public class SafetyTipProvider {
    private static final Random random = new Random();
    private static List<String> safetyTips;

    /**
     * Get a random safety tip
     * @param context Context to get string resources
     * @return Random safety tip as a string
     */
    public static String getRandomSafetyTip(Context context) {
        if (safetyTips == null) {
            initializeTips(context);
        }

        int index = random.nextInt(safetyTips.size());
        return safetyTips.get(index);
    }

    /**
     * Initialize the list of safety tips
     */
    private static void initializeTips(Context context) {
        safetyTips = new ArrayList<>();

        // Add tips from resources
        String[] tipsArray = context.getResources().getStringArray(R.array.safety_tips);
        for (String tip : tipsArray) {
            safetyTips.add(tip);
        }

        // If no tips were found, add some default ones
        if (safetyTips.isEmpty()) {
            safetyTips.add("In emergency situations, remain calm and provide clear information to help responders reach you quickly.");
            safetyTips.add("Save important emergency contacts in your phone for quick access.");
            safetyTips.add("Create a family emergency plan and ensure everyone knows what to do in case of various emergencies.");
            safetyTips.add("Keep a first aid kit at home, in your car, and at your workplace.");
            safetyTips.add("Learn basic first aid and CPR - these skills could save a life.");
        }
    }
}