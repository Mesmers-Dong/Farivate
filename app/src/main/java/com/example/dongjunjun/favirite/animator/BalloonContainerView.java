package com.example.dongjunjun.favirite.animator;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.example.dongjunjun.favirite.R;
import com.example.dongjunjun.favirite.animator.event.FlowEvent;
import com.example.dongjunjun.favirite.animator.event.SelectBalloonEvent;
import com.example.dongjunjun.favirite.animator.helper.AnimatorHelper;
import com.example.dongjunjun.favirite.animator.helper.RandomHelper;
import com.example.dongjunjun.favirite.animator.listener.BalloonItemClickListener;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import static com.example.dongjunjun.favirite.animator.Balloon.State.NONE;
import static com.example.dongjunjun.favirite.animator.BalloonConstant.BALLOON_CAPACITY;
import static com.example.dongjunjun.favirite.animator.BalloonConstant.BALLOON_EXCHANGE_DELAY;
import static com.example.dongjunjun.favirite.animator.BalloonConstant.BALLOON_SELECT_DURATION;
import static com.example.dongjunjun.favirite.animator.BalloonConstant.EVEN_TOP;
import static com.example.dongjunjun.favirite.animator.BalloonConstant.FLOW_DELAY;
import static com.example.dongjunjun.favirite.animator.BalloonConstant.FLOW_MAX;
import static com.example.dongjunjun.favirite.animator.BalloonConstant.INIT_RADIUS;
import static com.example.dongjunjun.favirite.animator.BalloonConstant.LINE_COUNT;
import static com.example.dongjunjun.favirite.animator.BalloonConstant.ODD_TOP;

/**
 * 盛放Balloon的容器View
 * 移除Balloon时会更新布局，
 * 若在绘制完成后再添加Balloon，需手动更新布局
 * 动态改变气泡的数量需谨慎
 * 如果没有动态改变气泡的逻辑可以将synchronized去掉
 * Created by dongjunjun on 2017/12/12.
 */

public class BalloonContainerView extends FrameLayout {

    private static final String TAG = "BalloonContainerView";

    private List<BalloonView> mBalloons = new ArrayList<>(BALLOON_CAPACITY);
    private List<SubTagView> mSubTags = new ArrayList<>(SubTag.SUBTAG_CAPACITY);
    private BalloonViewLifeCallBack mBalloonCallBack;
    private BalloonHandlerThread mHandlerThread;
    private BalloonItemClickListener mItemCLickListener;
    private BalloonView mSelectBalloonView;
    private BalloonMeasure mBalloonMeasure;//存放一些测量数据

    private int rawHeight;//一行的高度

    FlowRunnable flowRunnable = new FlowRunnable();

    public BalloonContainerView(Context context) {
        super(context);
        initData();
    }

    public BalloonContainerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initData();
        initAttrs(attrs);
    }

    private void initData() {
        mBalloonCallBack = new BalloonViewLifeCallBack();
        mHandlerThread = new BalloonHandlerThread(this, TAG);
        mBalloonMeasure = new BalloonMeasure();
        setClipChildren(false);

        for (int i = 0; i < SubTag.SUBTAG_CAPACITY; i++) {
            SubTagView subTagView = new SubTagView(getContext());
            mSubTags.add(subTagView);
            addView(subTagView);
            subTagView.setVisibility(GONE);
        }
    }

    public void setBalloonItemClickListener(BalloonItemClickListener clickListener) {
        this.mItemCLickListener = clickListener;
    }

    private void initAttrs(AttributeSet attrs) {
        if (attrs != null) {
            TypedArray array = getContext().obtainStyledAttributes(attrs, R.styleable.BalloonContainerView);
            int N = array.getIndexCount();
            for (int i = 0; i < N; i++) {
                int attr = array.getIndex(i);
                switch (attr) {
                    case R.styleable.BalloonContainerView_raw_height:
                        setRawHeight(array.getDimensionPixelSize(attr, 0));
                        break;
                }
            }
        }
    }

    public void setRawHeight(int rawHeight) {
        this.rawHeight = rawHeight;
        mBalloonMeasure.setRawHeight(rawHeight);
        mBalloonMeasure.measure();
    }

    public void addBalloon(String text) {
        addBalloon(mBalloons.size(), text);
    }

    public void addBalloon(int i, String text) {
        BalloonView balloonView = new BalloonView(getContext());
        addBalloon(i, balloonView);
        balloonView.setText(text);
    }

    public void addBalloon(BalloonView balloonView) {
        addBalloon(mBalloons.size(), balloonView);
    }

    public void addBalloon(int i, BalloonView balloonView) {
        synchronized (mBalloons) {
            balloonView.setItemClickListener(mItemCLickListener);
            balloonView.getModel().setNum(i);
            balloonView.getModel().setMeasure(mBalloonMeasure);
            mBalloons.add(balloonView);
            addView(balloonView);
        }
    }

    public void removeBalloon(BalloonView balloonView) {
        synchronized (mBalloons) {
            int key = getIndexBalloon(balloonView);
            if (key != -1) {
                mBalloons.remove(key);
            }
        }
    }

    public void removeBalloon(int i) {
        synchronized (mBalloons) {
            if (i >= 0 && i < mBalloons.size()) {
                mBalloons.remove(i);
            }
        }
    }

    public void removeAll() {
        synchronized (mBalloons) {
            mBalloons.clear();
        }
    }

    public void changeBalloonToFront(BalloonView balloonView) {
        synchronized (mBalloons) {
            int index = mBalloons.indexOf(balloonView);
            if (index != -1) {
                mBalloons.remove(index);
                mBalloons.add(balloonView);
            }
            resetPosition();
        }
    }

    private void resetPosition() {
        int size = mBalloons.size();
        for (int i = 0; i < size; i++) {
            mBalloons.get(i).getModel().setPosition(i);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        BalloonMeasure.initPaint();
        EventBus.getDefault().register(this);
        for (BalloonView balloonView : mBalloons) {
            Balloon balloon = balloonView.getModel();
            balloon.setPosition(balloon.getNum());
            balloon.setState(NONE);
        }
        mBalloonMeasure.setCounts(mBalloons.size());
        mBalloonMeasure.setLines(getRawCount());
        mBalloonMeasure.calculateMargin();


    }

    private void startThread() {
        if (mHandlerThread != null) {
            mHandlerThread.start();
            mHandlerThread.initHandler();
            mHandlerThread.sendFlowRequest();
        }
    }

    public void startFlow() {
        if (mHandlerThread != null) {
            startThread();
        }
    }

    public void stopFlow() {
        if (mHandlerThread != null) {
            mHandlerThread.stopFlow();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        handleHorizontalMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void handleHorizontalMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        if (widthMode == 0 || heightMode == 0) {
            widthSize = 0;
            heightSize = 0;
            setMeasuredDimension(widthSize, heightSize);
        } else {
            int childWidthMeasureSpec = widthSize / LINE_COUNT;
            int childHeightMeasureSpec = Math.min((int) (rawHeight * INIT_RADIUS) + 2 * FLOW_MAX, rawHeight);
            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                if (child instanceof BalloonView) {
                    Balloon balloon = ((BalloonView) child).getModel();
                    if (balloon.getState() == NONE) {
                        child.measure(MeasureSpec.makeMeasureSpec(childHeightMeasureSpec, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(childHeightMeasureSpec, MeasureSpec.EXACTLY));
                    } else {
                        child.measure(MeasureSpec.makeMeasureSpec((int) balloon.getLayoutBoundary().width(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) balloon.getLayoutBoundary().height(), MeasureSpec.EXACTLY));
                    }
                } else {
                    //child.measure(MeasureSpec.makeMeasureSpec(childHeightMeasureSpec, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(childHeightMeasureSpec, MeasureSpec.EXACTLY));
                }
            }
        }
        int height = getRawCount() * rawHeight;
        mBalloonMeasure.setWidth(widthSize);
        mBalloonMeasure.setHeight(height);
        setMeasuredDimension(widthSize, height);
    }

    private int getRawCount() {
        int count = mBalloons.size();
        int lines = count == 0 ? 0 : BalloonConstant.getRaw(count - 1) + 1;
        return lines;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        layoutChild();
        layoutTag();
    }

    private void layoutTag() {
        int size = mSubTags.size();
        for (int i = 0; i < size; i++) {
            SubTagView subTagView = mSubTags.get(i);
            if (subTagView.getVisibility() != View.GONE) {
                boolean isSelected = subTagView.getSubTag().isSelected();
                int centerX = getMeasuredWidth() / 2;
                int centerY = getMeasuredHeight() / 2;
                if (isSelected) {
                    switch (i) {
                        case 0:
                            subTagView.layout((int) (centerX - 0.90625 * mBalloonMeasure.getBigRadius()), (int) (centerY - 0.875 * BalloonMeasure.getBigRadius()), (int) (centerX + 0.15625 * BalloonMeasure.getBigRadius()), (int) (centerY - 0.625 * mBalloonMeasure.getBigRadius()));
                            break;
                        case 1:
                            subTagView.layout((int) (centerX - 0.85 * mBalloonMeasure.getBigRadius() - 0.5 * mBalloonMeasure.getBigRadius()), (int) (centerY - 0.5 * mBalloonMeasure.getBigRadius()), (int) (centerX - 0.0375 * mBalloonMeasure.getBigRadius() - 0.25 * mBalloonMeasure.getBigRadius()), (int) (centerY - 0.25 * mBalloonMeasure.getBigRadius()));
                            break;
                        case 2:
                            subTagView.layout((int) (centerX + 0.0375 * mBalloonMeasure.getBigRadius() + 0.25 * mBalloonMeasure.getBigRadius()), (int) (centerY - 0.5 * mBalloonMeasure.getBigRadius()), (int) (centerX + 0.85 * mBalloonMeasure.getBigRadius() + 0.5 * mBalloonMeasure.getBigRadius()), (int) (centerY - 0.25 * mBalloonMeasure.getBigRadius()));
                            break;
                        case 3:
                            subTagView.layout((int) (centerX - 0.9375 * mBalloonMeasure.getBigRadius() - 0.5 * mBalloonMeasure.getBigRadius()), (int) (centerY - 0.125 * mBalloonMeasure.getBigRadius()), (int) (centerX - 0.125 * mBalloonMeasure.getBigRadius() - 0.25 * mBalloonMeasure.getBigRadius()), (int) (centerY + 0.125 * mBalloonMeasure.getBigRadius()));
                            break;
                        case 4:
                            subTagView.layout((int) (centerX + 0.125 * mBalloonMeasure.getBigRadius() + 0.25 * mBalloonMeasure.getBigRadius()), (int) (centerY - 0.125 * mBalloonMeasure.getBigRadius()), (int) (centerX + 0.9375 * mBalloonMeasure.getBigRadius() + 0.5 * mBalloonMeasure.getBigRadius()), (int) (centerY + 0.125 * mBalloonMeasure.getBigRadius()));
                            break;
                        case 5:
                            subTagView.layout((int) (centerX - 0.85 * mBalloonMeasure.getBigRadius() - 0.5 * mBalloonMeasure.getBigRadius()), (int) (centerY + 0.25 * mBalloonMeasure.getBigRadius()), (int) (centerX - 0.0375 * mBalloonMeasure.getBigRadius() - 0.25 * mBalloonMeasure.getBigRadius()), (int) (centerY + 0.5 * mBalloonMeasure.getBigRadius()));
                            break;
                    }

                } else {
                    switch (i) {
                        case 0:
                            subTagView.layout((int) (centerX - 0.53125 * mBalloonMeasure.getBigRadius()), (int) (centerY - 0.875 * mBalloonMeasure.getBigRadius()), (int) (centerX + 0.53125 * mBalloonMeasure.getBigRadius()), (int) (centerY - 0.625 * mBalloonMeasure.getBigRadius()));
                            break;
                        case 1:
                            subTagView.layout((int) (centerX - 0.975 * mBalloonMeasure.getBigRadius()), (int) (centerY - 0.5 * mBalloonMeasure.getBigRadius()), (int) (centerX + 0.0875 * mBalloonMeasure.getBigRadius()), (int) (centerY - 0.25 * mBalloonMeasure.getBigRadius()));
                            break;
                        case 2:
                            subTagView.layout((int) (centerX - 0.0875 * mBalloonMeasure.getBigRadius()), (int) (centerY - 0.5 * mBalloonMeasure.getBigRadius()), (int) (centerX + 0.975 * mBalloonMeasure.getBigRadius()), (int) (centerY - 0.25 * mBalloonMeasure.getBigRadius()));
                            break;
                        case 3:
                            subTagView.layout((int) (centerX - 1.0625 * mBalloonMeasure.getBigRadius()), (int) (centerY - 0.125 * mBalloonMeasure.getBigRadius()), (int) (centerX - 0 * mBalloonMeasure.getBigRadius()), (int) (centerY + 0.125 * mBalloonMeasure.getBigRadius()));
                            break;
                        case 4:
                            subTagView.layout(centerX, (int) (centerY - 0.125 * mBalloonMeasure.getBigRadius()), (int) (centerX + 1.0625 * mBalloonMeasure.getBigRadius()), (int) (centerY + 0.125 * mBalloonMeasure.getBigRadius()));
                            break;
                        case 5:
                            subTagView.layout((int) (centerX - 0.975 * mBalloonMeasure.getBigRadius()), (int) (centerY + 0.25 * mBalloonMeasure.getBigRadius()), (int) (centerX + 0.0875 * mBalloonMeasure.getBigRadius()), (int) (centerY + 0.5 * mBalloonMeasure.getBigRadius()));
                            break;
                    }
                }

            }
        }
    }

    private void layoutChild() {
        int size = mBalloons.size();
        int width = getMeasuredWidth();
        int balloonWidth = width / LINE_COUNT;
        int offsetX = Integer.MIN_VALUE;
        for (int i = 0; i < size; i++) {
            BalloonView balloonView = mBalloons.get(i);
            Balloon balloon = balloonView.getModel();
            if (balloon.getState() == NONE) {
                if (offsetX == Integer.MIN_VALUE) {
                    offsetX = (balloonWidth - balloonView.getMeasuredWidth()) / 2;
                }
                initChildrenLayout(i, balloonView, balloonWidth, offsetX);
            }
            RectF layout = balloon.getLayoutBoundary();
            balloonView.layout((int) layout.left, (int) layout.top, (int) layout.right, (int) layout.bottom);
        }
    }

    @Override
    public void bringChildToFront(View child) {
        if (child instanceof BalloonView) {
//            changeBalloonToFront((BalloonView) child);
        }
        super.bringChildToFront(child);
        if (child instanceof BalloonView) {
            for (SubTagView tagView : mSubTags) {
                tagView.bringToFront();
            }
        }
    }

    private int getIndexBalloon(BalloonView child) {
        return mBalloons.indexOf(child);
    }

    private void initChildrenLayout(int i, BalloonView balloonView, int balloonWidth, int offsetX) {
        if (mBalloons.size() == 0) {
            return;
        }
        Balloon balloon = balloonView.getModel();
        int raw = BalloonConstant.getRaw(i);//行数
        int column = BalloonConstant.getColumn(i);//列数
        int offsetY;
        if ((column & 1) != 0) {
            //奇数列
            offsetY = (int) (rawHeight * ODD_TOP);
        } else {
            //偶数列
            offsetY = (int) (rawHeight * EVEN_TOP);
        }
        balloon.setLayoutBoundary(column * balloonWidth + offsetX, raw * rawHeight + offsetY, column * balloonWidth + balloonView.getMeasuredWidth() + offsetX, raw * rawHeight + balloonView.getMeasuredHeight() + offsetY);
    }

    /**
     * 更新浮动坐标
     */
    public void updateFlow() {
        synchronized (mBalloons) {
            for (int i = 0; i < mBalloons.size(); i++) {
                BalloonView balloon = mBalloons.get(i);
                balloon.updateFlow();
            }
        }
    }

    public void invalidateChildren() {
        updateFlow();
        post(flowRunnable);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        BalloonMeasure.releasePaint();
        EventBus.getDefault().unregister(this);
        if (mHandlerThread != null) {
            mHandlerThread.quit();
        }
        removeCallbacks(flowRunnable);
        AnimatorHelper.getInstance().cancelAllAnimator();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onFlowEvent(FlowEvent event) {
        if (event != null && event.cancel) {
            stopFlow();
        } else {
            if (mHandlerThread != null) {
                mHandlerThread.sendFlowRequest();
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSelectBalloonEvent(SelectBalloonEvent event) {
        if (event == null || event.position == -1) {
            return;
        } else {

            //具体逻辑在下面注释
            final BalloonView balloonView = mBalloons.get(event.position);
            changePosition(balloonView);
            //在这里获取balloonView的标签数量 目前用5个标签吧
            final int newTagCount = 6;
            if (mSelectBalloonView != null) {
                Balloon selectBalloon = mSelectBalloonView.getModel();
                Balloon normalBalloon = balloonView.getModel();
                selectBalloon.setState(Balloon.State.EXPAND_TO_SMALL);
                selectBalloon.setCurSelected(false);
                normalBalloon.setState(Balloon.State.SMALL_TO_EXPAND);
                normalBalloon.setCurSelected(true);
                AnimatorHelper.getInstance().balloonsPlayTogether(balloonView);
                AnimatorHelper.getInstance().playExchangeAnimator(mSelectBalloonView);

                int oldTagCount = 6;
                AnimatorSet animatorSet = new AnimatorSet();
                for (int i = 0; i < oldTagCount; i++) {
                    final SubTagView subTagView = mSubTags.get(i);
                    //可以搞一个动画集一起跑
                    if (subTagView.getSubTag().isSelected()) {
                        subTagView.getRetractionAnimator(subTagView, (int) BALLOON_EXCHANGE_DELAY).start();
                    }
                    ValueAnimator animator = subTagView.getAlphaTo0Animator(subTagView, (int) BalloonConstant.SUBTAG_DISAPPEAR_DURATION);
                    animator.setStartDelay(BALLOON_EXCHANGE_DELAY);
                    animatorSet.playTogether(animator);
                }

                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);

                        resetSubTagView(newTagCount, balloonView);
                        for (int i = 0; i < newTagCount; i++) {
                            SubTagView subTagView = mSubTags.get(i);
                            ValueAnimator animator1 = subTagView.getAlphaTo255Animator(subTagView, (int) BalloonConstant.SUBTAG_APPEAR_DURATION);
                            animator1.setStartDelay((int) BalloonConstant.SUBTAG_APPEAR_DELAY);
                            animator1.start();
                        }
                    }
                });
                animatorSet.start();

            } else {
                Balloon normalBalloon = balloonView.getModel();
                normalBalloon.setState(Balloon.State.NORMAL_TO_EXPAND);
                normalBalloon.setCurSelected(true);
                for (BalloonView balloonView1 : mBalloons) {
                    if (balloonView1 != balloonView) {
                        balloonView1.getModel().setState(Balloon.State.NORMAL_TO_SMALL);
                    }
                }
                AnimatorHelper.getInstance().balloonsPlayTogether(mBalloons);

                //第一次点击 渐显
                //先设置每个view是否可点击
                resetSubTagView(newTagCount, balloonView);
                for (int i = 0; i < newTagCount; i++) {
                    SubTagView subTagView = mSubTags.get(i);
                    ValueAnimator animator = subTagView.getAlphaTo255Animator(subTagView, (int) BALLOON_SELECT_DURATION);
                    animator.setStartDelay(BALLOON_SELECT_DURATION);
                    animator.start();
                }

            }
            mSelectBalloonView = balloonView;
        }
    }

    private void resetSubTagView(int newTagCount, BalloonView balloonView) {


        for (int i = newTagCount; i < SubTag.SUBTAG_CAPACITY; i++) {
            mSubTags.get(i).setVisibility(GONE);
        }

        for (int i = 0; i < newTagCount; i++) {
            //根据这个新的mSubTags

            Paint bgCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgCenterPaint.setColor(Color.parseColor("#f4f4f4"));
            bgCenterPaint.setAlpha(0);
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(BalloonConstant.SUBTAG_TEXT_SIZE);
            textPaint.setColor(Color.parseColor("#888888"));
            textPaint.setAlpha(0);
            Paint bgOtherPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgOtherPaint.setColor(Color.parseColor("#f4f4f4"));
            bgOtherPaint.setAlpha(0);
            SubTag subTag = new SubTag(0, 0,
                    new BallInfo((int) (BalloonMeasure.getBigRadius() * 0.25), (int) (BalloonMeasure.getBigRadius() * 0.0625), (int) (BalloonMeasure.getBigRadius() * 0.0625)),
                    new RectInfo((int) (BalloonMeasure.getBigRadius() * 0.5625), (int) (BalloonMeasure.getBigRadius() * 0.125), (int) (BalloonMeasure.getBigRadius() * 0.25), (int) (BalloonMeasure.getBigRadius() * 0.125), (int) (BalloonMeasure.getBigRadius() * 0.0625)),
                    new RectInfo((int) (BalloonMeasure.getBigRadius() * 0.125), 0, (int) (BalloonMeasure.getBigRadius() * 0.8125), (int) (BalloonMeasure.getBigRadius() * 0.25), (int) (BalloonMeasure.getBigRadius() * 0.125)),
                    "abc",
                    textPaint,
                    bgCenterPaint,
                    bgOtherPaint
            );
            if (subTag.isSelected()) {
                subTag.getLeftBall().setX((int) (BalloonMeasure.getBigRadius() * 0.0625));
                subTag.getRightRect().setLeft((int) (BalloonMeasure.getBigRadius() * 0.75));
                int pos = RandomHelper.getColorPosition(balloonView.getModel());
                RectF rectF = new RectF();
                rectF.set(0, 0, (int) (1.0625 * BalloonMeasure.getBigRadius()), (int) (0.25 * BalloonMeasure.getBigRadius()));
                LinearGradient shader = new LinearGradient(rectF.left, rectF.top, rectF.right, rectF.bottom,
                        RandomHelper.color[pos], null, Shader.TileMode.CLAMP);
                subTag.getBgOtherPaint().setShader(shader);
                subTag.getBgCenterPaint().setShader(shader);
                subTag.getTextPaint().setColor(Color.parseColor("#ffffff"));
                subTag.getTextPaint().setAlpha(0);
                subTag.getBgCenterPaint().setAlpha(0);
                subTag.getBgOtherPaint().setAlpha(0);
            }
            changeSubTagSize(subTag);
            subTag.setIndex(i);
            subTag.setParent(balloonView.getModel());
            mSubTags.get(i).setSubTag(subTag);
            if (subTag.isSelected()) {
                mSubTags.get(i).setClickable(false);
            } else {
                mSubTags.get(i).setClickable(true);
            }

        }
    }

    private void changeSubTagSize(SubTag subTag) {
        int index = subTag.getIndex();
        if (subTag.getText().length() == 3) {
            if (index == 0) {
                subTag.getLeftBall().setX((float) (subTag.getLeftBall().getX() + 0.0625 * BalloonMeasure.getBigRadius()));
                subTag.getRightRect().setLeft((float) (subTag.getRightRect().getLeft() - 0.0625 * BalloonMeasure.getBigRadius()));
                subTag.getCenterRect().setLeft((float) (subTag.getCenterRect().getLeft() + 0.0625 * BalloonMeasure.getBigRadius()));
                subTag.getCenterRect().setWidth((float) (subTag.getCenterRect().getWidth() - 0.125 * BalloonMeasure.getBigRadius()));
            } else if (index == 1 || index == 3 || index == 5) {
                //subTag.getLeftBall().setX((float) (subTag.getLeftBall().getX()+0.0625*BalloonMeasure.getBigRadius()));
                subTag.getRightRect().setLeft((float) (subTag.getRightRect().getLeft() - 0.125 * BalloonMeasure.getBigRadius()));
                //subTag.getCenterRect().setLeft((float) (subTag.getCenterRect().getLeft()+0.0625*BalloonMeasure.getBigRadius()));
                subTag.getCenterRect().setWidth((float) (subTag.getCenterRect().getWidth() - 0.125 * BalloonMeasure.getBigRadius()));
            } else {
                subTag.getLeftBall().setX((float) (subTag.getLeftBall().getX() + 0.125 * BalloonMeasure.getBigRadius()));
                //subTag.getRightRect().setLeft((float) (subTag.getRightRect().getLeft()-0.0625*BalloonMeasure.getBigRadius()));
                subTag.getCenterRect().setLeft((float) (subTag.getCenterRect().getLeft() + 0.125 * BalloonMeasure.getBigRadius()));
                subTag.getCenterRect().setWidth((float) (subTag.getCenterRect().getWidth() - 0.125 * BalloonMeasure.getBigRadius()));
            }
        }
        if (subTag.getText().length() == 2) {
            if (index == 0) {
                subTag.getLeftBall().setX((float) (subTag.getLeftBall().getX() + 0.125 * BalloonMeasure.getBigRadius()));
                subTag.getRightRect().setLeft((float) (subTag.getRightRect().getLeft() - 0.125 * BalloonMeasure.getBigRadius()));
                subTag.getCenterRect().setLeft((float) (subTag.getCenterRect().getLeft() + 0.125 * BalloonMeasure.getBigRadius()));
                subTag.getCenterRect().setWidth((float) (subTag.getCenterRect().getWidth() - 0.25 * BalloonMeasure.getBigRadius()));
            } else if (index == 1 || index == 3 || index == 5) {
                //subTag.getLeftBall().setX((float) (subTag.getLeftBall().getX()+0.0625*BalloonMeasure.getBigRadius()));
                subTag.getRightRect().setLeft((float) (subTag.getRightRect().getLeft() - 0.25 * BalloonMeasure.getBigRadius()));
                //subTag.getCenterRect().setLeft((float) (subTag.getCenterRect().getLeft()+0.0625*BalloonMeasure.getBigRadius()));
                subTag.getCenterRect().setWidth((float) (subTag.getCenterRect().getWidth() - 0.25 * BalloonMeasure.getBigRadius()));
            } else {
                subTag.getLeftBall().setX((float) (subTag.getLeftBall().getX() + 0.25 * BalloonMeasure.getBigRadius()));
                //subTag.getRightRect().setLeft((float) (subTag.getRightRect().getLeft()-0.0625*BalloonMeasure.getBigRadius()));
                subTag.getCenterRect().setLeft((float) (subTag.getCenterRect().getLeft() + 0.25 * BalloonMeasure.getBigRadius()));
                subTag.getCenterRect().setWidth((float) (subTag.getCenterRect().getWidth() - 0.25 * BalloonMeasure.getBigRadius()));
            }
        }
    }

    private void changePosition(BalloonView balloonView) {
        if (mSelectBalloonView != null && balloonView != null && mSelectBalloonView != balloonView) {
            int selectPos = mSelectBalloonView.getModel().getSmallPosition();
            int normalPos = balloonView.getModel().getSmallPosition();
            balloonView.getModel().setSmallPosition(selectPos);
            mSelectBalloonView.getModel().setSmallPosition(normalPos);
        }
    }

    class FlowRunnable implements Runnable {

        @Override
        public void run() {
            for (BalloonView balloonView : mBalloons) {
                balloonView.postInvalidate();
            }
        }
    }

    private static class BalloonHandlerThread extends HandlerThread {

        private Handler mFlowHandler;
        private static final int FLOW = 1;

        private BalloonContainerView mContainerView;

        public BalloonHandlerThread(BalloonContainerView containerView, String name) {
            super(name);
            this.mContainerView = containerView;
        }

        public void initHandler() {
            mFlowHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case FLOW:
                            if (mContainerView != null) {
                                mContainerView.invalidateChildren();
                            }
                            sendFlowRequest();
                            break;
                    }
                }
            };
        }

        public void sendFlowRequest() {
            if (mFlowHandler == null || mFlowHandler.hasMessages(FLOW)) {
                return;
            }
            mFlowHandler.sendEmptyMessageDelayed(FLOW, FLOW_DELAY);
        }

        public void stopFlow() {
            if (mFlowHandler != null) {
                mFlowHandler.removeMessages(FLOW);
            }
        }
    }
}
