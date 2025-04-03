package com.rescuereach.citizen.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ExpandableListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.rescuereach.R;
import com.rescuereach.citizen.adapters.FaqExpandableListAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HelpSupportFragment extends Fragment {

    private ExpandableListView expandableListView;
    private Button contactSupportButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_help_support, container, false);

        expandableListView = view.findViewById(R.id.expandable_list_view);
        contactSupportButton = view.findViewById(R.id.button_contact_support);

        setupFaq();
        setupContactButton();

        return view;
    }

    private void setupFaq() {
        List<String> faqTitles = new ArrayList<>();
        Map<String, List<String>> faqContent = new HashMap<>();

        // Add FAQ items
        faqTitles.add("How do I report an emergency?");
        List<String> reportingSteps = new ArrayList<>();
        reportingSteps.add("1. Tap the red \"Report Emergency\" button\n2. Select the emergency type\n3. Add details and location information\n4. Submit your report");
        faqContent.put(faqTitles.get(0), reportingSteps);

        faqTitles.add("What happens after I report an emergency?");
        List<String> afterReporting = new ArrayList<>();
        afterReporting.add("After submitting, your report will be sent to the appropriate emergency responders. You'll be able to track the status of your report and receive updates as responders take action.");
        faqContent.put(faqTitles.get(1), afterReporting);

        faqTitles.add("How do I update my profile information?");
        List<String> profileUpdates = new ArrayList<>();
        profileUpdates.add("1. Open the navigation menu\n2. Tap on \"Profile\"\n3. Tap the \"Edit Profile\" button\n4. Update your information and tap \"Update\"");
        faqContent.put(faqTitles.get(2), profileUpdates);

        faqTitles.add("What if I don't have internet connection?");
        List<String> offlineInfo = new ArrayList<>();
        offlineInfo.add("RescueReach supports offline emergency reporting. The app will automatically switch to SMS mode when no internet connection is available, ensuring your emergency is still reported.");
        faqContent.put(faqTitles.get(3), offlineInfo);

        faqTitles.add("Is my data secure?");
        List<String> security = new ArrayList<>();
        security.add("Yes, RescueReach takes data security seriously. We use industry-standard encryption for all data transmission and storage. Your personal information is only shared with authorized emergency personnel when needed for emergency response.");
        faqContent.put(faqTitles.get(4), security);

        FaqExpandableListAdapter adapter = new FaqExpandableListAdapter(requireContext(), faqTitles, faqContent);
        expandableListView.setAdapter(adapter);
    }

    private void setupContactButton() {
        contactSupportButton.setOnClickListener(v -> {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse("mailto:support@rescuereach.com"));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "RescueReach App Support");

            if (emailIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
                startActivity(emailIntent);
            }
        });
    }
}