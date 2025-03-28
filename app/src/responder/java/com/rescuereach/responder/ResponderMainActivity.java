package com.rescuereach.responder;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.rescuereach.R;

public class ResponderMainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_responder_main);

        // Set title
        setTitle("RescueReach Responder");
    }
}