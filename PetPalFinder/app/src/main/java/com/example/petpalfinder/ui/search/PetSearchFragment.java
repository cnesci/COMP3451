package com.example.petpalfinder.ui.search;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.petpalfinder.R;
import com.google.android.material.appbar.MaterialToolbar;

public class PetSearchFragment extends Fragment {

    private PetSearchViewModel vm;
    private AnimalAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle s) {
        return inf.inflate(R.layout.fragment_pet_search, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        vm = new ViewModelProvider(requireActivity()).get(PetSearchViewModel.class);

        RecyclerView list = v.findViewById(R.id.list);
        ProgressBar pb = v.findViewById(R.id.progress);
        MaterialToolbar toolbar = v.findViewById(R.id.toolbar);

        // Toolbar menu: open Map
        if (toolbar != null) {
            toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_toggle_map) {
                    // Pass current args to Map so it mirrors the same search
                    Bundle argsBundle = (getArguments() != null) ? getArguments() : new Bundle();
                    PetSearchFragmentArgs args = PetSearchFragmentArgs.fromBundle(argsBundle);
                    Bundle b = new Bundle();
                    b.putString("type", args.getType());
                    b.putString("location", args.getLocation());
                    NavHostFragment.findNavController(PetSearchFragment.this)
                            .navigate(R.id.mapFragment, b);
                    return true;
                }
                return false;
            });

            toolbar.setNavigationOnClickListener(click ->
                    NavHostFragment.findNavController(PetSearchFragment.this).navigateUp());
        }

        adapter = new AnimalAdapter(a -> {
            PetSearchFragmentDirections.ActionPetSearchFragmentToPetDetailFragment dir =
                    PetSearchFragmentDirections.actionPetSearchFragmentToPetDetailFragment(a.id);
            NavHostFragment.findNavController(PetSearchFragment.this).navigate(dir);
        });

        list.setLayoutManager(new LinearLayoutManager(getContext()));
        list.setAdapter(adapter);

        vm.loading().observe(getViewLifecycleOwner(),
                isLoading -> pb.setVisibility(Boolean.TRUE.equals(isLoading) ? View.VISIBLE : View.GONE));

        vm.error().observe(getViewLifecycleOwner(), err -> {
            if (!TextUtils.isEmpty(err)) {
                Toast.makeText(getContext(), err, Toast.LENGTH_LONG).show();
            }
        });

        vm.results().observe(getViewLifecycleOwner(), adapter::setItems);

        // Pull type/location from Safe Args; fallback to Toronto coords
        Bundle argsBundle = (getArguments() != null) ? getArguments() : new Bundle();
        PetSearchFragmentArgs args = PetSearchFragmentArgs.fromBundle(argsBundle);
        String location = args.getLocation();
        String type = args.getType();
        if (location == null || location.isEmpty()) {
            location = "43.6532,-79.3832"; // Toronto fallback
        }
        vm.firstSearch(type, location);

        // Endless scroll pagination
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int visible = lm.getChildCount();
                int total = lm.getItemCount();
                int first = lm.findFirstVisibleItemPosition();
                if (first + visible >= (int) (0.8 * total)) vm.nextPage();
            }
        });
    }
}
