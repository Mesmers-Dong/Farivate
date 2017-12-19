package com.example.dongjunjun.favirite.animator;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Created by dongjunjun on 2017/12/13.
 */

public class Tag extends Renderable {

    private String text;
    private float baseLine;

    public Tag(float x, float y) {
        super(x, y);
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    @Override
    public void setPaint(Paint paint) {
        super.setPaint(paint);
    }

    public void setBaseLine(float baseLine){
        this.baseLine = baseLine;
    }

    @Override
    protected void draw(Canvas canvas) {
        canvas.save();
        canvas.translate(translationX, translationY);
        canvas.drawText(text, x, y+baseLine, paint);
        canvas.restore();
    }

}
