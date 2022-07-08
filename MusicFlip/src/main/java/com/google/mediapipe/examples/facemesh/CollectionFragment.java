package com.google.mediapipe.examples.facemesh;

import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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
                    saveFile(uri);
                    activity.openAndView(uri);
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

        LinearLayout verticalLayout = getView().findViewById(R.id.collection_vertical);
        verticalLayout.removeAllViews();

        for (int i = 0; i < files.length; i = i + 2) {
            LinearLayout row = (LinearLayout) getLayoutInflater().inflate(R.layout.card_row, verticalLayout, false);
            CardView leftCard = (CardView) getLayoutInflater().inflate(R.layout.card_container, row, false);
            CardView rightCard = (CardView) getLayoutInflater().inflate(R.layout.card_container, row, false);

            TextView leftText = leftCard.findViewById(R.id.card_txt);
            ImageView leftImage = leftCard.findViewById(R.id.card_img);
            Bitmap leftPreview = getPDFPreview(files[i]);
            leftText.setText(Files.getNameWithoutExtension(files[i]));
            leftImage.setImageBitmap(leftPreview);

            final String leftFile = files[i];
            leftCard.setOnClickListener(v -> {
                File f = new File(activity.getFilesDir(), leftFile);
                activity.openAndView(Uri.fromFile(f));
            });
            leftCard.setOnLongClickListener(v -> {
                createActionMenu(leftFile).show();
                return true;
            });

            if (i + 1 < files.length) {
                TextView rightText = rightCard.findViewById(R.id.card_txt);
                ImageView rightImage = rightCard.findViewById(R.id.card_img);
                Bitmap rightPreview = getPDFPreview(files[i + 1]);
                rightText.setText(Files.getNameWithoutExtension(files[i + 1]));
                rightImage.setImageBitmap(rightPreview);

                final String rightFile = files[i + 1];
                rightCard.setOnClickListener(v -> {
                    File f = new File(activity.getFilesDir(), rightFile);
                    activity.openAndView(Uri.fromFile(f));
                });
                rightCard.setOnLongClickListener(v -> {
                    createActionMenu(rightFile).show();
                    return true;
                });
            } else {
                rightCard.setVisibility(View.INVISIBLE);
            }
            row.addView(leftCard);
            row.addView(rightCard);
            verticalLayout.addView(row);
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
            Bitmap preview = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
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
            String fileName = getFileName(uri);
            FileOutputStream destination = activity.openFileOutput(fileName, Context.MODE_PRIVATE);
            FileInputStream source = (FileInputStream) activity.getContentResolver().openInputStream(uri);
            FileUtils.copy(source, destination);
        } catch (IOException e) {
            Log.e("IO", e.getMessage());
        }
    }
}