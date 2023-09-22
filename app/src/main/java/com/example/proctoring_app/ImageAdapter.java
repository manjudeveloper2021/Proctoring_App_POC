package com.example.proctoring_app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

public class ImageAdapter extends BaseAdapter {
    private Context ctx;
    private final String[] filesNames;
    private final String[] filesPaths;

    public ImageAdapter(Context ctx, String[] filesNames, String[] filesPaths) {
        this.ctx = ctx;
        this.filesNames = filesNames;
        this.filesPaths = filesPaths;
    }

    @Override
    public int getCount() {
        return filesNames.length;
    }

    @Override
    public Object getItem(int pos) {
        return pos;
    }


    @Override
    public long getItemId(int pos) {
        return pos;
    }

    @Override
    public View getView(int p, View convertView, ViewGroup parent) {
        View grid;
        LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        if (convertView == null) {
            grid = inflater.inflate(R.layout.activity_galleryview, null);
            ImageView imageView = (ImageView)grid.findViewById(R.id.imggalleryview);

            Bitmap bmp = BitmapFactory.decodeFile(filesPaths[p]);
                  if (bmp == null){
                      imageView.setVisibility(View.GONE);
                  }
                  else{
                      imageView.setVisibility(View.VISIBLE);
                      imageView.setImageBitmap(bmp);
                  }



        } else {
            grid = (View) convertView;
        }
        return grid;
    }
}
