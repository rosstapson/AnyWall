package za.co.rosstapson.anywall;

import android.app.ProgressDialog;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.parse.ParseACL;
import com.parse.ParseException;
import com.parse.ParseGeoPoint;
import com.parse.ParseUser;
import com.parse.SaveCallback;

import org.w3c.dom.Text;

public class PostActivity extends AppCompatActivity {
    private EditText postEditText;
    private TextView characterCountTextView;
    FloatingActionButton fab;

    private int maxCharacterCount = Application.getConfigHelper().getPostMaxCharacterCount();
    private ParseGeoPoint geoPoint = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Intent intent = getIntent();
        Location location = intent.getParcelableExtra(Application.INTENT_EXTRA_LOCATION);
        geoPoint = new ParseGeoPoint(location.getLatitude(), location.getLongitude());

        postEditText = (EditText)findViewById(R.id.post_edittext);
        postEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                updatePostButtonState();
                updateCharacterCountTextViewText();
            }
        });

        characterCountTextView = (TextView)findViewById(R.id.character_count_textview);
        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                post();
            }
        });
        updatePostButtonState();
        updateCharacterCountTextViewText();
    }
    private void post() {
        final ProgressDialog dialog = new ProgressDialog(PostActivity.this);
        dialog.setMessage(getString(R.string.progress_post));
        dialog.show();
        // 1
        AnywallPost post = new AnywallPost();
        post.setLocation(geoPoint);
        post.setText(postEditText.getText().toString().trim());
        post.setUser(ParseUser.getCurrentUser());
        //2
        ParseACL acl = new ParseACL();
        acl.setPublicReadAccess(true);
        post.setACL(acl);
        //3
        post.saveInBackground(new SaveCallback() {
            @Override
            public void done(ParseException e) {
                dialog.dismiss();
                finish();
            }
        });
    }
    private String getPostEditTextText() {
        return postEditText.getText().toString().trim();
    }
    private void updatePostButtonState() {
        int length = getPostEditTextText().length();
        boolean enabled = length > 0 && length < maxCharacterCount;
        fab.setEnabled(enabled);
    }
    private void updateCharacterCountTextViewText(){
        String characterCountString = String.format("%d/%d", postEditText.length(), maxCharacterCount);
        characterCountTextView.setText(characterCountString);
    }
}
