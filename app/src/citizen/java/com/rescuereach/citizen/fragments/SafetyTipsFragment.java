package com.rescuereach.citizen.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.rescuereach.R;
import com.rescuereach.citizen.adapters.SafetyTipAdapter;
import com.rescuereach.data.model.SafetyTip;

import java.util.ArrayList;
import java.util.List;

public class SafetyTipsFragment extends Fragment {

    private RecyclerView recyclerView;
    private SafetyTipAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_safety_tips, container, false);

        recyclerView = view.findViewById(R.id.recycler_safety_tips);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        setupRecyclerView();

        return view;
    }

    private void setupRecyclerView() {
        List<SafetyTip> tips = new ArrayList<>();

        // Add some sample safety tips
        tips.add(new SafetyTip(
                "Fire Safety",
                "• Keep a fire extinguisher at home\n• Know your evacuation route\n• Never leave cooking unattended\n• Test smoke alarms monthly",
                R.drawable.ic_fire));

        tips.add(new SafetyTip(
                "Medical Emergency",
                "• Learn basic CPR\n• Keep a first aid kit accessible\n• Know important emergency numbers\n• Be aware of nearest hospitals",
                R.drawable.ic_medical));

        tips.add(new SafetyTip(
                "Natural Disaster",
                "• Have an emergency kit ready\n• Create a family emergency plan\n• Stay informed about weather alerts\n• Know community evacuation routes",
                R.drawable.ic_disaster));

        tips.add(new SafetyTip(
                "Personal Safety",
                "• Always be aware of your surroundings\n• Share your location with trusted contacts\n• Keep emergency contacts easily accessible\n• Trust your instincts",
                R.drawable.ic_person));

        adapter = new SafetyTipAdapter(tips);
        recyclerView.setAdapter(adapter);
    }
}