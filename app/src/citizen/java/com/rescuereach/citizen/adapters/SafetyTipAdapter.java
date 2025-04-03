package com.rescuereach.citizen.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.rescuereach.R;
import com.rescuereach.data.model.SafetyTip;

import java.util.List;

public class SafetyTipAdapter extends RecyclerView.Adapter<SafetyTipAdapter.ViewHolder> {

    private final List<SafetyTip> safetyTips;

    public SafetyTipAdapter(List<SafetyTip> safetyTips) {
        this.safetyTips = safetyTips;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_safety_tip, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SafetyTip tip = safetyTips.get(position);

        holder.titleTextView.setText(tip.getTitle());
        holder.contentTextView.setText(tip.getContent());
        holder.iconImageView.setImageResource(tip.getIconResId());

        // Add some color variety to the cards
        int[] colors = {
                R.color.safety_card_1,
                R.color.safety_card_2,
                R.color.safety_card_3,
                R.color.safety_card_4
        };

        holder.cardView.setCardBackgroundColor(
                holder.itemView.getContext().getResources().getColor(
                        colors[position % colors.length]
                )
        );
    }

    @Override
    public int getItemCount() {
        return safetyTips.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView iconImageView;
        TextView titleTextView;
        TextView contentTextView;
        MaterialCardView cardView;

        ViewHolder(View itemView) {
            super(itemView);
            iconImageView = itemView.findViewById(R.id.image_tip_icon);
            titleTextView = itemView.findViewById(R.id.text_tip_title);
            contentTextView = itemView.findViewById(R.id.text_tip_content);
            cardView = itemView.findViewById(R.id.card_safety_tip);
        }
    }
}