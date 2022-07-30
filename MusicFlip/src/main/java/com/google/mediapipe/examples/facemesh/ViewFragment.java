package com.google.mediapipe.examples.facemesh;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
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
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;

public class ViewFragment extends Fragment {

    private Uri current = Uri.EMPTY;

    private Animation fromRight;
    private Animation fromLeft;
    private Animation toRight;
    private Animation toLeft;

    protected ParcelFileDescriptor file;
    protected PdfRenderer renderer;
    protected PdfRenderer.Page currentPage;
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

        fromRight = AnimationUtils.loadAnimation(getActivity(), R.anim.from_right);
        fromLeft = AnimationUtils.loadAnimation(getActivity(), R.anim.from_left);
        toRight = AnimationUtils.loadAnimation(getActivity(), R.anim.to_right);
        toLeft = AnimationUtils.loadAnimation(getActivity(), R.anim.to_left);

        ImageSwitcher switcher = getView().findViewById(R.id.docu_view);
        switcher.setFactory(() -> {
            ImageView imageView = new ImageView(getActivity());
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
            return imageView;
        });

        if (current != Uri.EMPTY) {
            View root = getView();
            root.findViewById(R.id.blank_image).setVisibility(View.GONE);
            root.findViewById(R.id.blank_info).setVisibility(View.GONE);
            root.findViewById(R.id.docu_view).setVisibility(View.VISIBLE);
            root.findViewById(R.id.page_num).setVisibility(View.VISIBLE);
            renderPDF();
            root.findViewById(R.id.docu_view).setOnTouchListener(new OnSwipeListener(getActivity()) {
                @Override
                public void onSwipeLeft() {
                    nextPage();
                }

                @Override
                public void onSwipeRight() {
                    previousPage();
                }
            });
        }
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
            Bitmap page = Bitmap.createBitmap(currentPage.getWidth(), currentPage.getHeight(), Bitmap.Config.ARGB_8888);
            page.eraseColor(Color.WHITE);
            currentPage.render(page, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            ImageSwitcher v = getView().findViewById(R.id.docu_view);
            v.setImageDrawable(new BitmapDrawable(null, page));
            TextView t = getView().findViewById(R.id.page_num);
            t.setText(String.format("%d / %d", p + 1, renderer.getPageCount()));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Skip if fragment not yet attached
        }
    }

    public void nextPage() {
        if (renderer != null && p + 1 < renderer.getPageCount()) {
            p += 1;
            currentPage.close();
            currentPage = renderer.openPage(p);
            Bitmap page = Bitmap.createBitmap(currentPage.getWidth(), currentPage.getHeight(), Bitmap.Config.ARGB_8888);
            page.eraseColor(Color.WHITE);
            currentPage.render(page, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            getActivity().runOnUiThread(() -> {
                ImageSwitcher v = getView().findViewById(R.id.docu_view);
                v.setInAnimation(fromRight);
                v.setOutAnimation(toLeft);
                v.setImageDrawable(new BitmapDrawable(null, page));
                TextView t = getView().findViewById(R.id.page_num);
                t.setText(String.format("%d / %d", p + 1, renderer.getPageCount()));
            });
        }
    }

    public void previousPage() {
        if (renderer != null && p > 0) {
            p -= 1;
            currentPage.close();
            currentPage = renderer.openPage(p);
            Bitmap page = Bitmap.createBitmap(currentPage.getWidth(), currentPage.getHeight(), Bitmap.Config.ARGB_8888);
            page.eraseColor(Color.WHITE);
            currentPage.render(page, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            getActivity().runOnUiThread(() -> {
                ImageSwitcher v = getView().findViewById(R.id.docu_view);
                v.setInAnimation(fromLeft);
                v.setOutAnimation(toRight);
                v.setImageDrawable(new BitmapDrawable(null, page));
                TextView t = getView().findViewById(R.id.page_num);
                t.setText(String.format("%d / %d", p + 1, renderer.getPageCount()));
            });
        }
    }
}