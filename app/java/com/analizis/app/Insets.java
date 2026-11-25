package com.analizis.app;

import android.view.View;
import android.view.WindowInsets;

/** Deja hueco para la barra de estado y la de navegación (pantallas de borde a borde). */
public class Insets {

    public static void apply(final View v, final int baseTopPx, final int baseBottomPx) {
        final int left = v.getPaddingLeft();
        final int right = v.getPaddingRight();
        v.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets ins) {
                int top = ins.getSystemWindowInsetTop();
                int bottom = ins.getSystemWindowInsetBottom();
                view.setPadding(left, baseTopPx + top, right, baseBottomPx + bottom);
                return ins;
            }
        });
        v.requestApplyInsets();
    }
}
