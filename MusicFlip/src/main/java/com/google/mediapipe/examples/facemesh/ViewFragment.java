package com.google.mediapipe.examples.facemesh;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.ParcelFileDescriptor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.io.IOException;

public class ViewFragment extends Fragment {

    private Uri current = Uri.EMPTY;

    protected ParcelFileDescriptor file;
    protected PdfRenderer renderer;
    protected PdfRenderer.Page currentPage;
    protected Bitmap pdfImage;
    protected int p = 0;

    public ViewFragment() {
        // Required empty public constructor
    }

    // TODO: Rename and change types and number of parameters
    public static ViewFragment newInstance() {
        ViewFragment fragment = new ViewFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_view, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (current != Uri.EMPTY) renderPDF();
    }

    public void openPDF(Uri uri) {
        p = 0;
        current = uri;
        renderPDF();
    }

    private void renderPDF() {
        try {
            file = getActivity().getContentResolver().openFileDescriptor(current, "r");
            renderer = new PdfRenderer(file);
            currentPage = renderer.openPage(p);
            pdfImage = Bitmap.createBitmap(currentPage.getWidth(), currentPage.getHeight(), Bitmap.Config.ARGB_8888);
            currentPage.render(pdfImage, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            ImageView v = getView().findViewById(R.id.imageView);
            v.setImageBitmap(pdfImage);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        catch (NullPointerException e) {
            // Skip if fragment not yet attached
        }
    }

    public void nextPage() {
        if (renderer != null && p + 1 < renderer.getPageCount()) {
            currentPage.close();
            pdfImage.eraseColor(Color.WHITE);
            p += 1;
            currentPage = renderer.openPage(p);
            currentPage.render(pdfImage, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            getActivity().runOnUiThread(() -> {
                ImageView v = getView().findViewById(R.id.imageView);
                v.setImageBitmap(pdfImage);
            });
        }
    }

    public void previousPage() {
        if (renderer != null && p > 0) {
            currentPage.close();
            pdfImage.eraseColor(Color.WHITE);
            p -= 1;
            currentPage = renderer.openPage(p);
            currentPage.render(pdfImage, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            getActivity().runOnUiThread(() -> {
                ImageView v = getView().findViewById(R.id.imageView);
                v.setImageBitmap(pdfImage);
            });
        }
    }
}