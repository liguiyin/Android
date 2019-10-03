package com.example.mybluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    //计时变量
    private int seconds = 0;
    //按钮含义
    private boolean running;     //计时标志位
    Button start,stop;
    Button reset_button;
    private BluetoothAdapter mBluetoothAdapter;//蓝牙适配器
    private static final int REQUEST_BT_ENABLE_CODE = 1;//requestCode
    public static final String BT_UUID = "00001101-0000-1000-8000-00805F9B34FB";//uuid 用于创建服务器
    private MyBlueToothStateReceiver myBlueToothStateReceiver;       //蓝牙广播接收器
    private  MyBlueToothConnectStateReceiver myBlueToothConnectStateReceiver;
    private BluetoothDevice mdevice;
    private ConnectThread mConnectThread; //客户端线程
    private boolean bluetoothIsConnected;
    boolean found = false;  //记录本条记录是否在item中

    private ConnectedThread rThread=null;  //数据接收线程
    private BluetoothSocket mSocket;  //套接字
    private  TextView timeView;
    private  final String TAG = "Message";
    private  OutputStream outStream;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        reset_button=(Button)findViewById(R.id.reset_button);
        timeView = (TextView)findViewById(R.id.time_view);
        start=(Button)findViewById(R.id.start);
        stop=(Button)findViewById(R.id.stop);
        this.setTitle("蓝牙小车");
        runTimer();
        //重置按钮监听
        reset_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClickReset();  //重新计时
            }
        });
        //开始行走
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                seconds=0;
                new SendInfoTask().execute("B");
            }
        });
        //停止行走
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new SendInfoTask().execute("S");
            }
        });
        //蓝牙连接状态监听
        /*myBlueToothConnectStateReceiver = new MyBlueToothConnectStateReceiver();       //蓝牙连接状态广播接收器
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);//搜索蓝牙
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);//状态改变
        registerReceiver(myBlueToothConnectStateReceiver,filter);*/
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode==KeyEvent.KEYCODE_BACK){
            AlertDialog.Builder builder=new AlertDialog.Builder(this);
            builder.setTitle("蓝精灵：");
            builder.setMessage("您确定退要离开我？");
            //设置取消按钮
            builder.setPositiveButton("取消",null);
            //设置确定按钮
            builder.setNegativeButton("确定", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if(mdevice.getBondState()==BluetoothDevice.BOND_BONDED){
                        try{
                            ClsUtils.removeBond(mdevice.getClass(), mdevice);
                            running=false;
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                    try {  //断开蓝牙连接
                        if(rThread!=null)
                        {
                            mSocket.close();
                            mSocket=null;
                            rThread.stop();
                        }
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    finish();
                }
            });
            //显示提示框
            builder.show();
        }
        return super.onKeyDown(keyCode, event);
    }

    //创建菜单
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = this.getMenuInflater();
        inflater.inflate(R.menu.menu,menu);
        return true;
    }

   /* @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if(bluetoothIsConnected){
            MainActivity.this.setTitle(mdevice.getName());
            menu.getItem(0).setTitle("断开");
        }
        else{
            MainActivity.this.setTitle("未连接");
            menu.getItem(0).setTitle("连接");
        }
        return true;
    }*/

    //菜单监听
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case R.id.open_bluetooth :{  //打开蓝牙
                openBT();
                break;
            }

            case R.id.close_bluetooth :{  //关闭蓝牙
                if(mBluetoothAdapter!=null){
                    mBluetoothAdapter.disable();
                }

                break;
            }
            case R.id.connect: { //连接蓝牙设备
                /*Intent intent = new Intent(MainActivity.this,MyBlueToothActivity.class);
                startActivity(intent);*/
                if(bluetoothIsConnected){
                    Toast.makeText(getApplicationContext(),"设备已连接",Toast.LENGTH_SHORT).show();
                }
                if (mdevice != null && mdevice.getBondState() == BluetoothDevice.BOND_BONDED) {
                    new ConnectThread().execute(mdevice.getAddress());
                    if(bluetoothIsConnected){
                        Toast.makeText(getApplicationContext(),"连接成功",Toast.LENGTH_SHORT).show();
                    }
                }
                else{
                    Toast.makeText(getApplicationContext(),"请与目标设备进行配对",Toast.LENGTH_SHORT).show();
                }
                break;
            }
            case R.id.search_bluetooth:{//搜索目标蓝牙设备 并与其配对
                registerRec();
                if (mBluetoothAdapter != null) {
                    mBluetoothAdapter.startDiscovery();
                } else {
                    openBT();
                    if (mBluetoothAdapter != null) {
                        mBluetoothAdapter.startDiscovery();
                    }
                }
                AlertDialog.Builder myDialog = new AlertDialog.Builder(MainActivity.this);
                myDialog.setTitle("蓝牙设备");
                myDialog.setMessage("搜索设备中......");
                myDialog.setIcon(R.drawable.bluetooth);
                myDialog.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
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
            case R.id.settings :{ //相关设置

                break;
            }
        }
        return super.onOptionsItemSelected(item);
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
            startActivityForResult(btEnable, REQUEST_BT_ENABLE_CODE);
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
            //String HC_MAC="98:D3:32:31:75:A5";
            String HC_MAC="00:18:E4:00:76:84";

            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            switch (action) {
                case BluetoothDevice.ACTION_FOUND: {

                    /*for(int i=0;i<mRvAdapter.getItemCount();i++){
                        if(device.equals(mRvAdapter.getDevice(i))){
                            found=true; //之前已经发现该设备
                            break;
                        }
                    }*/
                    if (device.getAddress().equals(HC_MAC)&&device.getName()!=null) {
                        if (found == false) {
                            Toast.makeText(context, "找到目标设备" + device.getName(), Toast.LENGTH_SHORT).show();
                            mdevice = device;
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
    class MyBlueToothConnectStateReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
                bluetoothIsConnected=true;
                MainActivity.this.setTitle(mdevice.getName());
            }
            else if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)){
                bluetoothIsConnected=false;
                MainActivity.this.setTitle("未连接");
            }
        }
    }

    @Override
    protected void onDestroy() {//在页面销毁时 要对广播接收器进行解注册
        if (myBlueToothStateReceiver != null) {
            unregisterReceiver(myBlueToothStateReceiver);
        }
        try {  //断开蓝牙连接
            if(rThread!=null)
            {
                mSocket.close();
                mSocket=null;
                rThread.stop();
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        super.onDestroy();
    }

    class ConnectThread extends AsyncTask<String,String,String> {
        private BluetoothDevice mDevice;
        @Override
        protected String doInBackground(String... strings) {
            mDevice=mBluetoothAdapter.getRemoteDevice(strings[0]);
            if(mDevice!=null){
                try{
                    mSocket = mDevice.createInsecureRfcommSocketToServiceRecord(UUID.fromString(BT_UUID));
                    //发起连接请求
                    if(mSocket!=null){
                        mSocket.connect();
                    }
                }catch(IOException e){
                    // Unable to connect; close the socket and get out
                    e.printStackTrace();
                    try {
                        if (mSocket != null) {
                            mSocket.close();
                            bluetoothIsConnected=false;
                            return "Socket 创建失败";
                        }
                    } catch (IOException closeException) {
                        closeException.printStackTrace();
                        bluetoothIsConnected=false;
                        return "Socket 关闭失败";
                    }
                }
            }
            try {
                outStream = mSocket.getOutputStream();
            } catch (IOException e) {
                Log.e("error", "ON RESUME: Output stream creation failed.", e);
                return "Socket 流创建失败";
            }
            bluetoothIsConnected=true;
            return "连接成功";
        }
        @Override
        protected void onPostExecute(String s) {
            //连接成功则启动监听
            rThread=new ConnectedThread(mSocket);
            rThread.start();
            super.onPostExecute(s);
        }
    }
    //通信数据接收线程
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            // Get the input and output streams, using temp objects because
            // member streams are final
            try {//获取输入输出流
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }
            mmInStream = tmpIn;

        }

        public void run() {
            while(mSocket!=null){
                Log.d(TAG,"开始接收");
                byte[] buffer = new byte[1024];  // buffer store for the stream
                int bytes; // bytes returned from read()
                // Keep listening to the InputStream until an exception occurs
                    try {
                        // Read from the InputStream
                        bytes = mmInStream.read(buffer);
                        processBuffer(buffer,bytes);
                    } catch (IOException e) {
                        Log.d(TAG,"接收失败");
                        break;
                    }
            }
        }
        /* Call this from the main Activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
        //处理接收到信息 存储到ReceiverData中
        private void processBuffer(byte[] buff,int size)
        {
            String ReceiveData="";   //接收字符串
            int length=0;
            for(int i=0;i<size;i++)
            {
                if(buff[i]>'\0')
                {
                    length++;
                }
                else
                {
                    break;
                }
            }
            byte[] newbuff=new byte[length];  //newbuff字节数组，用于存放真正接收到的数据

            for(int j=0;j<length;j++)
            {
                newbuff[j]=buff[j];
            }
            ReceiveData=new String(newbuff);
            Log.d(TAG,"接收到信息:"+ReceiveData);
            if(ReceiveData.equals("B")){
                running=true;
            }
            else if(ReceiveData.equals("S")){
                running=false;
            }
            Message msg=Message.obtain();
            msg.what=1;
        }
    }
    //发送数据的异步线程
    //发送数据到蓝牙设备的异步任务
    private class SendInfoTask extends AsyncTask<String,String,String>
    {

        @Override
        protected void onPostExecute(String result) {
            // TODO Auto-generated method stub
            super.onPostExecute(result);
        }

        @Override
        protected String doInBackground(String... arg0) {
            // TODO Auto-generated method stub

            if(mSocket==null)
            {
                return "还没有创建连接";
            }
            if(arg0[0].length()>0)//不是空白串
            {
                //String target=arg0[0];

                byte[] msgBuffer = arg0[0].getBytes();//将字符串转换成字符数组

                try {
                    //  将msgBuffer中的数据写到outStream对象中
                    outStream.write(msgBuffer);
                } catch (IOException e) {
                    Log.e("error", "ON RESUME: Exception during write.", e);
                    return "发送失败";
                }
            }
            Log.d(TAG,"发送信息");
            return "发送成功";
        }

    }
    // 计时重置
    public void ClickReset() {
        running = false;
        seconds = 0;
    }
    //计时停止
    public void ClickStop(){
        running = false;
    }
    private void runTimer() {

        final Handler handler = new Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {
                int minutes = (seconds%3600)/60;
                int secs = seconds%60;
                String time = String.format("%02d:%02d",
                        minutes, secs);
                timeView.setText(time);
                if (running) {
                    seconds++;
                }
                handler.postDelayed(this, 1000);

            }
        });
    }
}
