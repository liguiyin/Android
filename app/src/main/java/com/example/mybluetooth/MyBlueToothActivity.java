package com.example.mybluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatImageButton;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.OrientationHelper;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.UUID;

public class MyBlueToothActivity extends AppCompatActivity {
    ImageButton back_btn ;
    ImageButton search_btn;
    TextView header_text;
    final String check = "搜索设备中......";
    final String tip   = "选择一个要连接的设备";
    private BluetoothAdapter mBluetoothAdapter;//蓝牙适配器
    public static final String BT_UUID = "00001101-0000-1000-8000-00805F9B34FB";//uuid 用于创建服务器
    private MyBlueToothStateReceiver myBlueToothStateReceiver;       //蓝牙广播接收器
    private RecyclerView mRecyclerView;
    public BlueToothRvAdapter mRvAdapter;
    private ConnectThread mConnectThread; //客户端线程
    private AcceptThread mAcceptThread; //服务端线程
    boolean found = false;  //记录本条记录是否在item中
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(R.style.AppTheme_MyTheme);   //
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);  //声明使用自定义标题
        setContentView(R.layout.activity_bluetooth);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE,R.layout.mycustomtitle); //自定义布局赋值
        back_btn=(ImageButton)findViewById(R.id.back_btn);
        search_btn=(ImageButton)findViewById(R.id.search_btn);
        header_text=(TextView)findViewById(R.id.header_text);
        if(mBluetoothAdapter == null){
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }
        mRecyclerView = (RecyclerView) findViewById(R.id.devices);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));//设置布局管理器
        mRvAdapter = new BlueToothRvAdapter(this);
        mRecyclerView.setAdapter(mRvAdapter);                                  //设置适配器
        mRecyclerView.addItemDecoration(new DividerItemDecoration(this, OrientationHelper.VERTICAL));//设置分割线
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());  //设置增添 删除动画
        mRvAdapter.setOnItemClickListener(new BlueToothRvAdapter.OnItemClickListener(){
            @Override
            public void onItemClick(BluetoothDevice device) {
                //开始连接蓝牙设备
                Toast.makeText(getApplicationContext(), "连接设备"+device.getName(), Toast.LENGTH_SHORT).show(); //浮框提示
                mConnectThread = new ConnectThread(device);
                mConnectThread.start();
            }
        });

        back_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        search_btn.setOnClickListener(new View.OnClickListener() {  //点击搜索蓝牙设备
            @Override
            public void onClick(View v) {
                header_text.setText(check);
                registerRec();
                //搜索设备
                if (mBluetoothAdapter != null) {
                    mRvAdapter.clearDevices();//开始搜索前清空上一次的列表
                    mBluetoothAdapter.startDiscovery();
                } else {
                    openBT();
                    if (mBluetoothAdapter != null) {
                        mRvAdapter.clearDevices();//开始搜索前清空上一次的列表
                        mBluetoothAdapter.startDiscovery();
                    }
                }
                AlertDialog.Builder myDialog = new AlertDialog.Builder(MyBlueToothActivity.this);
                myDialog.setTitle("蓝牙设备");
                myDialog.setIcon(R.drawable.bluetooth);
                myDialog.setMessage(check);
                myDialog.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        header_text.setText(tip);
                        //关闭搜索
                        if (mBluetoothAdapter != null && mBluetoothAdapter.isDiscovering()) {
                            mBluetoothAdapter.cancelDiscovery();
                            unregisterReceiver(myBlueToothStateReceiver);  //关闭广播接收器
                        }
                    }
                });
                myDialog.create();
                myDialog.setCancelable(false);  //防止触碰对话框外部范围时 对话框关闭
                myDialog.show();

            }
        });
    }
    private void openBT() {
        //1.//获取蓝牙适配器
        if(mBluetoothAdapter == null){
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }
        //2.设备不支持蓝牙，结束应用
        if (mBluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(),"该设备不支持蓝牙",Toast.LENGTH_LONG).show();
        }
        //3.判断蓝牙是否打开
        if (!mBluetoothAdapter.enable()) {
            //没打开请求打开
            Intent btEnable = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(btEnable, 0);
        }
    }
    //注册蓝牙广播
    private void registerRec() {
        myBlueToothStateReceiver = new MyBlueToothStateReceiver();       //蓝牙广播接收器
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);//搜索蓝牙
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);//状态改变
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);//搜索结束
        filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);    //请求配对
        registerReceiver(myBlueToothStateReceiver,filter);
        found = false;
    }

    class MyBlueToothStateReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String pin = "1234";  //此处为你要连接的蓝牙设备的初始密钥，一般为1234或000
            String HC_MAC="98:D3:32:31:75:A5";

            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            switch (action) {
                case BluetoothDevice.ACTION_FOUND: {

                    /*for(int i=0;i<mRvAdapter.getItemCount();i++){
                        if(device.equals(mRvAdapter.getDevice(i))){
                            found=true; //之前已经发现该设备
                            break;
                        }
                    }*/
                    if (mRvAdapter != null && device.getAddress().equals(HC_MAC)) {
                        if (found == false) {
                            Toast.makeText(context, "找到目标设备" + device.getName(), Toast.LENGTH_SHORT).show();
                            mRvAdapter.addDevice(device);  //防止重复打印设备
                            found = true;
                            try{
                                ClsUtils.removeBond(device.getClass(), device);
                            }catch (Exception e){
                                e.printStackTrace();
                            }
                        }
                        try {
                            //通过工具类ClsUtils,调用createBond方法
                            ClsUtils.createBond(device.getClass(), device);
                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
                    break;
                case BluetoothDevice.ACTION_PAIRING_REQUEST:{
                    if(device.getAddress().equals(HC_MAC))
                    {
                        try {
                            //1.确认配对
                            ClsUtils.setPairingConfirmation(device.getClass(), device, true);
                            //3.调用setPin方法进行配对...
                            //boolean ret = ClsUtils.setPin(device.getClass(), device, pin);
                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                    break;
                }
            }
        }
    }
    //服务端线程代码
    class AcceptThread extends Thread {
        private BluetoothServerSocket mServerSocket ;
        private BluetoothSocket mSocket;
        private InputStream btIs;   //输入流
        private OutputStream btOs;  //输出流
        private PrintWriter writer; //打印
        private boolean canAccept;
        private boolean canRecv;

        public AcceptThread(){
            BluetoothServerSocket temp=null;
            try {
                //获取套接字
                temp = mBluetoothAdapter .listenUsingInsecureRfcommWithServiceRecord("Test", UUID.fromString(BT_UUID) );
                mServerSocket=temp;
            }catch (IOException e){
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            // Keep listening until exception occurs or a socket is returned
            while (true) {
                try {
                    mSocket = mServerSocket.accept();
                    //获取输入输出流
                    btIs = mSocket.getInputStream();
                    btOs = mSocket.getOutputStream();
                    //通讯-接收消息
                    BufferedReader reader = new BufferedReader(new InputStreamReader(btIs, "UTF-8"));
                    String content = null;
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }finally {
                    try{
                        // If a connection was accepted
                        if (mSocket != null) {
                            // Do work to manage the connection (in a separate thread)
                            mServerSocket.close();
                            mSocket.close();
                            break;
                        }
                    }catch (IOException e){
                        e.printStackTrace();
                        break;
                    }
                }
            }
        }
    }
    //连接线程代码
    class ConnectThread extends Thread{
        private String TAG="Message";
        private BluetoothDevice mDevice;
        private BluetoothSocket mSocket;
        public ConnectThread(BluetoothDevice device){
            mDevice = device;
            Log.d(TAG,mDevice.getName());
            mBluetoothAdapter.cancelDiscovery();  //在连接设备之前停止扫描
            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try{
                BluetoothSocket temp = mDevice.createInsecureRfcommSocketToServiceRecord(UUID.fromString(BT_UUID));
                mSocket=temp;
            }catch (IOException e){
                e.printStackTrace();
                Log.d(TAG,"获取套接字失败");
            }

        }
        @Override
        public void run() {
            if(mSocket.isConnected()==true){
                Log.d(TAG,"设备已连接");
            }
            else if(mDevice!=null){
                try{
                    //发起连接请求
                    if(mSocket!=null){
                        mSocket.connect();
                        Log.d(TAG,"连接成功");
                    }
                }catch(IOException e){
                    // Unable to connect; close the socket and get out
                    e.printStackTrace();
                    Log.d(TAG,"连接失败");
                    try {
                        if (mSocket != null) {
                            mSocket.close();
                        }
                        //btIs.close();//两个输出流都依赖socket，关闭socket即可
                        //btOs.close();
                    } catch (IOException closeException) {
                        closeException.printStackTrace();
                    }
                }
            }

        }
    }
}


