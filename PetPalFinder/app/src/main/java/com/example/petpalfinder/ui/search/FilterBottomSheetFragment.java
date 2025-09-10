package com.example.petpalfinder.ui.search;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.petpalfinder.databinding.SheetFiltersBinding;
import com.example.petpalfinder.data.FilterParams;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;

public class FilterBottomSheetFragment extends BottomSheetDialogFragment {

    public interface Listener {
        void onFiltersApplied(FilterParams params);
    }

    private SheetFiltersBinding binding;
    private Listener listener;
    private FilterParams current;

    public static FilterBottomSheetFragment newInstance(FilterParams current) {
        FilterBottomSheetFragment f = new FilterBottomSheetFragment();
        Bundle b = new Bundle();
        b.putString("type", current.type);
        b.putString("gender", current.gender);
        b.putString("age", current.age);
        b.putString("size", current.size);
        b.putBoolean("gwk", current.goodWithKids);
        b.putBoolean("gwd", current.goodWithDogs);
        b.putBoolean("gwc", current.goodWithCats);
        b.putInt("distKm", current.distanceKm == null ? 50 : current.distanceKm);
        f.setArguments(b);
        return f;
    }

    @Override public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (getParentFragment() instanceof Listener) listener = (Listener) getParentFragment();
        else if (context instanceof Listener)       listener = (Listener) context;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = SheetFiltersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);

        // Build a working copy
        current = new FilterParams();
        Bundle args = getArguments() == null ? new Bundle() : getArguments();
        current.type = args.getString("type", null);
        current.gender = nullIfEmpty(args.getString("gender", null));
        current.age = nullIfEmpty(args.getString("age", null));
        current.size = nullIfEmpty(args.getString("size", null));
        current.goodWithKids = args.getBoolean("gwk", false);
        current.goodWithDogs = args.getBoolean("gwd", false);
        current.goodWithCats = args.getBoolean("gwc", false);
        current.distanceKm = args.getInt("distKm", 50);
        current.sort = "distance";

        // Preselect chips/switches
        preselect(binding.chipGroupGender, current.gender);
        preselect(binding.chipGroupAge, current.age);
        preselect(binding.chipGroupSize, current.size);
        binding.switchKids.setChecked(current.goodWithKids);
        binding.switchDogs.setChecked(current.goodWithDogs);
        binding.switchCats.setChecked(current.goodWithCats);
        binding.sliderDistance.setValue(current.distanceKm);

        binding.btnClear.setOnClickListener(v1 -> {
            binding.chipGroupGender.clearCheck();
            binding.chipGroupAge.clearCheck();
            binding.chipGroupSize.clearCheck();
            binding.switchKids.setChecked(false);
            binding.switchDogs.setChecked(false);
            binding.switchCats.setChecked(false);
            binding.sliderDistance.setValue(50);
        });

        binding.btnApply.setOnClickListener(v12 -> {
            current.gender = selectedKey(binding.chipGroupGender);
            current.age    = selectedKey(binding.chipGroupAge);
            current.size   = selectedKey(binding.chipGroupSize);
            current.goodWithKids = binding.switchKids.isChecked();
            current.goodWithDogs = binding.switchDogs.isChecked();
            current.goodWithCats = binding.switchCats.isChecked();
            current.distanceKm   = (int) binding.sliderDistance.getValue();
            if (listener != null) listener.onFiltersApplied(current);
            dismiss();
        });
    }

    private void preselect(ViewGroup chipGroup, String key) {
        if (key == null) return;
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            View c = chipGroup.getChildAt(i);
            if (c instanceof Chip) {
                Object tag = c.getTag();
                if (tag != null && tag.equals(key)) {
                    ((Chip) c).setChecked(true);
                    break;
                }
            }
        }
    }

    private String selectedKey(ViewGroup chipGroup) {
        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            View c = chipGroup.getChildAt(i);
            if (c instanceof Chip && ((Chip) c).isChecked()) {
                Object tag = c.getTag();
                return tag == null ? null : String.valueOf(tag);
            }
        }
        return null;
    }

    private String nullIfEmpty(String s) { return (s == null || s.trim().isEmpty()) ? null : s; }
}
