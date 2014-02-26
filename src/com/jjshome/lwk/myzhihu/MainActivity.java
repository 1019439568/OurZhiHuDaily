package com.jjshome.lwk.myzhihu;

import java.util.Locale;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.jjshome.lwk.widget.PinnedSectionListView;
import com.jjshome.lwk.widget.PinnedSectionListView.PinnedSectionListAdapter;

public class MainActivity extends Activity {

    /*这里定义的Item类，*/
	static class Item {

		public static final int ITEM = 0;
		public static final int SECTION = 1;

		public final int type;
		public final String text;

       /*sectionPosition代表的的是每个标签栏的位置，如：A，B。。。(带颜色的Item)
       *而listPosition代表的是每个小标签的位置，这里的设定，可以对Item点击后内容获取取得方便
       */
		public int sectionPosition;
		public int listPosition;

		public Item(int type, String text) {
			this.type = type;
			this.text = text;
		}

		@Override
		public String toString() {
			return text;
		}

	}

    /*
    *生成一个PinnedSectionListView对象
    */
	private PinnedSectionListView MainLv;

	private View head;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
       getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_main);
/*
       利用LayoutInflater动态载入XML布局文件，并且实例化，并且使用inflate返回一个View
       对象，且将ViewGroup设置为null，将此View作为根
*/

		head = LayoutInflater.from(this).inflate(R.layout.item_main_viewheader,
				null);

		MainLv = (PinnedSectionListView) findViewById(R.id.main_list);

		MainLv.setTopView((ImageView)findViewById(R.id.iv_top));
		
		MainLv.setHeadView(head);
		MainLv.addHeaderView(head);

       //调用了PinnedSectionListView中的setAdapter方法，按照构造函数传值没什么说的
		MainLv.setAdapter(new SimpleAdapter(this,
				android.R.layout.simple_list_item_1, android.R.id.text1));
	}

    //Menu的实现
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

    /*
    *  定义了SimpleAdapter类，继承了ArrayAdapter，实现了接口PinniedSectionListAdapter
    */
	 static class SimpleAdapter extends ArrayAdapter<Item> implements
			PinnedSectionListAdapter {

       /*定义了一个颜色的数组,在colors.xml中设置颜色*/
		private static final int[] COLORS = new int[] { R.color.green_light,
				R.color.orange_light, R.color.blue_light, R.color.red_light };

       //SimpleAdapter构造函数
		public SimpleAdapter(Context context, int resource,
				int textViewResourceId) {
			super(context, resource, textViewResourceId);

           //计算出sectionNumber为26,即为26个英文字母个数，下面的prepareSections为测试函数
			final int sectionsNumber = 'Z' - 'A' + 1;
			prepareSections(sectionsNumber);

			int sectionPosition = 0, listPosition = 0;
			for (char i = 0; i < sectionsNumber; i++) {
              /*首先要注意传入的第一个值type为1,后面的参数利用Ascii码累加，产生A,B,C.....*/
				Item section = new Item(Item.SECTION,
						String.valueOf((char) ('A' + i)));
				section.sectionPosition = sectionPosition;
				section.listPosition = listPosition++;
              /*这里的onSectionAdded同样为测试函数*/
				onSectionAdded(section, sectionPosition);
              //add函数没什么说的，就是将指定对象添加到Array末尾
				add(section);

              /*这个计算看起来很高端，其实将值打印出来，就会发现，itemNumber获得就是每块item的数量
              *如：A就有12条。。。。
              */
				final int itemsNumber = (int) Math.abs((Math.cos(2f * Math.PI
						/ 3f * sectionsNumber / (i + 1f)) * 25f));
              //Log.d("itemsNumber",itemsNumber+"");
              /*这个就是根据计算的itemsNumber的对listview加入相应多少的item*/
				for (int j = 0; j < itemsNumber; j++) {
                  //这里传入type值为0,请注意
					Item item = new Item(Item.ITEM,
							section.text.toUpperCase(Locale.ENGLISH) + " - "
									+ j);
					item.sectionPosition = sectionPosition;
					item.listPosition = listPosition++;
                  Log.d("listPosition",listPosition+"");
					add(item);
				}

				sectionPosition++;
			}
		}

		protected void prepareSections(int sectionsNumber) {
               Log.e("prepareSections", sectionsNumber+"");
		}

		protected void onSectionAdded(Item section, int sectionPosition) {
			Log.e("onSectionAdded", sectionPosition+"");
		}

       /*
        *这里的getView函数可以对每条Item的内容进行获取
        */
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			TextView view = (TextView) super.getView(position, convertView,
					parent);
			view.setTextColor(Color.DKGRAY);
			view.setTag("" + position);
			Item item = getItem(position);
           //刚刚设置的Type值在这里起到了效果，对type值为1(即：Item.SECTION)的item设置背景色
			if (item.type == Item.SECTION) {
				// view.setOnClickListener(PinnedSectionListActivity.this);
				view.setBackgroundColor(parent.getResources().getColor(
						COLORS[item.sectionPosition % COLORS.length]));
			}
			return view;
		}

		@Override
		public int getViewTypeCount() {
			return 2;
		}

		@Override
		public int getItemViewType(int position) {
			return getItem(position).type;
		}

		@Override
		public boolean isItemViewTypePinned(int viewType) {
			return viewType == Item.SECTION;
		}

	}

}
