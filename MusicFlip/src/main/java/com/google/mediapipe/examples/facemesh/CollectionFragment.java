package com.google.mediapipe.examples.facemesh;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class CollectionFragment extends Fragment {

    public CollectionFragment() {
        // Required empty public constructor
    }

    private MainActivity activity;

    public static CollectionFragment newInstance() {
        return new CollectionFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (MainActivity) getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_collection, container, false);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ActivityResultLauncher<String> getContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            (Uri uri) -> {
                if (uri == null) return;
                activity.openAndView(uri);
                saveFile(uri);
            }
        );
        FloatingActionButton addButton = getView().findViewById(R.id.add_doc);
        addButton.setOnClickListener((View v) -> getContent.launch("application/pdf"));

        renderItems();
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void renderItems() {

        String[] files = activity.fileList();
        files = Arrays.stream(files).filter(f -> Files.getFileExtension(f).equals("pdf")).toArray(String[]::new);

        FlexboxLayout flexboxLayout = (FlexboxLayout) getView().findViewById(R.id.collection);
        flexboxLayout.removeAllViews();

        Resources resource = activity.getResources();
        int screenWidth = resource.getConfiguration().screenWidthDp;
        int cardPerRow = screenWidth / 195;
        int marginLeft = (int) ((screenWidth - cardPerRow * 195f) / (float) (cardPerRow + 1));
        int marginLeftPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, marginLeft, resource.getDisplayMetrics());

        for (int i = 0; i < files.length; i++) {

            CardView card = (CardView) getLayoutInflater().inflate(R.layout.card_container, flexboxLayout, false);
            FlexboxLayout.LayoutParams params = (FlexboxLayout.LayoutParams) card.getLayoutParams();
            params.setMargins(marginLeftPx, 0, 0, 0);

            TextView cardText = card.findViewById(R.id.card_txt);
            ImageView cardImg = card.findViewById(R.id.card_img);
            Bitmap preview = getPDFPreview(files[i]);
            cardText.setText(Files.getNameWithoutExtension(files[i]));
            cardImg.setImageBitmap(preview);

            final String file = files[i];
            card.setOnClickListener(v -> {
                File f = new File(activity.getFilesDir(), file);
                activity.openAndView(Uri.fromFile(f));
            });
            card.setOnLongClickListener(v -> {
                createActionMenu(file).show();
                return true;
            });

            flexboxLayout.addView(card);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private AlertDialog createActionMenu(final String fileName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setItems(R.array.file_menu, (dialog, choice) -> {
            if (choice == 0) createRenameDialog(fileName).show();
            if (choice == 1) createDeleteDialog(fileName).show();
        });
        return builder.create();
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private AlertDialog createRenameDialog(final String fileName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage(R.string.please_rename)
                .setView(R.layout.dialog_rename)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialog, id) -> {
                    EditText edit = ((AlertDialog) dialog).findViewById(R.id.file_rename);
                    String newName = edit.getText().toString() + ".pdf";
                    File source = new File(activity.getFilesDir(), fileName);
                    File target = new File(activity.getFilesDir(), newName);
                    source.renameTo(target);
                    renderItems();
                });
        return builder.create();
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private AlertDialog createDeleteDialog(final String fileName) {

        View itemName = getLayoutInflater().inflate(R.layout.dialog_delete, null);
        ((TextView) itemName.findViewById(R.id.to_delete)).setText(fileName);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage(R.string.confirm_delete)
                .setView(itemName)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.Remove, (dialog, id) -> {
                    File f = new File(activity.getFilesDir(), fileName);
                    f.delete();
                    renderItems();
                });
        return builder.create();
    }

    private Bitmap getPDFPreview(String fileName) {
        try {
            File f = new File(activity.getFilesDir(), fileName);
            PdfRenderer renderer = new PdfRenderer(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY));
            PdfRenderer.Page page = renderer.openPage(0);
            Bitmap preview = Bitmap.createBitmap(page.getWidth() / 2, page.getHeight() / 2, Bitmap.Config.ARGB_8888);
            preview.eraseColor(Color.WHITE);
            page.render(preview, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return preview;
        } catch (IOException e) {
            Log.e("IO", e.getMessage());
            return null;
        }
    }

    private String getFileName(Uri uri) {
        Cursor cursor = activity.getContentResolver().query(uri, null, null, null, null);
        cursor.moveToFirst();
        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        String name = cursor.getString(nameIndex);
        cursor.close();
        return name;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void saveFile(Uri uri) {
        try {
            Log.e("File Save", "Started");
            String fileName = getFileName(uri);
            FileOutputStream destination = activity.openFileOutput(fileName, Context.MODE_PRIVATE);
            FileInputStream source = (FileInputStream) activity.getContentResolver().openInputStream(uri);
            FileUtils.copy(source, destination);
        } catch (IOException e) {
            Log.e("File Save", e.getMessage());
        }
    }
}