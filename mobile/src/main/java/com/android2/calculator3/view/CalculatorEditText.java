/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android2.calculator3.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Html;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.xlythe.math.BaseModule;
import com.xlythe.math.Constants;
import com.xlythe.math.EquationFormatter;
import com.xlythe.math.Solver;

public class CalculatorEditText extends EditText {
    private static final int BLINK = 500;
    private final long mShowCursor = SystemClock.uptimeMillis();
    Paint mHighlightPaint = new Paint();
    Handler mHandler = new Handler();
    Runnable mRefresher = new Runnable() {
        @Override
        public void run() {
            invalidate();
        }
    };
    private EquationFormatter mEquationFormatter;
    private AdvancedDisplay mDisplay;
    private String mInput = "";
    private int mSelectionHandle = 0;
    private Solver mSolver;

    public CalculatorEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        setUp();
    }

    private void setUp() {
        // Hide the keyboard
        setCustomSelectionActionModeCallback(new NoTextSelectionMode());
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);

        // Display ^ , and other visual cues
        mEquationFormatter = new EquationFormatter();
        addTextChangedListener(new TextWatcher() {
            boolean updating = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if(updating) return;

                mInput = s.toString()
                        .replace(Constants.PLACEHOLDER, Constants.POWER)
                        .replace(Constants.DECIMAL_SEPARATOR + "", "")
                        .replace(Constants.BINARY_SEPARATOR + "", "")
                        .replace(Constants.HEXADECIMAL_SEPARATOR + "", "");
                updating = true;

                // Get the selection handle, since we're setting text and that'll overwrite it
                mSelectionHandle = getSelectionStart();
                // Adjust the handle by removing any comas or spacing to the left
                String cs = s.subSequence(0, mSelectionHandle).toString();
                mSelectionHandle -= countOccurrences(cs, Constants.DECIMAL_SEPARATOR);
                if(Constants.BINARY_SEPARATOR != Constants.DECIMAL_SEPARATOR) {
                    mSelectionHandle -= countOccurrences(cs, Constants.BINARY_SEPARATOR);
                }
                if(Constants.HEXADECIMAL_SEPARATOR != Constants.BINARY_SEPARATOR
                        && Constants.HEXADECIMAL_SEPARATOR != Constants.DECIMAL_SEPARATOR) {
                    mSelectionHandle -= countOccurrences(cs, Constants.HEXADECIMAL_SEPARATOR);
                }

                setText(formatText(mInput));
                setSelection(Math.min(mSelectionHandle, getText().length()));
                updating = false;
            }
        });

        setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if(hasFocus && mDisplay != null)
                    mDisplay.setActiveEditText(CalculatorEditText.this);
            }
        });
    }

    private int countOccurrences(String haystack, char needle) {
        int count = 0;
        for(int i = 0; i < haystack.length(); i++) {
            if(haystack.charAt(i) == needle) {
                count++;
            }
        }
        return count;
    }

    private Spanned formatText(String input) {
        if(mSolver != null) {
            // Add grouping, and then split on the selection handle
            // which is saved as a unique char
            String grouped = mEquationFormatter.addComas(mSolver, input, mSelectionHandle);
            if (grouped.contains(String.valueOf(BaseModule.SELECTION_HANDLE))) {
                String[] temp = grouped.split(String.valueOf(BaseModule.SELECTION_HANDLE));
                mSelectionHandle = temp[0].length();
                input = "";
                for (String s : temp) {
                    input += s;
                }
            } else {
                input = grouped;
                mSelectionHandle = input.length();
            }
        }

        return Html.fromHtml(mEquationFormatter.insertSupScripts(input));
    }

    public CalculatorEditText(final AdvancedDisplay display) {
        super(display.getContext());
        setUp();
        mDisplay = display;
    }

    public static String load(final AdvancedDisplay parent) {
        return CalculatorEditText.load("", parent);
    }

    public static String load(String text, final AdvancedDisplay parent) {
        return CalculatorEditText.load(text, parent, parent.getChildCount());
    }

    public static String load(String text, final AdvancedDisplay parent, final int pos) {
        final CalculatorEditText et = new CalculatorEditText(parent);
        et.mDisplay = parent;
        et.setText(text);
        et.setSelection(0);
        et.setLongClickable(false);
        et.setBackgroundResource(android.R.color.transparent);
        et.setEnabled(parent.isEnabled());
        et.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER_VERTICAL;
        et.setLayoutParams(params);
        parent.addView(et, pos);
        return "";
    }

    public AdvancedDisplay getAdvancedDisplay() {
        return mDisplay;
    }

    @Override
    public String toString() {
        return mInput;
    }

    @Override
    public View focusSearch(int direction) {
        View v;
        switch(direction) {
            case View.FOCUS_FORWARD:
                v = mDisplay.nextView(this);
                while(!v.isFocusable())
                    v = mDisplay.nextView(v);
                return v;
            case View.FOCUS_BACKWARD:
                v = mDisplay.previousView(this);
                while(!v.isFocusable())
                    v = mDisplay.previousView(v);
                if(MatrixView.class.isAssignableFrom(v.getClass())) {
                    v = ((ViewGroup) v).getChildAt(((ViewGroup) v).getChildCount() - 1);
                    v = ((ViewGroup) v).getChildAt(((ViewGroup) v).getChildCount() - 1);
                }
                return v;
        }
        return super.focusSearch(direction);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // TextViews don't draw the cursor if textLength is 0. Because we're an
        // array of TextViews, we'd prefer that it did.
        if(getText().length() == 0 && isEnabled() && (isFocused() || isPressed())) {
            if((SystemClock.uptimeMillis() - mShowCursor) % (2 * BLINK) < BLINK) {
                mHighlightPaint.setColor(getCurrentTextColor());
                mHighlightPaint.setStyle(Paint.Style.STROKE);
                canvas.drawLine(getWidth() / 2, 0, getWidth() / 2, getHeight(), mHighlightPaint);
                mHandler.postAtTime(mRefresher, SystemClock.uptimeMillis() + BLINK);
            }
        }
    }

    class NoTextSelectionMode implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Prevents the selection action mode on double tap.
            return false;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
        }
    }
}
