package com.example.petpalfinder.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.petpalfinder.R;
import com.example.petpalfinder.repository.GeocodingRepository;

import java.util.Locale;

public class HomeFragment extends Fragment {

    private GeocodingRepository repo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repo = new GeocodingRepository();

        TextView title = view.findViewById(R.id.txtTitle);
        TextView subtitle = view.findViewById(R.id.txtSubtitle);
        if (title != null) title.setText("Pet Pal Finder");
        if (subtitle != null) subtitle.setText("Geocoding test…");

        // Try a sample address (replace with user input later)
        String testAddress = "Toronto, ON";

        repo.forwardSimple(testAddress, new GeocodingRepository.SimpleHandler() {
            @Override
            public void onSuccess(double lat, double lng, String formatted) {
                if (!isAdded()) return;
                String msg = "OK: " + formatted + " → " + lat + ", " + lng;
                if (subtitle != null) subtitle.setText(msg);
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();

                String location = String.format(Locale.US, "%.5f,%.5f", lat, lng);

                HomeFragmentDirections.ActionHomeFragmentToPetSearchFragment dir =
                        HomeFragmentDirections.actionHomeFragmentToPetSearchFragment();
                dir.setLocation(location);
                dir.setType("dog");

                NavHostFragment.findNavController(HomeFragment.this).navigate(dir);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                String msg = "Geocode error: " + message;
                if (subtitle != null) subtitle.setText(msg);
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
            }
        });
    }
}
