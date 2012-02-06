package us.squishy;

import java.io.IOException;

import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Vector;

import android.app.Activity;
import android.content.Context;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ListAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class RemoteControlActivity extends Activity implements OnItemSelectedListener {
	
	private static final short PORT = 27027;
	private static final short LISTEN_PORT = 27127;
	private static final String QUIT_MESSAGE = "YOU QUIT NOW!!";
	private static final String DISCOVERY_MESSAGE = "DISCOVERY:";
	private static final String DISCOVER_REPORT = "DISCOVER:";

	private static final String ControlMessages[] = {
		"VOLUMEUP",
		"VOLUMEDOWN",
		"MUTE",
		"POWER",
		DISCOVERY_MESSAGE,
		"EXIT"
	};
	private Spinner mTargetSpinner = null;
	private ArrayAdapter<String>    mAdapter    = null;
	private Vector<String>  mTargets    = new Vector<String>();
	private InetAddress 	mTargetIP   = null;
	private InetAddress     mLocalIP    = null;
	private InetAddress     mBroadcastIP= null;
	
	AsyncTask<Short, String, Void> mListenerTask =	new AsyncTask<Short, String, Void> () {
		
		@Override
	    protected Void doInBackground(Short... port) {
			
        	byte[] receiveData = new byte[1024];
        	DatagramSocket serverSocket = null;
			try {
				if (mLocalIP != null) {
					serverSocket = new DatagramSocket(port[0], mLocalIP);
				} else {
					serverSocket = new DatagramSocket(port[0]);
				}
			    while (true)  {
					DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
					serverSocket.receive(receivePacket);
			    	
			    	String returnSentence = new String(receivePacket.getData());
			    	publishProgress(returnSentence);

			    	if (returnSentence.equalsIgnoreCase(QUIT_MESSAGE)) {
			    		return null;
			    	}
				}
			} catch (SocketException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (serverSocket != null) {
					serverSocket.close();
				}
			}
			return null;
	    }

	     @Override
	     protected void onProgressUpdate(String... message) {
	    	 
	    	 if (message[0].startsWith(DISCOVER_REPORT)) {
	    		 try{
		    		 // handle discovery, don't care about anything else.
		    		 String serverAddressNamePair = message[0].substring(DISCOVER_REPORT.length());
		    		 String addressOnly = 
	    				 serverAddressNamePair.substring(0, serverAddressNamePair.indexOf(":"));
		    		 
    	    		 mTargets.add(addressOnly);
    	    		 mAdapter.add(serverAddressNamePair);
	  	 	         mTargetIP = InetAddress.getByName(addressOnly);
	  	 	         
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}
	    	 }	
	     }
	};
	
	OnClickListener mHandler = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			try {
				switch (v.getId()) {
				case R.id.buttonPower:
					RemoteControlActivity.this.send(ControlMessages[3]); // Power
					break;
				case R.id.buttonVolumeUp:
					RemoteControlActivity.this.send(ControlMessages[0]); // Volume Up
					break;
				case R.id.buttonVolumeDown:
					RemoteControlActivity.this.send(ControlMessages[1]); // Volume Down
					break;
				case R.id.buttonMute:
					RemoteControlActivity.this.send(ControlMessages[2]); // Mute
					break;
				case R.id.buttonDiscover:
					RemoteControlActivity.this.broadcast(DISCOVERY_MESSAGE);
					break;
				case R.id.buttonAddIP:
					RemoteControlActivity.this.addTargetIP();
					break;
				}
			} catch(Exception e){
				e.printStackTrace();
			}
		}
	};
	
	 /**
	  *  Called when the activity is first created. 
	  */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
		((Button)findViewById(R.id.buttonPower)).setOnClickListener(mHandler);
		((Button)findViewById(R.id.buttonVolumeUp)).setOnClickListener(mHandler);
		((Button)findViewById(R.id.buttonVolumeDown)).setOnClickListener(mHandler);
		((Button)findViewById(R.id.buttonMute)).setOnClickListener(mHandler);
		((Button)findViewById(R.id.buttonDiscover)).setOnClickListener(mHandler);
		((Button)findViewById(R.id.buttonAddIP)).setOnClickListener(mHandler);
		
		mTargetSpinner = (Spinner)findViewById(R.id.spinnerTargetAddresses);
		mAdapter =	new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item);
		mTargetSpinner.setOnItemSelectedListener(this);
		mTargetSpinner.setAdapter(mAdapter);
		
		getAddresses();
		
		String localInfo =  "Broadcast: " + mBroadcastIP.getHostAddress();
		localInfo        += "\nLocal: "   + mLocalIP.getHostAddress();
		System.out.println(localInfo);
		
		mTargets.add("127.0.0.1");
		mAdapter.add("127.0.0.1:LocalLoopback");
		mListenerTask.execute(LISTEN_PORT);
    }

    void addTargetIP()  {
    	TextView editTargetIP = (TextView)findViewById(R.id.editIPAddress);
    	String targetIP = new String(editTargetIP.getText().toString());
    	try {
			InetAddress addressIP = InetAddress.getByName(targetIP);
	    	mTargets.add(targetIP);
	    	mAdapter.add(targetIP);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
    }
    
	InetAddress getBroadcastAddressDHCP() throws IOException {
		
	    WifiManager wifi = (WifiManager)getSystemService(Context.WIFI_SERVICE);
	    DhcpInfo dhcp = wifi.getDhcpInfo();
	    
	    if (dhcp == null) {
	    	return null;
	    }

	    int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
	    byte[] quads = new byte[4];
	    for (int k = 0; k < 4; k++)
	      quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
	    
	    return InetAddress.getByAddress(quads);
	}
	
	void getAddresses() {
		
		Enumeration<NetworkInterface> interfaces;
		try {
			interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				
				NetworkInterface networkInterface = interfaces.nextElement();
				if (networkInterface.isLoopback())
					continue;    // Don't want to broadcast to the loopback interface

				for (InterfaceAddress interfaceAddress :
					 networkInterface.getInterfaceAddresses()) {
					
					mBroadcastIP = interfaceAddress.getBroadcast();
					if (mBroadcastIP == null)
						continue;
					
					mLocalIP = interfaceAddress.getAddress();
					break;
				}
			}
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}
    
    private void broadcast(String message_) {
    	
    	new AsyncTask<String, Void, Void>() {
    		
    		@Override
	    	protected Void doInBackground(String... message_to_broadcast) {
	    		
	    		try {
	    			if (message_to_broadcast.length == 0) {
	    				return null;
	    			}
			       	DatagramSocket clientSocket = new DatagramSocket();
		       		byte[] sendData = message_to_broadcast[0].getBytes();

			    	DatagramPacket sendPacket = 
			    			new DatagramPacket(sendData, sendData.length, mBroadcastIP, PORT);
		
			    	clientSocket.setBroadcast(true);
			    	clientSocket.send(sendPacket);
			    	clientSocket.close();
			    	
	    		} catch (SocketException se) {
					se.printStackTrace();
				} catch (UnknownHostException e) {
					e.printStackTrace();
	    		} catch (IOException io) {
	    			io.printStackTrace();
				}
	    		return null;
	    	}
    	}.execute(message_);

    }
    /**
     * @param message_
     * @throws IOException
     */
    private void send(String message_) throws IOException {
    	
    	if (mTargetIP == null) {
    		Context context = getApplicationContext();
    		CharSequence text = "Select a device to control.";
    		int duration = Toast.LENGTH_SHORT;

    		Toast toast = Toast.makeText(context, text, duration);
    		toast.show();
    		return;
    	}
    	
    	new AsyncTask<String, Void, Void>() {
    		
    		@Override
	    	protected Void doInBackground(String... message_to_send) {
	    		
	    		try {
			       	DatagramSocket clientSocket = new DatagramSocket();
			    	byte[] sendData = message_to_send[0].getBytes();
			    	
			    	DatagramPacket sendPacket = 
			    			new DatagramPacket(sendData, sendData.length, mTargetIP, PORT);
			    	
			     	clientSocket.send(sendPacket);
			    	clientSocket.close();
			    	
	    		} catch (SocketException se) {
					se.printStackTrace();
				} catch (UnknownHostException e) {
					e.printStackTrace();
	    		} catch (IOException io) {
	    			io.printStackTrace();
				}
	    		return null;
	    	}
	    	
    	}.execute(message_);
    }
		
	@Override
	public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		try {
			mTargetIP = InetAddress.getByName(mTargets.elementAt(arg2));
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onNothingSelected(AdapterView<?> arg0) {
		//mTargetIP = null;
	}


}

