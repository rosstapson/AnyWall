package za.co.rosstapson.anywall;

import android.content.Intent;
import android.os.Bundle;

import android.support.v7.app.AppCompatActivity;

import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.parse.ParseUser;

import java.util.Collections;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {


    private List<Float> availableOptions = Application.getConfigHelper().getSearchDistanceAvailableOptions();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);


        float currentSearchDistance = Application.getSearchDistance();
        if (!availableOptions.contains(currentSearchDistance)) {
            availableOptions.add(currentSearchDistance);
        }
        Collections.sort(availableOptions);

        RadioGroup searchDistanceRadioGroup = (RadioGroup) findViewById(R.id.searchdistance_radiogroup);

        for (int index = 0; index < availableOptions.size(); index++) {
            float searchDistance = availableOptions.get(index);
            RadioButton button = new RadioButton(this);
            button.setId(index);
            button.setText(getString(R.string.settings_distance_format, (int)searchDistance));
            searchDistanceRadioGroup.addView(button, index);


            if (currentSearchDistance == searchDistance) {
                searchDistanceRadioGroup.check(index);
            }
        }


        // Set up the selection handler to save the selection to the application
        searchDistanceRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                Application.setSearchDistance(availableOptions.get(checkedId));
            }
        });


        // Set up the log out button click handler
        Button logoutButton = (Button) findViewById(R.id.logout_button);
        logoutButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Call the Parse log out method
                ParseUser.logOut();
                // Start and intent for the dispatch activity
                Intent intent = new Intent(SettingsActivity.this, DispatchActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });
    }
}
