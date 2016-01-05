package za.co.rosstapson.anywall;

import android.content.Intent;
import android.os.Bundle;

import android.support.v7.app.AppCompatActivity;


import com.parse.ParseUser;

public class DispatchActivity extends AppCompatActivity {

    public DispatchActivity() {

    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (ParseUser.getCurrentUser() != null) {
            startActivity(new Intent(this, MainActivity.class));
        }
        else {
            startActivity(new Intent(this, WelcomeActivity.class));
        }
    }

}
