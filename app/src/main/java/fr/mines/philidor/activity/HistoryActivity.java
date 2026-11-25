package fr.mines.philidor.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

import fr.mines.philidor.R;
import fr.mines.philidor.adapter.GameAdapter;
import fr.mines.philidor.databinding.ActivityHistoryBinding;
import fr.mines.philidor.databinding.ActivityMainBinding;
import fr.mines.philidor.service.HistoryService;

public class HistoryActivity extends AppCompatActivity {

    private ActivityHistoryBinding binding;

    private GameAdapter gameAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        gameAdapter = new GameAdapter(this, new ArrayList<>(HistoryService.getInstance().getHistory()));
        binding.lvHistory.setAdapter(gameAdapter);

        binding.tvListDesc.setText(HistoryService.getInstance().nbParties() + " - "  +
                getResources().getQuantityString(R.plurals.party_win_pl, HistoryService.getInstance().nbPartiesWin(), HistoryService.getInstance().nbPartiesWin()));


        binding.lvHistory.setOnItemLongClickListener((parent, view, position, id) -> {
            showModal(position);
            return true;
        });


    }

    private void showModal(int position) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.abandon)
                .setMessage(R.string.abandon_desc)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes,
                        (dialogue, quel) -> deleteHistory(position))
                .setCancelable(false)
                .show();
    }

    private void deleteHistory(int position) {
        gameAdapter.remove(HistoryService.getInstance().getHistory().get(position));
        HistoryService.getInstance().deleteHistory(position);
    }

}