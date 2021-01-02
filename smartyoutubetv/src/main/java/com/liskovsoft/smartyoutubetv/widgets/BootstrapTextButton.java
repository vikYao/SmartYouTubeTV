package com.liskovsoft.smartyoutubetv.widgets;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.CompoundButtonCompat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import com.liskovsoft.smartyoutubetv.R;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class BootstrapTextButton extends BootstrapButtonBase {
    private String mTitleText;
    private LinearLayout mWrapper;
    private LinearLayout mContent;
    private CheckBox mChkbox;
    private int mPaddingPx;
    private float mNormalTextSizeDp;
    private float mZoomedTextSizeDp;
    private List<OnCheckedChangeListener> mCheckedListeners = new ArrayList<>();

    public BootstrapTextButton(Context context) {
        super(context);
        init();
    }

    public BootstrapTextButton(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.BootstrapCheckButton,
                0, 0);

        try {
            mTitleText = a.getString(R.styleable.BootstrapCheckButton_titleText);
            String handlerName = a.getString(R.styleable.BootstrapCheckButton_onCheckedChanged);
            if (handlerName != null) {
                setOnCheckedChangeListener(new DeclaredOnCheckedChangeListener(this, handlerName));
            }
        } finally {
            a.recycle();
        }

        init();
    }

    public BootstrapTextButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public BootstrapTextButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        calculateSizes();
        inflate();
        applyDefaultAttributes();
        transferClicks();
        setOnFocus();
        setDefaultState();
    }

    private void calculateSizes() {
        mNormalTextSizeDp = Utils.convertPixelsToDp(getResources().getDimension(R.dimen.bootstrap_button_text_size), getContext());
        mZoomedTextSizeDp = mNormalTextSizeDp * 1.3f;
        mPaddingPx = (int) getResources().getDimension(R.dimen.bootstrap_text_button_padding);
    }

    private void setDefaultState() {
        Drawable transparentDrawable = new ColorDrawable(Color.TRANSPARENT);
        mChkbox.setButtonDrawable(transparentDrawable);
        makeUnfocused();
    }

    private void applyDefaultAttributes() {
        if (mTitleText != null) {
            mChkbox.setText(mTitleText);
        }
        initCheckBox();
        setFocusable(false);
        setClickable(false);
    }

    private void initCheckBox() {
        int states[][] = {{android.R.attr.state_focused}, {}};
        int colors[] = {Color.TRANSPARENT, Color.DKGRAY};
        CompoundButtonCompat.setButtonTintList(mChkbox, new ColorStateList(states, colors));
    }

    private void setOnFocus() {
        mWrapper.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    makeFocused();
                } else {
                    makeUnfocused();
                }
            }
        });
    }

    protected void makeUnfocused() {
        super.makeUnfocused();
        mChkbox.setTextColor(Color.DKGRAY);
        mChkbox.setTextSize(mNormalTextSizeDp);
        int semitransparentBlack = Color.argb(70, 0, 0, 0);
        mContent.setBackgroundColor(semitransparentBlack);
        mWrapper.setPadding(mPaddingPx, mPaddingPx, mPaddingPx, mPaddingPx);
    }

    protected void makeFocused() {
        super.makeFocused();
        mChkbox.setTextColor(Color.BLACK);
        mChkbox.setTextSize(mZoomedTextSizeDp);
        mContent.setBackgroundColor(Color.WHITE);
        mWrapper.setPadding(0, 0, 0, 0);
    }

    private void inflate() {
        inflate(getContext(), R.layout.bootstrap_check_button, this);
        mWrapper = (LinearLayout) findViewById(R.id.bootstrap_checkbox_wrapper);
        mContent = (LinearLayout) findViewById(R.id.bootstrap_checkbox_content);
        mChkbox = (CheckBox) findViewById(R.id.bootstrap_checkbox);
    }

    private void transferClicks() {
        OnClickListener clickListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (VERSION.SDK_INT >= 15) {
                    BootstrapTextButton.this.callOnClick();
                } else {
                    BootstrapTextButton.this.performClick();
                }
                mChkbox.setChecked(!mChkbox.isChecked());
                callCheckedListener(mChkbox.isChecked());
            }
        };
        mWrapper.setOnClickListener(clickListener);
    }

    public boolean isChecked() {
        return mChkbox.isChecked();
    }

    public void setChecked(boolean isChecked) {
        mChkbox.setChecked(isChecked);
    }

    private void callCheckedListener(boolean isChecked) {
        for (OnCheckedChangeListener listener : mCheckedListeners) {
            if (listener != null)
                listener.onCheckedChanged(this, isChecked);
        }
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        mCheckedListeners.add(listener);
    }

    @Override
    protected View getWrapper() {
        return mWrapper;
    }

    public interface OnCheckedChangeListener {
        void onCheckedChanged(BootstrapTextButton button, boolean isChecked);
    }

    /**
     * An implementation of Listener that attempts to lazily load a
     * named handling method from a parent or ancestor context.
     */
    private class DeclaredOnCheckedChangeListener implements OnCheckedChangeListener {
        private final View mHostView;
        private final String mMethodName;

        private Method mResolvedMethod;
        private Context mResolvedContext;

        public DeclaredOnCheckedChangeListener(@NonNull View hostView, @NonNull String methodName) {
            mHostView = hostView;
            mMethodName = methodName;
        }

        @Override
        public void onCheckedChanged(@NonNull BootstrapTextButton compoundButton, boolean b) {
            if (mResolvedMethod == null) {
                resolveMethod(mHostView.getContext(), mMethodName);
            }

            try {
                mResolvedMethod.invoke(mResolvedContext, compoundButton, b);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(
                        "Could not execute non-public method for app:onCheckedChanged", e);
            } catch (InvocationTargetException e) {
                throw new IllegalStateException(
                        "Could not execute method for app:onCheckedChanged", e);
            }
        }

        @NonNull
        private void resolveMethod(@Nullable Context context, @NonNull String name) {
            while (context != null) {
                try {
                    if (!context.isRestricted()) {
                        final Method method = context.getClass().getMethod(mMethodName, BootstrapTextButton.class, boolean.class);
                        if (method != null) {
                            mResolvedMethod = method;
                            mResolvedContext = context;
                            return;
                        }
                    }
                } catch (NoSuchMethodException e) {
                    // Failed to find method, keep searching up the hierarchy.
                }

                if (context instanceof ContextWrapper) {
                    context = ((ContextWrapper) context).getBaseContext();
                } else {
                    // Can't search up the hierarchy, null out and fail.
                    context = null;
                }
            }

            final int id = mHostView.getId();
            final String idText = id == NO_ID ? "" : " with id '"
                    + mHostView.getContext().getResources().getResourceEntryName(id) + "'";
            throw new IllegalStateException("Could not find method " + mMethodName
                    + "(BootstrapCheckBox, boolean) in a parent or ancestor Context for app:onCheckedChanged "
                    + "attribute defined on view " + mHostView.getClass() + idText);
        }
    }
}
