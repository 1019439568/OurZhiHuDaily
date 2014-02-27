/*
 * Copyright (C) 2013 Sergej Shafarenka, halfbit.de
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file kt in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jjshome.lwk.widget;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.GradientDrawable.Orientation;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.widget.AbsListView;
import android.widget.HeaderViewListAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Scroller;
import android.widget.SectionIndexer;

import com.jjshome.lwk.myzhihu.BuildConfig;
import com.jjshome.lwk.myzhihu.R;

/**
 * ListView, which is capable to pin section views at its top while the rest is still scrolled.
 */
/*这里的ListView是可以将标签栏留在顶部，而其他的可以滚动*/
public class PinnedSectionListView extends ListView {

    //-- inner classes

	/** List adapter to be implemented for being used with PinnedSectionListView adapter. */
	public static interface PinnedSectionListAdapter extends ListAdapter {
		/** This method shall return 'true' if views of given type has to be pinned. */
       /*如果给定的部分被固定将会返回true*/
		boolean isItemViewTypePinned(int viewType);
	}

	/** Wrapper class for pinned section view and its position in the list. */
    /*固定部分View的内部类，定义了一些参数：列表中的位置,id，view*/
	static class PinnedSection {
		public View view;
		public int position;
		public long id;
	}

	//-- class fields

    // fields used for handling touch events
    /*这里的参数用于处理触摸事件*/
    private final Rect mTouchRect = new Rect();
    private final PointF mTouchPoint = new PointF();
    private int mTouchSlop;
    private View mTouchTarget;
    private MotionEvent mDownEvent;

    // fields used for drawing shadow under a pinned section
    /*这里的参数用于画出固定部分下的阴影*/
    private GradientDrawable mShadowDrawable;
    private int mSectionsDistanceY;
    /*阴影的高度*/
    private int mShadowHeight;

    /** Delegating listener, can be null. */
    /*指派监听器，对当前的list进行监听，可以为空(估计他的意思是可以这样写：OnScrollListener mDelegateOnScrollListene = null)*/
    OnScrollListener mDelegateOnScrollListener;

    /** Shadow for being recycled, can be null. */
    /*回收阴影*/
    PinnedSection mRecycleSection;

    /** shadow instance with a pinned view, can be null. */
    /*阴影使用的View进行实例化，可以为空*/
    PinnedSection mPinnedSection;

    /** Pinned view Y-translation. We use it to stick pinned view to the next section. */
    /*顶部固定View的Y坐标转换，我们使用它与下一个部分连接起来*/
    int mTranslateY;

    private ImageView topView;

    /*设置的右上角图片*/
	public ImageView getTopView() {
		return topView;
	}

	public void setTopView(ImageView topView) {
		this.topView = topView;
	}

	/** Scroll listener which does the magic */
    /*定义一个onScrollListener*/
	private final OnScrollListener mOnScrollListener = new OnScrollListener() {

        /*这里是一个回调函数，当listview被滚动时调用
        *正在滚动时回调，回调2-3次，手指没抛则回调2次。scrollState = 2的这次不回调
        *回调顺序如下
        *第1次：scrollState = SCROLL_STATE_TOUCH_SCROLL(1) 正在滚动
        *第2次：scrollState = SCROLL_STATE_FLING(2) 手指做了抛的动作（手指离开屏幕前，用力滑了一下）
        *第3次：scrollState = SCROLL_STATE_IDLE(0) 停止滚动
        *当屏幕停止滚动时为0；当屏幕滚动且用户使用的触碰或手指还在屏幕上时为1；
        *由于用户的操作，屏幕产生惯性滑动时为2
        *当滚到最后一行且停止滚动时，执行加载
        */
		@Override public void onScrollStateChanged(AbsListView view, int scrollState) {
			if (mDelegateOnScrollListener != null) { // delegate
				mDelegateOnScrollListener.onScrollStateChanged(view, scrollState);
			}
		}

		@Override
        /*这里是一个回调函数，当list被滚动时调用
        *附上参数含义：
        * 滚动时一直回调，直到停止滚动时才停止回调。单击时回调一次。
        * firstVisibleItem：当前能看见的第一个列表项ID（从0开始）
        * visibleItemCount：当前能看见的列表项个数（小半个也算）
        * totalItemCount：列表项共数
        */
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

            if (mDelegateOnScrollListener != null) { // delegate
                mDelegateOnScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
            }

            // get expected adapter or fail fast
            /*不用多说ListAdapter是绑定数据与ListView的桥梁，一般使用它的子类，为接口*/
            ListAdapter adapter = getAdapter();
            /*为空判断，则不需要做什么*/
            if (adapter == null || visibleItemCount == 0) return; // nothing to do


            final boolean isFirstVisibleItemSection =
                    isItemViewTypePinned(adapter, adapter.getItemViewType(firstVisibleItem));
            Log.d("isFirstVisibleItemSection",""+isFirstVisibleItemSection);
            
            if (isFirstVisibleItemSection) {
                /*getChildAt返回指定number处的View*/
                View sectionView = getChildAt(0);
                Log.d("number_two","sectionView.getTop()--->"+sectionView.getTop()+"getPaddingTop()"+getPaddingTop());
                /*将得到的View，使用getTop()函数获取它相对整体的位置，同时与getPaddingTop()[getPaddingTop获取的数据一直
                * 为顶部,即为0]相比较，如果刚刚达到临界位置，getTop()也为0,就销毁掉阴影。
                */
                if (sectionView.getTop() == getPaddingTop()) { // view sticks to the top, no need for pinned shadow
                    destroyPinnedShadow();
                    
                } else { // section doesn't stick to the top, make sure we have a pinned shadow
                    /*在section没有在顶端时，确保有个固定的阴影*/
                    ensureShadowForPosition(firstVisibleItem, firstVisibleItem, visibleItemCount);
                    if(null!=topView){
                     /*此时更换图片资源，请注意右上角前后图片不同*/
                    	topView.setImageResource(R.drawable.home_menu2);
                    }
                }                
                if(null!=topView){
                	topView.setImageResource(R.drawable.home_menu2);
                }
            } else { // section is not at the first visible position
                int sectionPosition = findCurrentSectionPosition(firstVisibleItem);
                if (sectionPosition > -1) { // we have section position
                    ensureShadowForPosition(sectionPosition, firstVisibleItem, visibleItemCount);
                    
                } else { // there is no section for the first visible item, destroy shadow
                    destroyPinnedShadow();
                  if(null!=topView){
                	topView.setImageResource(R.drawable.home_menu1);
                   }
                   
                }
                
            }
		};

	};

	/** Default change observer. */
    private final DataSetObserver mDataSetObserver = new DataSetObserver() {
        @Override public void onChanged() {
            recreatePinnedShadow();
        };
        @Override public void onInvalidated() {
            recreatePinnedShadow();
        }
    };

	//-- constructors

    /*两个PinnedSectionListView的构造函数*/
    public PinnedSectionListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public PinnedSectionListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView(context);
    }
    
    private Scroller mScroller;
    
    private View headView;
    
    public View getHeadView() {
		return headView;
	}

	public void setHeadView(View headView) {
		this.headView = headView;
	}

    /*初始化View*/
	private void initView(Context mContext) {
        /*设置监听器*/
        setOnScrollListener(mOnScrollListener);
        /*这里获取手指在屏幕上面的滑动距离*/
        mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        /*初始化阴影部分所需数据*/
        initShadow(true);
        mScroller = new Scroller(mContext);
    }

    //-- public API methods
    /*没有使用这个函数*/
    public void setShadowVisible(boolean visible) {
        initShadow(visible);
        if (mPinnedSection != null) {
            View v = mPinnedSection.view;
            invalidate(v.getLeft(), v.getTop(), v.getRight(), v.getBottom() + mShadowHeight);
        }
    }

    //-- pinned section drawing methods
    /*初始化阴影部分所需数据，传入的boolean参数visible判断是否可见*/
    public void initShadow(boolean visible) {
        if (visible) {
            /*利用GradientDrawable的构造函数，传入参数：
            * 第一个参数是从底部绘制渐变顶端
            * 第二个按照颜色数组进行渐变
            */
            if (mShadowDrawable == null) {
                mShadowDrawable = new GradientDrawable(Orientation.TOP_BOTTOM,
                        new int[] { Color.parseColor("#ffa0a0a0"), Color.parseColor("#50a0a0a0"), Color.parseColor("#00a0a0a0")});
                /*设置阴影的高度，获取手机屏幕的dpi*/
                mShadowHeight = (int) (8 * getResources().getDisplayMetrics().density);
            }
        } else {
            /*当设置为不可见时，将GradientDrawable设置为null，高度为0*/
            if (mShadowDrawable != null) {
                mShadowDrawable = null;
                mShadowHeight = 0;
            }
        }
    }

	/** Create shadow wrapper with a pinned view for a view at given position */
    /*在指定的地方画出阴影*/
	void createPinnedShadow(int position) {

		// try to recycle shadow
        /*回收阴影*/
		PinnedSection pinnedShadow = mRecycleSection;
		mRecycleSection = null;

		// create new shadow, if needed
       /*在pinnedShadow为空时创建一个阴影*/
		if (pinnedShadow == null) pinnedShadow = new PinnedSection();
		// request new view using recycled view, if such
       /*定义一个View*/
		View pinnedView = getAdapter().getView(position, pinnedShadow.view, PinnedSectionListView.this);

		// read layout parameters
       /*获取pinnedView中的参数，设置width:match_parent,height:wrap_parent*/
		LayoutParams layoutParams = (LayoutParams) pinnedView.getLayoutParams();
		if (layoutParams == null) { // create default layout params
		    layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		}

        /*根据提供的测量值(格式)提取模式*/
		int heightMode = MeasureSpec.getMode(layoutParams.height);
        /*根据提供的测量值(格式)提取大小值(这个大小也就是我们通常所说的大小)*/
		int heightSize = MeasureSpec.getSize(layoutParams.height);

        /*这里的模式UNSPECIFIED表示未指定，即：父元素不对子元素施加任何束缚，
         *EXACTLY表示父元素决定自元素的确切大小，子元素将被限定在给定的边界里而忽略它本身大小
        */
		if (heightMode == MeasureSpec.UNSPECIFIED) heightMode = MeasureSpec.EXACTLY;

       /*最大高度使用整个View的高度减去底部最大间距与顶部最大间距*/
		int maxHeight = getHeight() - getListPaddingTop() - getListPaddingBottom();
       Log.d("measurenum",maxHeight+"");
       /*不能超过最大高度*/
		if (heightSize > maxHeight) heightSize = maxHeight;

		// measure & layout
       /*makeMeasureSpec根据提供的大小值和模式创建一个测量值(格式)
       * measure获取view的大小
       * 为pinnedView分配一定大小和位置的视图
       */
		int ws = MeasureSpec.makeMeasureSpec(getWidth() - getListPaddingLeft() - getListPaddingRight(), MeasureSpec.EXACTLY);
		int hs = MeasureSpec.makeMeasureSpec(heightSize, heightMode);
		pinnedView.measure(ws, hs);
		pinnedView.layout(0, 0, pinnedView.getMeasuredWidth(), pinnedView.getMeasuredHeight());
		mTranslateY = 0;

		// initialize pinned shadow
       /*初始化pinned shadow*/
		pinnedShadow.view = pinnedView;
		pinnedShadow.position = position;
		pinnedShadow.id = getAdapter().getItemId(position);

		// store pinned shadow
       /*保存pinnedShadow*/
		mPinnedSection = pinnedShadow;
	}

	/** Destroy shadow wrapper for currently pinned view */
    /*为现有的固定View删除掉阴影部分*/
	void destroyPinnedShadow() {
	    if (mPinnedSection != null) {
	        // keep shadow for being recycled later
           /*使阴影后面可以回收，同时将现有阴影设置为null*/
	        mRecycleSection = mPinnedSection;
	        mPinnedSection = null;
	    }
	}

	/** Makes sure we have an actual pinned shadow for given position. */
    /*确定阴影的位置
    * sectionPosition: 当前能看见的第一个列表项ID(从0开始)
    * firstVisibleItem： 第一个可见的Item的ID
    * visibleItemCount： 可见Item的总数
    */
    void ensureShadowForPosition(int sectionPosition, int firstVisibleItem, int visibleItemCount) {
        /*这里对于屏幕可见的item进行判断，对于只有item只有2以下的可以不用阴影*/
        if (visibleItemCount < 2) { // no need for creating shadow at all, we have a single visible item
            destroyPinnedShadow();
            return;
        }

        if (mPinnedSection != null
                && mPinnedSection.position != sectionPosition) { // invalidate shadow, if required
            destroyPinnedShadow();
        }

        if (mPinnedSection == null) { // create shadow, if empty
            createPinnedShadow(sectionPosition);
        }

        // align shadow according to next section position, if needed
        int nextPosition = sectionPosition + 1;
        if (nextPosition < getCount()) {
            int nextSectionPosition = findFirstVisibleSectionPosition(nextPosition,
                    visibleItemCount - (nextPosition - firstVisibleItem));
            if (nextSectionPosition > -1) {
                View nextSectionView = getChildAt(nextSectionPosition - firstVisibleItem);
                final int bottom = mPinnedSection.view.getBottom() + getPaddingTop();
                mSectionsDistanceY = nextSectionView.getTop() - bottom;
                if (mSectionsDistanceY < 0) {
                    // next section overlaps pinned shadow, move it up
                    mTranslateY = mSectionsDistanceY;
                } else {
                    // next section does not overlap with pinned, stick to top
                    mTranslateY = 0;
                }
            } else {
                // no other sections are visible, stick to top
                mTranslateY = 0;
                mSectionsDistanceY = Integer.MAX_VALUE;
            }
        }

    }

	int findFirstVisibleSectionPosition(int firstVisibleItem, int visibleItemCount) {
		ListAdapter adapter = getAdapter();
		for (int childIndex = 0; childIndex < visibleItemCount; childIndex++) {
			int position = firstVisibleItem + childIndex;
			int viewType = adapter.getItemViewType(position);
			if (isItemViewTypePinned(adapter, viewType)) return position;
		}
		return -1;
	}

	int findCurrentSectionPosition(int fromPosition) {
		ListAdapter adapter = getAdapter();

		if (adapter instanceof SectionIndexer) {
			// try fast way by asking section indexer
			SectionIndexer indexer = (SectionIndexer) adapter;
			int sectionPosition = indexer.getSectionForPosition(fromPosition);
			int itemPosition = indexer.getPositionForSection(sectionPosition);
			int typeView = adapter.getItemViewType(itemPosition);
			if (isItemViewTypePinned(adapter, typeView)) {
				return itemPosition;
			} // else, no luck
		}

		// try slow way by looking through to the next section item above
		for (int position=fromPosition; position>=0; position--) {
			int viewType = adapter.getItemViewType(position);
			if (isItemViewTypePinned(adapter, viewType)) return position;
		}
		return -1; // no candidate found
	}

    /*这里是对于固定部分阴影的重新生成*/
	void recreatePinnedShadow() {
	    destroyPinnedShadow();
        ListAdapter adapter = getAdapter();
        if (adapter != null && adapter.getCount() > 0) {
            int firstVisiblePosition = getFirstVisiblePosition();
            int sectionPosition = findCurrentSectionPosition(firstVisiblePosition);
            if (sectionPosition == -1) return; // no views to pin, exit
            ensureShadowForPosition(sectionPosition,
                    firstVisiblePosition, getLastVisiblePosition() - firstVisiblePosition);
        }
	}

    /*在这里设置监听器*/
	@Override
	public void setOnScrollListener(OnScrollListener listener) {
		if (listener == mOnScrollListener) {
			super.setOnScrollListener(listener);
		} else {
			mDelegateOnScrollListener = listener;
		}
	}

	@Override
	public void onRestoreInstanceState(Parcelable state) {
		super.onRestoreInstanceState(state);
		post(new Runnable() {
			@Override public void run() { // restore pinned view after configuration change
			    recreatePinnedShadow();
			}
		});
	}

	@Override
	public void setAdapter(ListAdapter adapter) {

	    // assert adapter in debug mode
		if (BuildConfig.DEBUG && adapter != null) {
			if (!(adapter instanceof PinnedSectionListAdapter))
				throw new IllegalArgumentException("Does your adapter implement PinnedSectionListAdapter?");
			if (adapter.getViewTypeCount() < 2)
				throw new IllegalArgumentException("Does your adapter handle at least two types" +
						" of views in getViewTypeCount() method: items and sections?");
		}

		// unregister observer at old adapter and register on new one
		ListAdapter oldAdapter = getAdapter();
		if (oldAdapter != null) oldAdapter.unregisterDataSetObserver(mDataSetObserver);
		if (adapter != null) adapter.registerDataSetObserver(mDataSetObserver);

		// destroy pinned shadow, if new adapter is not same as old one
		if (oldAdapter != adapter) destroyPinnedShadow();

		super.setAdapter(adapter);
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
	    super.onLayout(changed, l, t, r, b);
        if (mPinnedSection != null) {
            int parentWidth = r - l - getPaddingLeft() - getPaddingRight();
            int shadowWidth = mPinnedSection.view.getWidth();
            if (parentWidth != shadowWidth) {
                recreatePinnedShadow();
            }
        }
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);

		if (mPinnedSection != null) {

			// prepare variables
			int pLeft = getListPaddingLeft();
			int pTop = getListPaddingTop();
			View view = mPinnedSection.view;

			// draw child
			canvas.save();

			int clipHeight = view.getHeight() +
			        (mShadowDrawable == null ? 0 : Math.min(mShadowHeight, mSectionsDistanceY));
			canvas.clipRect(pLeft, pTop, pLeft + view.getWidth(), pTop + clipHeight);

			canvas.translate(pLeft, pTop + mTranslateY);
			drawChild(canvas, mPinnedSection.view, getDrawingTime());

			if (mShadowDrawable != null && mSectionsDistanceY > 0) {
			    mShadowDrawable.setBounds(mPinnedSection.view.getLeft(),
			            mPinnedSection.view.getBottom(),
			            mPinnedSection.view.getRight(),
			            mPinnedSection.view.getBottom() + mShadowHeight);
			    mShadowDrawable.draw(canvas);
			}

			canvas.restore();
		}
	}

	//-- touch handling methods
    /*对于触摸进行处理，主要是下拉刷新是的图片变化，以及刷新*/
	TouchTool tool;
	int left, top;
	float startX, startY ;
	int bgViewH, iv1W;
	int rootW, rootH;
	View bgView;
    //生成HoloCircleSeekBar的引用
	HoloCircleSeekBar proView;
	boolean scrollerType;
	static final int len = 0xc8;
	
    @Override
    /*触摸事件的监听*/
    public boolean dispatchTouchEvent(MotionEvent ev) {

        //获取触摸的x，y坐标，以及触摸事件
        final float x = ev.getX();
        final float y = ev.getY();
        final int action = ev.getAction();
        		     
        if (action == MotionEvent.ACTION_DOWN
                && mTouchTarget == null
                && mPinnedSection != null
                && isPinnedViewTouched(mPinnedSection.view, x, y)) { 
        	// create touch target
            // user touched pinned view
            //创建触摸目标，用户触摸固定视图，坐标值mTouchPoint设置
            mTouchTarget = mPinnedSection.view;
            mTouchPoint.x = x;
            mTouchPoint.y = y;

            // copy down event for eventually be used later
            /*复制已有的MotionEvent,生成一个*/
            mDownEvent = MotionEvent.obtain(ev);
        }

        if (mTouchTarget != null) {
            if (isPinnedViewTouched(mTouchTarget, x, y)) { // forward event to pinned view
                mTouchTarget.dispatchTouchEvent(ev);
            }

            if (action == MotionEvent.ACTION_UP) { // perform onClick on pinned view
                super.dispatchTouchEvent(ev);
                performPinnedItemClick();
                clearTouchTarget();

            } else if (action == MotionEvent.ACTION_CANCEL) { // cancel
                clearTouchTarget();

            } else if (action == MotionEvent.ACTION_MOVE) {
                if (Math.abs(y - mTouchPoint.y) > mTouchSlop) {

                    // cancel sequence on touch target
                    MotionEvent event = MotionEvent.obtain(ev);
                    event.setAction(MotionEvent.ACTION_CANCEL);
                    mTouchTarget.dispatchTouchEvent(event);
                    event.recycle();

                    // provide correct sequence to super class for further handling
                    super.dispatchTouchEvent(mDownEvent);
                    super.dispatchTouchEvent(ev);
                    clearTouchTarget();

                }
            }

            return true;
        }
           
    	if (!mScroller.isFinished()) {
			return super.onTouchEvent(ev);
		}
		if(headView==null){
			return super.onTouchEvent(ev);
		}
		
		bgView = headView.findViewById(R.id.path_headimage);
		proView=(HoloCircleSeekBar) headView.findViewById(R.id.picker);
		
		headView.getTop();
		switch (action) {
		case MotionEvent.ACTION_DOWN:
			left = bgView.getLeft();
			top = bgView.getBottom();
			rootW = getWidth();
			rootH = getHeight();
			bgViewH = bgView.getHeight();
			startX = x;
			startY = y;
			tool = new TouchTool(bgView.getLeft(), bgView.getBottom(),
					bgView.getLeft(), bgView.getBottom() + len);
			break;
		case MotionEvent.ACTION_MOVE:
			if (headView.isShown() && headView.getTop() >= 0) {
				if (tool != null) {
					int t = tool.getScrollY(y - startY);
					if (t >= top && t <= headView.getBottom() + len) {
						bgView.setLayoutParams(new RelativeLayout.LayoutParams(
								bgView.getWidth(), t));
					//	proView.getValue()
					}
				}
				scrollerType = false;
			}
			break;
		case MotionEvent.ACTION_UP:
			scrollerType = true;
//			if(bgView.getBottom()-bgViewH>100&&re!=null){
//				re.refresh();
//			}
			mScroller.startScroll(bgView.getLeft(), bgView.getBottom(),
					0 - bgView.getLeft(), bgViewH - bgView.getBottom(), 200);
			invalidate();
			break;
		}

        // call super if this was not our pinned view
        return super.dispatchTouchEvent(ev);
    }
    
    
	public void computeScroll() {
		if (mScroller.computeScrollOffset()) {
			int x = mScroller.getCurrX();
			int y = mScroller.getCurrY();
			bgView.layout(0, 0, x + bgView.getWidth(), y);
			invalidate();
			if (!mScroller.isFinished() && scrollerType && y > 200) {
				bgView.setLayoutParams(new RelativeLayout.LayoutParams(bgView
						.getWidth(), y));
			}
		}
	}

    /*判断固定部分是否被触摸*/
    private boolean isPinnedViewTouched(View view, float x, float y) {
        view.getHitRect(mTouchRect);

        // by taping top or bottom padding, the list performs on click on a border item.
        // we don't add top padding here to keep behavior consistent.
        /*
        *  通过点击顶部或者底部间距处，
        */
        mTouchRect.top += mTranslateY;

        mTouchRect.bottom += mTranslateY + getPaddingTop();
        mTouchRect.left += getPaddingLeft();
        mTouchRect.right -= getPaddingRight();
        return mTouchRect.contains((int)x, (int)y);
    }

    private void clearTouchTarget() {
        mTouchTarget = null;
        if (mDownEvent != null) {
            mDownEvent.recycle();
            mDownEvent = null;
        }
    }

    private boolean performPinnedItemClick() {
        if (mPinnedSection == null) return false;

        OnItemClickListener listener = getOnItemClickListener();
        if (listener != null) {
            View view =  mPinnedSection.view;
            playSoundEffect(SoundEffectConstants.CLICK);
            if (view != null) {
                view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
            }
            listener.onItemClick(this, view, mPinnedSection.position, mPinnedSection.id);
            return true;
        }
        return false;
    }

    /*这个是对顶部Item是否固定的判断，返回boolean类型数据*/
    public static boolean isItemViewTypePinned(ListAdapter adapter, int viewType) {
        /*这个是Java中的知识，instanceof是一个二元操作符，类似与==，>等，作用是测试它左边的对象
        * 是否是它右边的类的实例，返回boolean类型数据，而HeaderViewListAdapter是当ListView有一个头部View时调用;
        * 而getWrappedAdapter返回的是一个被listadapter包装的adapter
        */
        if (adapter instanceof HeaderViewListAdapter) {
            adapter = ((HeaderViewListAdapter)adapter).getWrappedAdapter();
        }
        /*实现了PinnedSectionListAdapter的接口*/
        return ((PinnedSectionListAdapter) adapter).isItemViewTypePinned(viewType);
    }

}
