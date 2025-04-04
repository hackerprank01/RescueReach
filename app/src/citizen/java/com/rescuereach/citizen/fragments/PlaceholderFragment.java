package com.rescuereach.citizen.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.rescuereach.R;

public class PlaceholderFragment extends Fragment {
    private static final String ARG_TITLE = "title";
    private static final String ARG_DESCRIPTION = "description";
    private static final String ARG_ICON = "icon";

    public static PlaceholderFragment newInstance(String title, String description, @DrawableRes int iconResId) {
        PlaceholderFragment fragment = new PlaceholderFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_DESCRIPTION, description);
        args.putInt(ARG_ICON, iconResId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_placeholder, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            TextView titleTextView = view.findViewById(R.id.placeholder_title);
            TextView descTextView = view.findViewById(R.id.placeholder_description);
            ImageView iconImageView = view.findViewById(R.id.placeholder_icon);

            titleTextView.setText(args.getString(ARG_TITLE, ""));
            descTextView.setText(args.getString(ARG_DESCRIPTION, ""));
            iconImageView.setImageResource(args.getInt(ARG_ICON, R.drawable.ic_placeholder));
        }
    }
}