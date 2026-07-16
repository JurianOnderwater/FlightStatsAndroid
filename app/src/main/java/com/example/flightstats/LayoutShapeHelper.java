package com.example.flightstats;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.ShapeAppearanceModel;

public class LayoutShapeHelper {

    public static void applyToView(View rootView) {
        if (rootView == null) return;
        Context context = rootView.getContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        
        String shapeFamilyStr = prefs.getString("shape_family", "rounded");
        int shapeFamily = "cut".equals(shapeFamilyStr) ? CornerFamily.CUT : CornerFamily.ROUNDED;
        
        float baseRadius = prefs.getFloat("shape_radius", 16f); // default 16dp
        float density = context.getResources().getDisplayMetrics().density;
        
        applyRecursive(rootView, shapeFamily, baseRadius, density);
    }

    private static void applyRecursive(View view, int shapeFamily, float baseRadius, float density) {
        if (view instanceof MaterialCardView) {
            MaterialCardView cv = (MaterialCardView) view;
            boolean isNested = isNestedCard(cv);
            
            float radius;
            boolean isCarousel = cv.getParent() instanceof com.google.android.material.carousel.MaskableFrameLayout;
            
            if (isCarousel) {
                radius = baseRadius * 1.75f;
            } else if (isNested) {
                radius = baseRadius * 0.625f;
            } else {
                radius = baseRadius;
            }
            
            ShapeAppearanceModel model = cv.getShapeAppearanceModel().toBuilder()
                    .setAllCorners(shapeFamily, radius * density)
                    .build();
            cv.setShapeAppearanceModel(model);
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyRecursive(vg.getChildAt(i), shapeFamily, baseRadius, density);
            }
        }
    }

    private static boolean isNestedCard(View view) {
        android.view.ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent instanceof MaterialCardView) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }
}
