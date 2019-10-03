package com.example.mybluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class BlueToothRvAdapter extends RecyclerView.Adapter<BlueToothRvAdapter.BlueToothRvHolder>{
    private Context mContext;
    private List<BluetoothDevice> mDevices;
    private OnItemClickListener mOnItemClickListener;//数组适配器(配对蓝牙设备集)

    //注册监听事件
    public void setOnItemClickListener(OnItemClickListener mOnItemClickListener) {
        this.mOnItemClickListener = mOnItemClickListener;
    }
    //初始化上下文和数据
    public BlueToothRvAdapter(Context mContext) {
        this.mContext = mContext;
        mDevices = new ArrayList<>();
    }
    //创建适配器
    @NonNull
    @Override
    public BlueToothRvHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        return new BlueToothRvHolder(LayoutInflater.from(mContext).inflate(R.layout.activity_item,viewGroup,false));
    }
    //适配渲染数据到View
    @Override
    public void onBindViewHolder(final BlueToothRvHolder blueToothRvHolder, final int i) {
        blueToothRvHolder.nameTv.setText(mDevices.get(i).getName()+":"+mDevices.get(i).getAddress());
        if(mOnItemClickListener!=null){  //点击事件 点击配对
            //监听列表本身点击事件
            blueToothRvHolder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int position = blueToothRvHolder.getLayoutPosition(); // 1
                    mOnItemClickListener.onItemClick(mDevices.get(position));
                }
            });
        }
    }
    //获取条目地址
    public  BluetoothDevice getDevice(int position){
        return mDevices.get(position);
    }
    //获取列表条目数量
    @Override
    public int getItemCount() {
        return mDevices.size();
    }
    //增添
    public void addDevice(BluetoothDevice device) {
        mDevices.add(device);
        notifyItemInserted(mDevices.size()-1);
    }
    //清除
    public void clearDevices(){
        mDevices.clear();
        notifyDataSetChanged();
    }
    //移除
    public void remove(BluetoothDevice device){
        mDevices.remove(device);
        notifyDataSetChanged();
    }
    //定义回调接口
    public interface OnItemClickListener{
        void onItemClick(BluetoothDevice device);
    }
    class BlueToothRvHolder extends RecyclerView.ViewHolder{
        private TextView nameTv;
        public BlueToothRvHolder(View itemView) {
            super(itemView);
            nameTv = itemView.findViewById(R.id.name);
        }
    }
}
